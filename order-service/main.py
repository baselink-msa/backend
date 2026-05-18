from fastapi import FastAPI
from routers import menu, order

app = FastAPI()

app.include_router(menu.router)
app.include_router(order.router)

@app.get("/health")
def health_check():
    return {"status": "order-service is running!"}