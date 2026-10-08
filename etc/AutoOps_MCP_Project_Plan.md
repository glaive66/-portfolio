# [PRD] AutoOps-MCP: 자율형 백엔드 운영 오케스트레이션 플랫폼 개발 기획서

---

## 1. 프로젝트 개요 (Executive Summary)

* **프로젝트명**: **AutoOps-MCP** (Autonomous Backend Operations Orchestrator via MCP)
* **목적**: 
  * 백오피스 운영자가 자연어로 업무를 지시하면, AI(LLM)가 표준 **MCP(Model Context Protocol)**를 통해 백엔드 시스템의 REST API, DB, 비동기 메시지 큐(RabbitMQ)를 자율적으로 조합하여 단계별로 실행하는 엔터프라이즈 운영 플랫폼 구축.
  * 기존 단순 챗봇의 한계를 극복하고, **Human-in-the-Loop(승인 게이트웨이)**, **Saga 패턴(보상 트랜잭션)**, **불변 감사 로그(Audit Trail)**를 결합하여 실무 엔터프라이즈 환경에서 실제로 안전하게 동작 가능한 차세대 AI 백엔드 아키텍처 구현.
* **가상 도메인**: **B2B 이커머스 & 가맹점 정산/물류 운영 플랫폼**
  * 결제 오류 정산 보류 건 자동 재처리, 대량 알림 발송 큐 적재, 관리자 전자결재 연동 등 실무에서 가장 빈번한 복합 운영 시나리오를 모델링.

---

## 2. 기술 스택 및 환경 (Technology Stack)

| 구분 | 기술 스택 및 버전 | 선정 사유 |
| :--- | :--- | :--- |
| **Language & JDK** | **Java 17** | 불변 Record, Pattern Matching 등 모던 Java 기능 활용 |
| **Framework** | **Spring Boot 3.5.x** | 최신 엔터프라이즈 표준 및 Spring AI 모듈과의 네이티브 호환 |
| **AI Orchestration** | **Spring AI (1.0+) & MCP SDK** | Model Context Protocol 표준 도구(Tool) 및 클라이언트 구현 |
| **LLM Engine** | **Google Gemini 2.0 Flash (Free Tier) / Multi-LLM** | 비용 제로($0) 개발/검증 및 Claude 3.5 / GPT-4o 무중단 스위칭 유연성 확보 |
| **Database** | **PostgreSQL 16** | 안정적인 RDBMS 트랜잭션 및 향후 Vector 확장 용이성 |
| **ORM / Persistence** | **Spring Data JPA & MyBatis** | 복잡한 관리자 통계 집계와 도메인 트랜잭션 분리 |
| **Message Broker** | **RabbitMQ** | 대량 알림/이벤트의 Chunk 단위 비동기 분할 병렬 처리 |
| **View / UI** | **Thymeleaf + Tailwind CSS + SSE** | 운영자 채팅 인터페이스, 실시간 실행 타임라인(SSE 스트리밍) |
| **Infrastructure** | **Docker Compose** | PostgreSQL, RabbitMQ를 로컬에서 즉시 원클릭 구동 |

---

## 3. 시스템 아키텍처 및 동작 메커니즘

```mermaid
flowchart TD
    User(["운영 관리자"]) --> UI["운영 콘솔 UI (Thymeleaf + SSE)"]
    UI --> Controller["Orchestration Controller"]
    Controller --> Agent["Spring AI Agent (Google Gemini 2.0 Flash / Multi-LLM)"]
    
    subgraph MCP Ecosystem
        Agent <-->|MCP Protocol (SSE / STDIO)| MCP_Registry["MCP Server & Tool Registry"]
        MCP_Registry --> T1["Tool: 가맹점 정산 오류 조회"]
        MCP_Registry --> T2["Tool: 맞춤 안내 메일 템플릿 생성"]
        MCP_Registry --> Gate{"Human-in-the-Loop Gate<br/>(상태 변경 / 대량 발송)"}
        Gate -->|위험 작업 승인 대기| UI
        UI -->|관리자 승인 확인| Gate
        Gate --> T3["Tool: RabbitMQ 비동기 청크 발행"]
        Gate --> T4["Tool: 관리자 전자결재 자동 상신"]
    end

    T3 --> RabbitMQ[("RabbitMQ Queue")]
    T4 --> DB[("PostgreSQL")]
    Agent --> Audit["불변 감사 로그 (TB_AI_AUDIT_LOG)"]
```

