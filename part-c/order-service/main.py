from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import List
from datetime import datetime, timedelta

app = FastAPI(title="Order Service", description="주류 주문 및 상태 관리 API (Mock)")

# --- [가짜(Mock) 데이터베이스 세팅] ---
# 백엔드 담당자가 나중에 이 부분을 RDS(PostgreSQL) 조회 코드로 바꿀 예정입니다.
MOCK_MENUS = [
    {"menuId": 1, "name": "생맥주", "price": 6000, "available": True},
    {"menuId": 2, "name": "캔맥주", "price": 5000, "available": True},
    {"menuId": 3, "name": "순살치킨", "price": 18000, "available": True}
]

MOCK_ORDERS = []
order_id_counter = 1

# --- [Pydantic 모델 정의] ---
class OrderItem(BaseModel):
    menuId: int
    quantity: int

class OrderRequest(BaseModel):
    gameId: int
    seatId: int
    items: List[OrderItem]

class OrderStatusUpdate(BaseModel):
    status: str


# --- [API 엔드포인트 구현] ---

@app.get("/")
def root():
    return {"message": "주문 서비스가 정상 작동 중입니다! /docs 로 이동해서 API를 확인하세요."}

# 1. 주류 메뉴 조회
@app.get("/api/orders/menus")
def get_menus():
    return {
        "success": True,
        "data": MOCK_MENUS
    }

# 2. 주문 생성
@app.post("/api/orders")
def create_order(request: OrderRequest):
    global order_id_counter
    total_price = 0
    order_items_detail = []

    # 프론트엔드가 보낸 메뉴 ID로 가격을 계산합니다.
    for item in request.items:
        menu = next((m for m in MOCK_MENUS if m["menuId"] == item.menuId), None)
        if not menu:
            raise HTTPException(status_code=400, detail=f"메뉴 ID {item.menuId}를 찾을 수 없습니다.")
        
        price = menu["price"]
        total_price += price * item.quantity
        order_items_detail.append({
            "menuId": menu["menuId"],
            "name": menu["name"],
            "quantity": item.quantity,
            "price": price
        })

    # 한국 시간(KST) 생성
    now_kst = (datetime.utcnow() + timedelta(hours=9)).isoformat() + "+09:00"

    # 새로운 주문 데이터 생성 후 가짜 DB에 저장
    new_order = {
        "orderId": order_id_counter,
        "gameId": request.gameId,
        "seatId": request.seatId,
        "status": "ORDERED",
        "totalPrice": total_price,
        "items": order_items_detail,
        "createdAt": now_kst
    }
    MOCK_ORDERS.append(new_order)
    order_id_counter += 1

    return {
        "success": True,
        "data": {
            "orderId": new_order["orderId"],
            "status": new_order["status"],
            "totalPrice": new_order["totalPrice"]
        },
        "message": "주문이 생성되었습니다. (비동기 큐 연동 준비 중)"
    }

# 3. 내 주문 목록 조회
@app.get("/api/orders/my")
def get_my_orders():
    # 현재는 가짜 DB에 있는 모든 주문을 반환합니다. 
    # (나중에 JWT에서 userId를 추출해 필터링하도록 수정될 예정)
    result = []
    for o in MOCK_ORDERS:
        result.append({
            "orderId": o["orderId"],
            "gameId": o["gameId"],
            "status": o["status"],
            "totalPrice": o["totalPrice"],
            "createdAt": o["createdAt"]
        })
    
    return {
        "success": True,
        "data": result
    }

# 4. 주문 상세 조회
@app.get("/api/orders/{orderId}")
def get_order_detail(orderId: int):
    order = next((o for o in MOCK_ORDERS if o["orderId"] == orderId), None)
    if not order:
        raise HTTPException(status_code=404, detail="주문을 찾을 수 없습니다.")
    
    return {
        "success": True,
        "data": order
    }

# 5. 주문 상태 변경 (관리자/직원용)
@app.patch("/api/orders/{orderId}/status")
def update_order_status(orderId: int, request: OrderStatusUpdate):
    order = next((o for o in MOCK_ORDERS if o["orderId"] == orderId), None)
    if not order:
        raise HTTPException(status_code=404, detail="주문을 찾을 수 없습니다.")
    
    order["status"] = request.status
    
    return {
        "success": True,
        "data": {
            "orderId": order["orderId"],
            "status": order["status"]
        },
        "message": "주문 상태가 변경되었습니다."
    }