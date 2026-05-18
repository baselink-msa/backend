-- 야구장 플랫폼 — 로컬 개발용 DB 초기화 스크립트
-- docker-compose의 PostgreSQL 컨테이너 최초 기동 시 1회 자동 실행됨
-- 파트 A(auth/game/admin)가 사용하는 스키마/테이블만 포함

CREATE SCHEMA IF NOT EXISTS auth_schema;
CREATE SCHEMA IF NOT EXISTS game_schema;
CREATE SCHEMA IF NOT EXISTS ticket_schema;
CREATE SCHEMA IF NOT EXISTS order_schema;
CREATE SCHEMA IF NOT EXISTS chatbot_schema;

-- ========== auth_schema ==========
CREATE TABLE auth_schema.users (
    user_id       BIGSERIAL    PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    name          VARCHAR(100) NOT NULL,
    role          VARCHAR(30)  NOT NULL DEFAULT 'USER',
    status        VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ========== game_schema ==========
CREATE TABLE game_schema.stadiums (
    stadium_id BIGSERIAL    PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    location   VARCHAR(255),
    capacity   INT,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE game_schema.games (
    game_id          BIGSERIAL    PRIMARY KEY,
    home_team_name   VARCHAR(100) NOT NULL,
    away_team_name   VARCHAR(100) NOT NULL,
    stadium_id       BIGINT       NOT NULL REFERENCES game_schema.stadiums(stadium_id),
    game_start_time  TIMESTAMP    NOT NULL,
    ticket_open_time TIMESTAMP    NOT NULL,
    status           VARCHAR(30)  NOT NULL DEFAULT 'SCHEDULED',
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE game_schema.seat_sections (
    section_id   BIGSERIAL   PRIMARY KEY,
    stadium_id   BIGINT      NOT NULL REFERENCES game_schema.stadiums(stadium_id),
    section_name VARCHAR(50) NOT NULL,
    price        INT         NOT NULL,
    created_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ========== ticket_schema (파트 A는 seats/game_seats만 사용) ==========
CREATE TABLE ticket_schema.seats (
    seat_id     BIGSERIAL   PRIMARY KEY,
    stadium_id  BIGINT      NOT NULL REFERENCES game_schema.stadiums(stadium_id),
    section_id  BIGINT      NOT NULL REFERENCES game_schema.seat_sections(section_id),
    seat_row    VARCHAR(20) NOT NULL,
    seat_number VARCHAR(20) NOT NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (stadium_id, section_id, seat_row, seat_number)
);

CREATE TABLE ticket_schema.game_seats (
    game_seat_id BIGSERIAL   PRIMARY KEY,
    game_id      BIGINT      NOT NULL REFERENCES game_schema.games(game_id),
    seat_id      BIGINT      NOT NULL REFERENCES ticket_schema.seats(seat_id),
    status       VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',
    price        INT         NOT NULL,
    updated_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (game_id, seat_id)
);

CREATE TABLE ticket_schema.waiting_room_policies (
    policy_id            BIGSERIAL PRIMARY KEY,
    game_id              BIGINT    NOT NULL REFERENCES game_schema.games(game_id),
    max_enter_per_minute INT       NOT NULL DEFAULT 100,
    token_ttl_seconds    INT       NOT NULL DEFAULT 300,
    enabled              BOOLEAN   NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (game_id)
);

-- ========== order_schema ==========
CREATE TABLE order_schema.alcohol_menus (
    menu_id    BIGSERIAL    PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    price      INT          NOT NULL,
    available  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ========== chatbot_schema ==========
CREATE TABLE chatbot_schema.faq (
    faq_id     BIGSERIAL   PRIMARY KEY,
    category   VARCHAR(50) NOT NULL,
    question   TEXT        NOT NULL,
    answer     TEXT        NOT NULL,
    enabled    BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ========== 시드 데이터 ==========
-- 관리자 계정  (이메일: admin@baseball.com  /  비밀번호: admin1234)
INSERT INTO auth_schema.users (email, password_hash, name, role, status)
VALUES ('admin@baseball.com',
        '$2b$10$BsY8qAsK0Iw4KVbX5Lr7MuCHLZje5ogazFKvgt3h5kOKROxW8Zqxq',
        '관리자', 'ADMIN', 'ACTIVE');

-- 구장 시드 (구장 등록 API는 명세에 없으므로 시드로 제공)
INSERT INTO game_schema.stadiums (name, location, capacity) VALUES
    ('잠실야구장', '서울특별시 송파구', 25000),
    ('고척스카이돔', '서울특별시 구로구', 16000),
    ('광주-KIA 챔피언스 필드', '광주광역시 북구', 20500);
