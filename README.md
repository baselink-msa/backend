# BaseLink Backend

야구장 통합 관람 플랫폼 BaseLink의 백엔드 MSA입니다. Spring Boot 7개 서비스와 FastAPI 2개 서비스로 구성되며, EKS 배포 이미지는 GitHub Actions에서 ECR로 빌드/푸시합니다.

## 서비스 구성

| 서비스 | 런타임 | 포트 | 역할 |
| --- | --- | ---: | --- |
| auth-service | Spring Boot / Java 17 | 8081 | 회원가입, 로그인, JWT 발급 |
| game-service | Spring Boot / Java 17 | 8082 | 경기, 구장, 좌석 조회 |
| admin-service | Spring Boot / Java 17 | 8083 | 관리자 CRUD, 구장 기본 좌석 생성, 경기 좌석 연결 |
| waiting-room-service | Spring Boot / Java 17 | 8084 | Redis 기반 예매 대기열 |
| ticket-worker-service | Spring Boot / Java 17 | 8085 | SQS 예매 메시지 검증 소비자 |
| seat-lock-service | Spring Boot / Java 17 | 8086 | Redis 좌석 잠금, game_seats 상태 동기화 |
| ticket-service | Spring Boot / Java 17 | 8087 | 예매 생성, 상세/목록 조회, 확정, 취소 |
| order-service | FastAPI / Python | 8001 | 주류 메뉴 및 주문 API, RDS 연동 |
| ai-chatbot-service | FastAPI / Python | 8000 | FAQ 조회 및 Bedrock 기반 AI 답변 |

## 주요 기능

- 관리자 API
  - 구장, 경기, 좌석구역, 좌석, 메뉴, FAQ CRUD
  - 구장 등록 시 기본 5개 구역 x 200석 자동 생성
  - 경기 등록 시 해당 구장의 좌석을 `ticket_schema.game_seats`에 자동 연결
  - dev 환경에서는 인증을 `permitAll`로 완화
- 예매 API
  - 예매 요청, 예매 상세 조회, 내 예매 목록
  - 사용자 직접 확정/취소
  - 확정 시 `game_seats.status=SOLD`, 취소 시 `AVAILABLE`
- 좌석 잠금
  - Redis 잠금 생성/해제
  - DB `game_seats.status`를 `LOCKED`/`AVAILABLE`로 업데이트
- 주문/챗봇
  - Mock 데이터 제거
  - PostgreSQL RDS 스키마 직접 조회

## 로컬 실행

### 사전 준비

- JDK 17 이상
- Docker / Docker Compose
- Python 3.11 이상

### 공통 DB 실행

```bash
docker compose up -d
```

PostgreSQL 컨테이너가 올라오며 `db/init.sql` 기준으로 로컬 스키마와 초기 데이터가 생성됩니다.

### Spring Boot 서비스 실행

```bash
./gradlew :auth-service:bootRun
./gradlew :game-service:bootRun
./gradlew :admin-service:bootRun
./gradlew :waiting-room-service:bootRun
./gradlew :ticket-worker-service:bootRun
./gradlew :seat-lock-service:bootRun
./gradlew :ticket-service:bootRun
```

### FastAPI 서비스 실행

```bash
cd order-service
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --reload --port 8001

cd ../ai-chatbot-service
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --reload --port 8000
```

## 환경변수

Kubernetes 배포에서는 `baselink-gitops/base/configmap.yaml`과 `backend-secret`으로 주입합니다.

| 변수 | 설명 |
| --- | --- |
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | DB 사용자 |
| `SPRING_DATASOURCE_PASSWORD` | DB 비밀번호 |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | dev 기본값 `update` |
| `SPRING_DATA_REDIS_HOST` | Redis/ElastiCache 호스트 |
| `SPRING_DATA_REDIS_PORT` | Redis 포트 |
| `APP_JWT_SECRET` | JWT 서명 키 |
| `AWS_REGION` | AWS 리전 |
| `SPRING_CLOUD_AWS_SQS_ENDPOINT` | SQS 엔드포인트 |
| `SQS_TICKET_CONFIRM_QUEUE_NAME` | 예매 검증 큐 이름 |
| `KNOWLEDGE_BASE_ID` | Bedrock Knowledge Base ID |

## CI/CD

`.github/workflows`에 서비스별 워크플로우 9개가 있습니다.

- `main`, `feature/ci-cd` 브랜치 push 시 변경 서비스만 실행
- Docker Buildx + QEMU 사용
- `linux/amd64,linux/arm64` multi-arch 이미지 빌드
- ECR 태그: `740831361032.dkr.ecr.ap-northeast-2.amazonaws.com/dev-<service-name>:<github.sha>`
- `main` 브랜치에서는 GitOps 저장소의 `overlays/dev/kustomization.yaml` 이미지 태그를 자동 업데이트

필수 GitHub Secrets:

```text
AWS_ACCOUNT_ID
AWS_ACCESS_KEY_ID
AWS_SECRET_ACCESS_KEY
GITOPS_TOKEN
```

## 배포 확인

백엔드 배포 상태는 GitOps 저장소에서 확인합니다.

```bash
cd ../../baselink-gitops
kubectl kustomize overlays/dev
kubectl get deploy,pods,svc,ingress -n baselink-dev
kubectl get applications -A
```

AWS CLI 세션이 만료된 경우 `kubectl`과 ECR 조회가 실패합니다. 먼저 AWS 인증을 갱신한 뒤 다시 확인합니다.

```bash
aws login
aws eks update-kubeconfig --region ap-northeast-2 --name baselink-dev
```

## 알려진 이슈

- Redis 좌석 잠금 TTL이 자연 만료될 때 DB `game_seats.status`가 `LOCKED`로 남을 수 있습니다. Redis keyspace notification 리스너 또는 주기적 정리 배치가 필요합니다.
- 팀 등록/수정/삭제 API는 아직 별도 엔티티가 없어 미구현입니다.
- 관리자 권한 부여/회수 API는 auth-service 영역 TODO입니다.
