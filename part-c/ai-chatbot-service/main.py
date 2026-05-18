from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI()

# 1. 프론트엔드에서 우리에게 보낼 '질문' 데이터의 모양을 정의합니다.
class ChatRequest(BaseModel):
    message: str

# 2. 기본 환영 메시지
@app.get("/")
def read_root():
    return {"message": "안녕하세요 예림님! 파트 C 백엔드 서버가 무사히 켜졌습니다."}

# 3. 챗봇 가짜(Mock) API 생성
@app.post("/api/chatbot/messages")
def ask_chatbot(request: ChatRequest):
    return {
        "success": True,
        "data": {
            # 프론트엔드가 보낸 질문을 그대로 메아리처럼 돌려줍니다.
            "answer": f"'{request.message}'에 대한 AI 답변이 곧 연동될 예정입니다!",
            "source": "Mock Data",
            "cached": False
        }
    }