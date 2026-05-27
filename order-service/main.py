import os
from contextlib import asynccontextmanager

import psycopg2
import psycopg2.extras
from fastapi import FastAPI, HTTPException, Header
from pydantic import BaseModel
from typing import List, Optional


def get_db():
    """RDS 연결. 환경변수에서 접속 정보를 가져온다."""
    dsn_url = os.getenv("SPRING_DATASOURCE_URL", "")
    # jdbc:postgresql://host:port/db -> host:port/db
    host_part = dsn_url.replace("jdbc:postgresql://", "")
    host_port, db_name = host_part.rsplit("/", 1) if "/" in host_part else (host_part, "baseball_platform")
    host, port = host_port.split(":") if ":" in host_port else (host_port, "5432")

    return psycopg2.connect(
        host=host,
        port=port,
        dbname=db_name,
        user=os.getenv("SPRING_DATASOURCE_USERNAME", "baseball"),
        password=os.getenv("SPRING_DATASOURCE_PASSWORD", "baseball"),
    )


def ensure_orders_table():
    """orders 테이블이 없으면 생성한다."""
    conn = get_db()
    try:
        with conn.cursor() as cur:
            cur.execute("""
                CREATE SCHEMA IF NOT EXISTS order_schema;
                CREATE TABLE IF NOT EXISTS order_schema.orders (
                    order_id SERIAL PRIMARY KEY,
                    user_id BIGINT,
                    game_id BIGINT NOT NULL,
                    seat_id BIGINT NOT NULL,
                    status VARCHAR(30) NOT NULL DEFAULT 'ORDERED',
                    total_price INTEGER NOT NULL DEFAULT 0,
                    created_at TIMESTAMP DEFAULT now()
                );
                CREATE TABLE IF NOT EXISTS order_schema.order_items (
                    order_item_id SERIAL PRIMARY KEY,
                    order_id INTEGER REFERENCES order_schema.orders(order_id) ON DELETE CASCADE,
                    menu_id BIGINT NOT NULL,
                    menu_name VARCHAR(100),
                    quantity INTEGER NOT NULL,
                    price INTEGER NOT NULL
                );
            """)
        conn.commit()
    finally:
        conn.close()


@asynccontextmanager
async def lifespan(application: FastAPI):
    ensure_orders_table()
    yield


app = FastAPI(title="Order Service", description="주류 주문 및 상태 관리 API", lifespan=lifespan)


# --- Pydantic 모델 ---
class OrderItem(BaseModel):
    menuId: int
    quantity: int


class OrderRequest(BaseModel):
    gameId: int
    seatId: int
    items: List[OrderItem]


class OrderStatusUpdate(BaseModel):
    status: str


# --- API ---

@app.get("/")
def root():
    return {"message": "주문 서비스가 정상 작동 중입니다!"}


@app.get("/api/orders/menus")
def get_menus():
    """실제 DB에서 메뉴 조회"""
    conn = get_db()
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute("""
                SELECT menu_id AS "menuId", name, price, available
                FROM order_schema.alcohol_menus
                ORDER BY menu_id
            """)
            menus = cur.fetchall()
        return {"success": True, "data": menus}
    finally:
        conn.close()


