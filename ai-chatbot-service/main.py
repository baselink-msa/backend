import os

import psycopg2
import psycopg2.extras
import boto3
from botocore.exceptions import NoCredentialsError
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import Optional


app = FastAPI(title="AI Chatbot Service", description="야구 규칙 및 구장 안내 FAQ + AI 챗봇 API")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

KNOWLEDGE_BASE_ID = os.getenv("KNOWLEDGE_BASE_ID", "")
MODEL_ARN = "arn:aws:bedrock:ap-northeast-2::foundation-model/anthropic.claude-3-haiku-20240307-v1:0"

bedrock_agent_client = None
try:
    bedrock_agent_client = boto3.client(
        service_name='bedrock-agent-runtime',
        region_name=os.getenv("AWS_DEFAULT_REGION", os.getenv("AWS_REGION", "ap-northeast-2")),
    )
except Exception:
    pass


def get_db():
    """RDS 연결"""
    dsn_url = os.getenv("SPRING_DATASOURCE_URL", "")
    host_part = dsn_url.replace("jdbc:postgresql://", "")
    host_port, db_name = host_part.rsplit("/", 1) if "/" in host_part else (host_part, "baseball_platform")
    host, port = host_port.split(":") if ":" in host_port else (host_port, "5432")

    return psycopg2.connect(
        host=host,
        port=port,
        dbname=db_name,
        user=os.getenv("SPRING_DATASOURCE_USERNAME", "baseball"),
        password=os.getenv("SPRING_DATASOURCE_PASSWORD", "baseball"),
    )


class ChatRequest(BaseModel):
    message: str


@app.get("/")
def root():
    return {"message": "AI 챗봇 서비스가 정상 작동 중입니다!"}


@app.get("/api/chatbot/faqs")
def get_faqs(category: Optional[str] = None):
    """실제 DB에서 FAQ 조회"""
    conn = get_db()
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            if category:
                cur.execute("""
                    SELECT faq_id AS "faqId", category, question, answer, enabled
                    FROM chatbot_schema.faq
                    WHERE category = %s AND enabled = true
                    ORDER BY faq_id
                """, (category,))
            else:
                cur.execute("""
                    SELECT faq_id AS "faqId", category, question, answer, enabled
                    FROM chatbot_schema.faq
                    WHERE enabled = true
                    ORDER BY faq_id
                """)
            faqs = cur.fetchall()
        return {"success": True, "data": faqs}
    finally:
        conn.close()


@app.post("/api/chatbot/messages")
def ask_chatbot(request: ChatRequest):
    """FAQ DB 매칭 → 없으면 Bedrock Knowledge Base로 답변"""
    # 1. DB에서 FAQ 매칭 시도
    conn = get_db()
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute("""
                SELECT answer FROM chatbot_schema.faq
                WHERE enabled = true AND LOWER(REPLACE(question, ' ', '')) = LOWER(REPLACE(%s, ' ', ''))
                LIMIT 1
            """, (request.message,))
            row = cur.fetchone()
    finally:
        conn.close()

    if row:
        return {
            "success": True,
            "data": {
                "answer": row["answer"],
                "source": "FAQ (DB)",
                "cached": True,
            }
        }

    # 2. Bedrock Knowledge Base 호출
    if not bedrock_agent_client or not KNOWLEDGE_BASE_ID:
        return {
            "success": True,
            "data": {
                "answer": "죄송합니다. AI 답변 서비스가 현재 설정되지 않았습니다. FAQ에 등록된 질문을 시도해 주세요.",
                "source": "fallback",
                "cached": False,
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
                "cached": False,
            }
        }
    except NoCredentialsError:
        return {
            "success": False,
            "error": {
                "code": "AWS_CREDENTIALS_MISSING",
                "message": "AWS 자격 증명이 설정되지 않았습니다.",
            }
        }
    except Exception as e:
        return {
            "success": False,
            "error": {
                "code": "AI_GENERATION_FAILED",
                "message": f"AI 답변 생성 중 오류가 발생했습니다: {str(e)}",
            }
        }
