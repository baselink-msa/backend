from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import List, Optional
import time

app = FastAPI(title="AI Chatbot Service", description="야구 규칙 및 구장 안내 FAQ 챗봇 API (Mock)")

# --- [가짜(Mock) 데이터베이스 세팅] ---
# 나중에 Redis 캐시와 RDS(PostgreSQL), S3를 연동해서 대체할 부분입니다.
MOCK_FAQS = [
    {"faqId": 1, "category": "RULE", "question": "스트라이크가 뭐야?", "answer": "스트라이크는 타자가 치지 않았거나 헛스윙한 공 중 심판이 스트라이크로 판정한 공입니다."},
    {"faqId": 2, "category": "TERM", "question": "병살타가 뭐야?", "answer": "병살타는 하나의 플레이로 두 명의 주자가 아웃되는 상황입니다."},
    {"faqId": 3, "category": "STADIUM", "question": "구장 내 흡연이 가능한가요?", "answer": "경기장 내 모든 구역은 금연입니다. 지정된 흡연 구역을 이용해 주세요."},
    {"faqId": 4, "category": "TICKETING", "question": "티켓 취소는 언제까지 가능한가요?", "answer": "경기 시작 2시간 전까지 취소 가능합니다. 이후에는 취소가 불가합니다."},
    {"faqId": 5, "category": "ORDER", "question": "주류 주문은 어떻게 하나요?", "answer": "앱 내 주문 서비스에서 원하는 메뉴를 선택하여 주문할 수 있습니다."}
]

# --- [Pydantic 모델 정의] ---
class ChatRequest(BaseModel):
    message: str

# --- [API 엔드포인트 구현] ---

@app.get("/")
def root():
    return {"message": "AI 챗봇 서비스가 정상 작동 중입니다! /docs 로 이동해서 API를 확인하세요."}

# 10-1. FAQ 목록 조회
@app.get("/api/chatbot/faqs")
def get_faqs(category: Optional[str] = None):
    # 카테고리가 지정되면 필터링, 아니면 전체 반환
    result = MOCK_FAQS
    if category:
        result = [f for f in MOCK_FAQS if f["category"] == category]
    
    return {
        "success": True,
        "data": result
    }

# 10-2. 챗봇 질문
@app.post("/api/chatbot/messages")
def ask_chatbot(request: ChatRequest):
    # 챗봇이 답변을 고민하는 척 약간의 지연 시간을 둡니다. (Mock 효과)
    time.sleep(0.5)

    # 1단계: MOCK_FAQS에서 유사 질문 검색 (가장 원시적인 형태)
    # 실제로는 Redis 캐시 확인 -> RDS FAQ 검색 -> Bedrock LLM 호출 순서로 진행될 예정
    
    # 공백 제거 및 소문자 변환 후 비교
    clean_message = request.message.replace(" ", "").lower()
    
    faq_answer = None
    for faq in MOCK_FAQS:
        if faq["question"].replace(" ", "").lower() == clean_message:
            faq_answer = faq["answer"]
            break
            
    if faq_answer:
        return {
            "success": True,
            "data": {
                "answer": faq_answer,
                "source": "FAQ", # 답변 출처 명시
                "cached": False  # 캐시 여부 표시
            }
        }
        
    # 2단계: FAQ에 없는 질문인 경우 (가짜 대답 반환)
    return {
        "success": True,
        "data": {
            "answer": f"'{request.message}' - 질문에 대한 AI 답변을 생성 중입니다! (실제 AI 연동 준비 대기 중)",
            "source": "Mock Data", # 출처 명시
            "cached": False  # 캐시 여부 표시
        }
    }