---

## 4. 4대 핵심 엔지니어링 명세 (시니어 차별화 포인트)

### ① MCP(Model Context Protocol) 표준 Tool 설계
* 백엔드의 비즈니스 서비스를 Spring AI의 표준 `@Tool` 및 MCP Tool 규격으로 노출.
* 각 도구는 명확한 JSON Schema, 파라미터 제약조건, 필수값 검증 로직을 포함.
  * `findSettlementErrors(yearMonth, minErrorCount)`: 정산 오류 가맹점 목록 조회
  * `generateNoticeTemplate(merchantId, errorReason)`: 맞춤 안내문 생성
  * `enqueueBulkNotice(merchantIds, payload)`: RabbitMQ 비동기 발송 큐 적재
  * `createApprovalDraft(title, content, targetCount)`: 전자결재 상신

### ② Human-in-the-Loop (승인 게이트웨이)
* 조회성 도구(`READ`)는 AI가 자율적으로 연속 실행.
* 데이터 수정/삭제, 결제/정산, 대량 발송 등 부수 효과(Side-Effect)가 있는 `MUTATION` 도구는 실행 전 **실행 계획(Plan)을 생성하고 일시 정지(SUSPEND)**.
* 관리자 UI로 SSE(Server-Sent Events)를 통해 *"정산 보류 가맹점 50곳에 메일 발송 및 결재를 상신하시겠습니까?"* 승인 카드 전송.
* 관리자 승인 클릭 시 저장된 세션 상태를 복원하여 잔여 단계 재개.

### ③ 비동기 분할 처리 & 보상 트랜잭션 (Saga Pattern)
* 대량 처리 시 WAS 스레드 고갈 방지를 위해 **RabbitMQ 10~50건 단위 Chunk 분할 메시징** 적용.
* 다단계 실행 중 특정 도구에서 타임아웃이나 비즈니스 예외 발생 시, 앞서 실행된 작업에 대해 보상 도구(`Compensating Tool`)를 호출하여 데이터 정합성 유지.

### ④ 불변 감사 추적 (Audit Trail)
* AI의 의사결정 블랙박스 문제를 해결하기 위해 모든 실행 이력을 전수 기록.
* **기록 항목**: 세션 ID, 관리자 ID, 사용자 자연어 프롬프트, LLM 추론 근거(Thought Process), 호출된 Tool 명칭, 입력 인자(Arguments), 결과 응답, 실행 소요 시간, 승인자 정보.

---

## 5. 데이터베이스 스키마 설계 (ERD 요약)

