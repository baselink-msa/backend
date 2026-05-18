from pydantic import BaseModel

class OrderCreate(BaseModel):
    menu_id: int
    quantity: int
    seat_number: str

class OrderResponse(BaseModel):
    order_id: int
    menu_id: int
    quantity: int
    seat_number: str
    status: str