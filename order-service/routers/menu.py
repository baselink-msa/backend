from fastapi import APIRouter

router = APIRouter()

# 임시 메뉴 데이터 (나중에 DB로 교체)
menus = [
    {"menu_id": 1, "name": "치킨", "price": 15000},
    {"menu_id": 2, "name": "맥주", "price": 5000},
    {"menu_id": 3, "name": "핫도그", "price": 4000},
]

@router.get("/menus")
def get_menus():
    return menus