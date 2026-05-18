from fastapi import FastAPI
from pydantic import BaseModel
from typing import List

# 주문 전용 서버
app = FastAPI(title="Order Service")

# 프론트엔드가 보낼 주문 데이터 형태
class OrderItem(BaseModel):
    menuId: int
    quantity: int

class OrderRequest(BaseModel):
    gameId: int
    seatId: int
    items: List[OrderItem]

# 1. 정문 매표소 (기본 주소) - 404 에러 방지용
@app.get("/")
def root():
    return {"message": "주문 서비스가 정상 작동 중입니다! /docs 로 이동해서 API를 확인하세요."}

# 2. 주류 메뉴 조회 API
@app.get("/api/orders/menus")
def get_menus():
    return {
        "success": True,
        "data": [
            {"menuId": 1, "name": "생맥주", "price": 6000, "available": True},
            {"menuId": 2, "name": "치킨", "price": 18000, "available": True}
        ]
    }

# 3. 주문 생성 API
@app.post("/api/orders")
def create_order(request: OrderRequest):
    return {
        "success": True,
        "data": {
            "orderId": 999,
            "status": "ORDERED",
            "message": "주문이 접수되었습니다! (비동기 큐 연동 준비 중)"
        }
    }