```sql
-- 1. 가맹점 정산 테이블 (가상 도메인)
CREATE TABLE TB_MERCHANT_SETTLEMENT (
    SETTLEMENT_ID       VARCHAR(32) PRIMARY KEY,
    MERCHANT_NAME       VARCHAR(100) NOT NULL,
    SETTLEMENT_YM       VARCHAR(6) NOT NULL,
    STATUS              VARCHAR(20) NOT NULL, -- PENDING, COMPLETED, ERROR
    FAIL_REASON         VARCHAR(255),
    PENDING_AMOUNT      NUMERIC(15, 2) NOT NULL,
    CREATED_AT          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. 전자결재 상신 테이블
CREATE TABLE TB_APPROVAL_DRAFT (
    DRAFT_ID            VARCHAR(32) PRIMARY KEY,
    TITLE               VARCHAR(200) NOT NULL,
    CONTENT             TEXT NOT NULL,
    STATUS              VARCHAR(20) NOT NULL, -- DRAFTED, APPROVED, REJECTED
    CREATED_BY_AI       BOOLEAN DEFAULT TRUE,
    CREATED_AT          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. AI 오케스트레이션 실행 감사 로그 (핵심)
CREATE TABLE TB_AI_AUDIT_LOG (
    AUDIT_ID            BIGSERIAL PRIMARY KEY,
    EXECUTION_ID        VARCHAR(64) NOT NULL,
    USER_ID             VARCHAR(50) NOT NULL,
    USER_PROMPT         TEXT NOT NULL,
    TOOL_NAME           VARCHAR(100) NOT NULL,
    TOOL_ARGUMENTS      JSONB NOT NULL,
    TOOL_RESULT         JSONB,
    IS_MUTATION         BOOLEAN NOT NULL,
    APPROVAL_STATUS     VARCHAR(20), -- NONE, PENDING, APPROVED, REJECTED
    EXECUTION_TIME_MS   BIGINT,
    CREATED_AT          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

## 6. 단계별 개발 로드맵 (4단계)

* **Phase 1: 인프라 및 도메인 기초 환경 구축 (Day 1)**
  * Docker Compose로 PostgreSQL 16 및 RabbitMQ 환경 구성.
  * Spring Boot 3.5 프로젝트 생성 및 가상 도메인(정산, 결재, 감사로그) 엔티티/리포지토리 구현.
  * 목(Mock) 데이터 50~100건 적재 스크립트 작성.

* **Phase 2: Spring AI & MCP Tool Registry 구현 (Day 2)**
  * Spring AI 의존성 추가 및 Google Gemini (Gemini 2.0 Flash 무료 티어) 연동 및 다중 LLM 프로파일 구조화.
  * 정산 조회, 템플릿 생성, 결재 상신, RabbitMQ 발행 메서드를 `@Tool`로 구현.
  * LLM이 프롬프트를 분석하여 도구들을 체이닝(Chaining) 호출하는 단위 테스트 검증.

* **Phase 3: Human-in-the-Loop & RabbitMQ 비동기 파이프라인 (Day 3)**
  * 위험 도구 호출 전 `ApprovalInterceptor` 구현 (실행 중단 및 상태 저장).
  * RabbitMQ Producer & Consumer(Chunk 분할 수신) 구현.
  * 보상 트랜잭션(에러 시 롤백/취소) 로직 처리.

* **Phase 4: 운영 콘솔 UI & 감사 로그 대시보드 (Day 4~5)**
  * Thymeleaf + Tailwind 기반 관리자 인터페이스 제작.
  * SSE 기반 AI 실행 생각 과정(Reasoning) 및 Tool 실행 실시간 타임라인 출력.
  * 승인 대기 카드 모달 및 클릭 시 재개(Resume) 처리.
  * 깃허브 공개용 README 및 시연 영상(GIF) 산출물 정리.

---

## 7. 차기 채팅창 전달용 '스타터 프롬프트'

새 대화창을 열었을 때 아래 내용을 그대로 첫 질문으로 전송하여 즉시 개발에 착수합니다.

```text
안녕하세요! 저는 14년 차 백엔드 엔지니어이며, 포트폴리오용으로 Spring Boot 3.5와 Spring AI, MCP(Model Context Protocol) 기반의 자율형 백엔드 운영 오케스트레이터 [AutoOps-MCP] 프로젝트를 단계별로 구축하려고 합니다.

[핵심 아키텍처 및 요구사항]
1. 프레임워크: Java 17, Spring Boot 3.5.x, Spring AI (Google Gemini 2.0 Flash 무료 티어 / Multi-LLM), PostgreSQL, RabbitMQ
2. 가상 도메인: B2B 가맹점 정산 오류 조회 -> 안내 메일 생성 -> RabbitMQ 비동기 적재 -> 전자결재 상신
3. 핵심 기능:
   - Spring AI 기반 MCP Tool 등록 및 자율 체이닝
   - Human-in-the-Loop (대량 발송 및 결재 전 관리자 승인 게이트웨이)
   - RabbitMQ Chunk 단위 비동기 분할 처리 및 Saga 패턴(보상 트랜잭션)
   - TB_AI_AUDIT_LOG 테이블을 통한 AI 추론 근거 및 Tool 실행 감사 로그 전수 기록
4. UI: Thymeleaf + Tailwind CSS + SSE 실시간 실행 타임라인

지금부터 Phase 1 (Docker Compose 환경 및 가상 도메인 엔티티/리포지토리 설계)부터 순서대로 프로덕션 수준의 깔끔한 코드로 구현을 시작해 주세요. 먼저 프로젝트 디렉토리 구조와 docker-compose.yml, build.gradle부터 제시해 주세요.
```
