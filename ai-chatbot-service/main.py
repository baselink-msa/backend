import os
import time
from contextlib import asynccontextmanager

import psycopg2
import psycopg2.extras
import boto3
from botocore.exceptions import NoCredentialsError
from fastapi import FastAPI, Request, Response
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from prometheus_client import generate_latest, CONTENT_TYPE_LATEST, Counter, Histogram
from pydantic import BaseModel
from typing import Optional

from db_pool import DbPoolExhaustedError, close_db_pool, db_connection


@asynccontextmanager
async def lifespan(application: FastAPI):
    try:
        yield
    finally:
        close_db_pool()


app = FastAPI(
    title="AI Chatbot Service",
    description="야구 규칙 및 구장 안내 FAQ + AI 챗봇 API",
    lifespan=lifespan,
)


@app.exception_handler(DbPoolExhaustedError)
async def db_pool_exhausted_handler(request: Request, exc: DbPoolExhaustedError):
    return JSONResponse(
        status_code=503,
        content={
            "success": False,
            "error": {
                "code": "DB_CONNECTION_POOL_EXHAUSTED",
                "message": "DB 연결이 사용 중입니다. 잠시 후 다시 시도해 주세요.",
            },
        },
    )

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Prometheus 메트릭 정의
REQUEST_COUNT = Counter(
    'http_requests_total',
    'Total HTTP requests',
    ['method', 'endpoint', 'status']
)
REQUEST_LATENCY = Histogram(
    'http_request_duration_seconds',
    'HTTP request latency',
    ['endpoint']
)

@app.middleware("http")
async def metrics_middleware(request, call_next):
    start = time.time()
    response = await call_next(request)
    duration = time.time() - start
    REQUEST_COUNT.labels(request.method, request.url.path, response.status_code).inc()
    REQUEST_LATENCY.labels(request.url.path).observe(duration)
    return response

@app.get("/actuator/prometheus")
def metrics():
    return Response(generate_latest(), media_type=CONTENT_TYPE_LATEST)

# Bedrock Agent 설정
AGENT_ID = os.getenv("BEDROCK_AGENT_ID", "PZBTYB3SFA")
AGENT_ALIAS_ID = os.getenv("BEDROCK_AGENT_ALIAS_ID", "PZJI9ZGBIQ")
REGION = os.getenv("AWS_DEFAULT_REGION", os.getenv("AWS_REGION", "ap-northeast-2"))

bedrock_agent_client = None
try:
    bedrock_agent_client = boto3.client(
        service_name='bedrock-agent-runtime',
        region_name=REGION,
    )
except Exception:
    pass


class ChatRequest(BaseModel):
    message: str


@app.get("/")
def root():
    return {"message": "AI 챗봇 서비스가 정상 작동 중입니다!"}


@app.get("/api/chatbot/faqs")
def get_faqs(category: Optional[str] = None):
    """실제 DB에서 FAQ 조회"""
    with db_connection() as conn:
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


@app.post("/api/chatbot/messages")
def ask_chatbot(request: ChatRequest):
    """FAQ DB 매칭 → 없으면 Bedrock Agent로 답변"""
    with db_connection() as conn:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute("""
                SELECT answer FROM chatbot_schema.faq
                WHERE enabled = true AND LOWER(REPLACE(question, ' ', '')) = LOWER(REPLACE(%s, ' ', ''))
                LIMIT 1
            """, (request.message,))
            row = cur.fetchone()

    if row:
        return {
            "success": True,
            "data": {
                "answer": row["answer"],
                "source": "FAQ (DB)",
                "cached": True,
            }
        }

    if not bedrock_agent_client or not AGENT_ID:
        return {
            "success": True,
            "data": {
                "answer": "죄송합니다. AI 답변 서비스가 현재 설정되지 않았습니다. FAQ에 등록된 질문을 시도해 주세요.",
                "source": "fallback",
                "cached": False,
            }
        }

    try:
        response = bedrock_agent_client.invoke_agent(
            agentId=AGENT_ID,
            agentAliasId=AGENT_ALIAS_ID,
            sessionId=os.urandom(16).hex(),
            inputText=request.message,
        )

        answer_parts = []
        for event in response["completion"]:
            if "chunk" in event:
                chunk_text = event["chunk"]["bytes"].decode("utf-8")
                answer_parts.append(chunk_text)

        ai_answer = "".join(answer_parts)

        if not ai_answer.strip():
            ai_answer = "죄송합니다. 답변을 생성하지 못했습니다. 다시 시도해 주세요."

        return {
            "success": True,
            "data": {
                "answer": ai_answer,
                "source": "AWS Bedrock Agent",
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