@app.post("/api/orders")
def create_order(request: OrderRequest, x_user_id: Optional[str] = Header(None, alias="X-User-Id")):
    """실제 DB에서 메뉴 가격을 조회하고 주문을 생성"""
    user_id = int(x_user_id) if x_user_id else None
    conn = get_db()
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            # 메뉴 가격 조회
            menu_ids = [item.menuId for item in request.items]
            cur.execute(
                "SELECT menu_id, name, price FROM order_schema.alcohol_menus WHERE menu_id = ANY(%s)",
                (menu_ids,)
            )
            menu_map = {row["menu_id"]: row for row in cur.fetchall()}

            total_price = 0
            order_items_detail = []
            for item in request.items:
                menu = menu_map.get(item.menuId)
                if not menu:
                    raise HTTPException(status_code=400, detail=f"메뉴 ID {item.menuId}를 찾을 수 없습니다.")
                total_price += menu["price"] * item.quantity
                order_items_detail.append({
                    "menuId": menu["menu_id"],
                    "name": menu["name"],
                    "quantity": item.quantity,
                    "price": menu["price"],
                })

            # 주문 저장
            cur.execute("""
                INSERT INTO order_schema.orders (user_id, game_id, seat_id, status, total_price)
                VALUES (%s, %s, %s, 'ORDERED', %s)
                RETURNING order_id, status, total_price
            """, (user_id, request.gameId, request.seatId, total_price))
            order_row = cur.fetchone()

            # 주문 아이템 저장
            for detail in order_items_detail:
                cur.execute("""
                    INSERT INTO order_schema.order_items (order_id, menu_id, menu_name, quantity, price)
                    VALUES (%s, %s, %s, %s, %s)
                """, (order_row["order_id"], detail["menuId"], detail["name"], detail["quantity"], detail["price"]))

        conn.commit()
        return {
            "success": True,
            "data": {
                "orderId": order_row["order_id"],
                "status": order_row["status"],
                "totalPrice": order_row["total_price"],
            },
            "message": "주문이 생성되었습니다.",
        }
    finally:
        conn.close()


@app.get("/api/orders/my")
def get_my_orders(x_user_id: Optional[str] = Header(None, alias="X-User-Id")):
    """내 주문 목록 조회"""
    user_id = int(x_user_id) if x_user_id else None
    conn = get_db()
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            if user_id:
                cur.execute("""
                    SELECT order_id AS "orderId", game_id AS "gameId", status,
                           total_price AS "totalPrice", created_at AS "createdAt"
                    FROM order_schema.orders WHERE user_id = %s ORDER BY created_at DESC
                """, (user_id,))
            else:
                cur.execute("""
                    SELECT order_id AS "orderId", game_id AS "gameId", status,
                           total_price AS "totalPrice", created_at AS "createdAt"
                    FROM order_schema.orders ORDER BY created_at DESC LIMIT 50
                """)
            orders = cur.fetchall()
        # datetime -> string
        for o in orders:
            if o.get("createdAt"):
                o["createdAt"] = o["createdAt"].isoformat()
        return {"success": True, "data": orders}
    finally:
        conn.close()


@app.get("/api/orders/{order_id}")
def get_order_detail(order_id: int):
    """주문 상세 조회"""
    conn = get_db()
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute("""
                SELECT order_id AS "orderId", user_id AS "userId", game_id AS "gameId",
                       seat_id AS "seatId", status, total_price AS "totalPrice",
                       created_at AS "createdAt"
                FROM order_schema.orders WHERE order_id = %s
            """, (order_id,))
            order = cur.fetchone()
            if not order:
                raise HTTPException(status_code=404, detail="주문을 찾을 수 없습니다.")

            cur.execute("""
                SELECT menu_id AS "menuId", menu_name AS "name", quantity, price
                FROM order_schema.order_items WHERE order_id = %s
            """, (order_id,))
            items = cur.fetchall()

        order["items"] = items
        if order.get("createdAt"):
            order["createdAt"] = order["createdAt"].isoformat()
        return {"success": True, "data": order}
    finally:
        conn.close()


@app.patch("/api/orders/{order_id}/status")
def update_order_status(order_id: int, request: OrderStatusUpdate):
    """주문 상태 변경"""
    conn = get_db()
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute("""
                UPDATE order_schema.orders SET status = %s WHERE order_id = %s
                RETURNING order_id AS "orderId", status
            """, (request.status, order_id))
            row = cur.fetchone()
            if not row:
                raise HTTPException(status_code=404, detail="주문을 찾을 수 없습니다.")
        conn.commit()
        return {"success": True, "data": row, "message": "주문 상태가 변경되었습니다."}
    finally:
        conn.close()
