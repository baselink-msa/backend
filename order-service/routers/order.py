from fastapi import APIRouter
from models.order import OrderCreate, OrderResponse

router = APIRouter()

orders = []

@router.post("/orders", response_model=OrderResponse)
def create_order(order: OrderCreate):
    new_order = {
        "order_id": len(orders) + 1,
        "menu_id": order.menu_id,
        "quantity": order.quantity,
        "seat_number": order.seat_number,
        "status": "접수완료"
    }
    orders.append(new_order)
    return new_order

@router.get("/orders/{order_id}")
def get_order(order_id: int):
    for order in orders:
        if order["order_id"] == order_id:
            return order
    return {"error": "주문을 찾을 수 없습니다"}