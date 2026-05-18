from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import List, Optional
import os
import time
import boto3
import json
from botocore.exceptions import NoCredentialsError, ClientError

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

bedrock_client = boto3.client(
    service_name='bedrock-runtime',
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
        prompt = f"""
        너는 친절하고 전문적인 한국 프로야구 고객센터 AI 챗봇이야. 
        사용자의 질문에 대해 3문장 이내로 아주 쉽고 친절하게 대답해줘.
        사용자 질문: {request.message}
        """
        
        body = json.dumps({
            "anthropic_version": "bedrock-2023-05-31",
            "max_tokens": 300,
            "messages": [{"role": "user", "content": prompt}]
        })

        response = bedrock_client.invoke_model(
            modelId='anthropic.claude-3-haiku-20240307-v1:0',
            body=body
        )
        
        response_body = json.loads(response.get('body').read())
        ai_answer = response_body.get('content')[0].get('text')

        return {
            "success": True,
            "data": {
                "answer": ai_answer,
                "source": "AWS Bedrock (AI)",
                "cached": False
            }
        }

    except NoCredentialsError:
        return {
            "success": False,
            "error": {
                "code": "AWS_CREDENTIALS_MISSING",
                "message": "AWS 자격 증명(Access Key)이 설정되지 않아 AI를 호출할 수 없습니다."
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