# 야구장 플랫폼 — 파트 A (베이스 도메인 & 인증)

auth-service · game-service · admin-service 3개 서비스로 구성된 멀티 프로젝트 Gradle 빌드.

## 구성

| 서비스 | 포트 | 역할 |
| --- | --- | --- |
| auth-service | 8081 | 회원가입 / 로그인 / JWT 발급 |
| game-service | 8082 | 경기 · 구장 · 좌석 조회 |
| admin-service | 8083 | 관리자 등록 API (경기 · 좌석 등) |

## 사전 준비

- JDK 17 이상
- Docker / Docker Compose

## 실행 방법

### 1. 로컬 DB 기동

```
docker compose up -d
```

PostgreSQL 컨테이너가 뜨면서 `db/init.sql`이 자동 실행된다
(스키마 · 테이블 생성 + 시드 데이터 입력).

### 2. 서비스 실행

각 서비스를 별도 터미널에서 실행한다.

```
./gradlew :auth-service:bootRun
./gradlew :game-service:bootRun
./gradlew :admin-service:bootRun
```

## 시드 계정

| 이메일 | 비밀번호 | 권한 |
| --- | --- | --- |
| admin@baseball.com | admin1234 | ADMIN |

## 기능 테스트 흐름

```
1. 로그인 → JWT 획득
   POST http://localhost:8081/api/auth/login
   { "email": "admin@baseball.com", "password": "admin1234" }

2. 관리자가 경기/좌석 생성 (1번 토큰을 Authorization 헤더에)
   POST http://localhost:8083/api/admin/games
   POST http://localhost:8083/api/admin/seat-sections
   POST http://localhost:8083/api/admin/seats
   POST http://localhost:8083/api/admin/games/{gameId}/seats

3. 경기/좌석 조회
   GET http://localhost:8082/api/games
   GET http://localhost:8082/api/games/{gameId}/seats
```

curl 예시:

```
# 로그인
curl -X POST http://localhost:8081/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@baseball.com","password":"admin1234"}'

# 경기 등록 (TOKEN은 위 응답의 accessToken)
curl -X POST http://localhost:8083/api/admin/games \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer TOKEN' \
  -d '{"homeTeamName":"KIA","awayTeamName":"LG","stadiumId":1,
       "gameStartTime":"2026-06-01T18:30:00","ticketOpenTime":"2026-05-25T14:00:00"}'
```

## 참고

- DB 스키마는 `db/init.sql`이 단일 기준이며, 각 서비스는 `ddl-auto: validate`로
  엔티티와 스키마 일치만 검증한다.
- JWT는 HS256 + 공유 시크릿. 세 서비스의 `app.jwt.secret` 값이 동일해야 한다.
  (기본값은 application.yml에 있으며, 운영 시 `APP_JWT_SECRET` 환경변수로 주입)
- Docker / K8s 배포 매니페스트는 인프라 통합 단계에서 추가한다.

## 트러블슈팅

- **DB를 처음부터 다시 만들고 싶을 때** — `db/init.sql`은 컨테이너 최초 기동 시에만
  실행된다. 스키마를 갈아엎으려면 볼륨까지 삭제한다.

  ```
  docker compose down -v
  docker compose up -d
  ```

- **`Schema-validation` 오류로 서비스가 안 뜰 때** — 엔티티와 `init.sql` 스키마가
  어긋난 경우다. `init.sql`을 최신 상태로 맞춘 뒤 위 명령으로 DB를 재생성한다.

- **포트 충돌** — 8081~8083 또는 5432가 이미 사용 중이면 해당 프로세스를 종료하거나
  `application.yml` / `docker-compose.yml`의 포트를 변경한다.
