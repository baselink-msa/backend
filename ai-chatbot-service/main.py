from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import Optional
import os
import boto3
from botocore.exceptions import NoCredentialsError

app = FastAPI(title="AI Chatbot Service", description="야구 규칙 및 구장 안내 FAQ + AI 챗봇 API")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

MOCK_FAQS = [
    {"faqId": 1, "category": "RULE", "question": "스트라이크가 뭐야?", "answer": "스트라이크는 타자가 치지 않았거나 헛스윙한 공 중 심판이 스트라이크로 판정한 공입니다."},
    {"faqId": 2, "category": "TERM", "question": "병살타가 뭐야?", "answer": "병살타는 하나의 플레이로 두 명의 주자가 아웃되는 상황입니다."}
]

KNOWLEDGE_BASE_ID = os.getenv("KNOWLEDGE_BASE_ID", "49LLIN3BEV")
MODEL_ARN = "arn:aws:bedrock:ap-northeast-2::foundation-model/anthropic.claude-3-haiku-20240307-v1:0"

bedrock_agent_client = boto3.client(
    service_name='bedrock-agent-runtime',
    region_name=os.getenv("AWS_DEFAULT_REGION", "ap-northeast-2"),
    aws_access_key_id=os.getenv("AWS_ACCESS_KEY_ID"),
    aws_secret_access_key=os.getenv("AWS_SECRET_ACCESS_KEY"),
)

class ChatRequest(BaseModel):
    message: str

@app.get("/")
def root():
    return {"message": "AI 챗봇 서비스가 정상 작동 중입니다!"}

@app.get("/api/chatbot/faqs")
def get_faqs(category: Optional[str] = None):
    result = MOCK_FAQS
    if category:
        result = [f for f in MOCK_FAQS if f["category"] == category]
    return {"success": True, "data": result}

@app.post("/api/chatbot/messages")
def ask_chatbot(request: ChatRequest):
    clean_message = request.message.replace(" ", "").lower()

    for faq in MOCK_FAQS:
        if faq["question"].replace(" ", "").lower() == clean_message:
            return {
                "success": True,
                "data": {
                    "answer": faq["answer"],
                    "source": "FAQ (DB/Redis)",
                    "cached": True
                }
            }

    try:
        response = bedrock_agent_client.retrieve_and_generate(
            input={"text": f"한국어로 답해줘. 야구나 구장, 주문 관련 질문이 아니면 '죄송합니다. 야구 및 구장 관련 질문만 답변 가능합니다.' 라고 해줘. 질문: {request.message}"},
            retrieveAndGenerateConfiguration={
                "type": "KNOWLEDGE_BASE",
                "knowledgeBaseConfiguration": {
                    "knowledgeBaseId": KNOWLEDGE_BASE_ID,
                    "modelArn": MODEL_ARN,
                    "retrievalConfiguration": {
                        "vectorSearchConfiguration": {
                            "numberOfResults": 3
                        }
                    }
                }
            }
        )

        ai_answer = response["output"]["text"]

        return {
            "success": True,
            "data": {
                "answer": ai_answer,
                "source": "AWS Bedrock Knowledge Base",
                "cached": False
            }
        }

    except NoCredentialsError:
        return {
            "success": False,
            "error": {
                "code": "AWS_CREDENTIALS_MISSING",
                "message": "AWS 자격 증명이 설정되지 않았습니다."
            }
        }
    except Exception as e:
        return {
            "success": False,
            "error": {
                "code": "AI_GENERATION_FAILED",
                "message": f"AI 답변 생성 중 오류가 발생했습니다: {str(e)}"
            }
        }
