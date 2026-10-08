# AutoOps-MCP 사용자 및 운영자 매뉴얼 (User & Operations Manual)

> **AutoOps-MCP (Autonomous Backend Operations Orchestrator)**  
> 대규모 B2B 금융/정산 도메인에서 자연어 명령 기반 AI 에이전트가 백엔드 표준 도구(MCP Tools)를 자율 체이닝하고, Human-in-the-Loop(HITL) 승인 게이트웨이 및 Saga 분산 트랜잭션을 통해 부수 효과(Side-effect) 없는 안전한 운영 자동화를 제공하는 엔터프라이즈 운영 콘솔입니다.

---

## 📌 목차 (Table of Contents)
1. [시스템 개요 및 아키텍처](#1-시스템-개요-및-아키텍처)
2. [사전 준비 및 환경 기동 가이드](#2-사전-준비-및-환경-기동-가이드)
3. [웹 운영 콘솔 화면 구성 안내](#3-웹-운영-콘솔-화면-구성-안내)
4. [단계별 표준 업무 시나리오 실습](#4-단계별-표준-업무-시나리오-실습)
5. [데이터 연계 및 모니터링 확인 방법](#5-데이터-연계-및-모니터링-확인-방법)
6. [자주 묻는 질문 및 트러블슈팅 (FAQ)](#6-자주-묻는-질문-및-트러블슈팅-faq)

---

## 1. 시스템 개요 및 아키텍처

```
[운영자 웹 콘솔 (8090)]
       │
       ▼ (자연어 지시 / SSE 스트리밍)
[Spring AI 오케스트레이터] ──(Spring AI / Gemini)──▶ 사고 계획(Reasoning)
       │
       ├─▶ READ Tool 1: findSettlementErrors (정산오류 48건 / 5.08억 조회)
       ├─▶ READ Tool 2: generateNoticeTemplate (LLM 기반 맞춤 소명서 생성)
       │
       ▼ (위험 작업 감지: 대량 발송/결재 상신)
[Human-in-the-Loop 승인 게이트웨이] ──▶ 작업 일시 중지 (SUSPENDED)
       │
       ▼ [운영팀장 승인(Approve)]
[Saga 분산 오케스트레이터]
       ├─▶ MUTATION Tool 3: createApprovalDraft (전자결재 품의서 자동 상신)
       └─▶ MUTATION Tool 4: enqueueBulkNotice
                 │
                 ▼ (10건 단위 분할 발행)
           [RabbitMQ 브로커] ──(비동기 Worker 소비)──▶ 카카오 알림톡/메일 발송
```

### 핵심 설계 가치
- **자율성과 내부 통제의 균형**: AI가 읽기(READ) 단계는 완전 자율로 분석하되, 데이터 쓰기/외부 발송(MUTATION) 전 반드시 사람(HITL)의 승인을 거칩니다.
- **분산 트랜잭션 정합성 (Saga Pattern)**: 전자결재 상신과 메시지 브로커 발행 중 하나라도 실패할 경우 자동 보상 트랜잭션(Rollback)을 수행합니다.
- **메모리 고갈 방지 (Chunking Streaming)**: 대량 알림(수만 건 확장 가능)을 10건 단위 청크로 분할하여 브로커로 비동기 스트리밍 처리합니다.
- **불변 감사 추적성 (Immutable Audit Trail)**: AI가 호출한 모든 도구의 입출력 JSONB 및 실행 이력을 데이터베이스에 영구 보존합니다.

---

## 2. 사전 준비 및 환경 기동 가이드

### 2.1 인프라 요구사항
- **Java**: JDK 17
- **Docker & Docker Compose**: PostgreSQL 16, RabbitMQ 3.13-management 구동용

### 2.2 인프라 컨테이너 기동
터미널을 열고 프로젝트 루트 폴더에서 Docker Compose를 실행합니다:
```cmd
cd C:\jun\포트폴리오
docker compose -f docker/docker-compose.yml up -d
```
> **정상 기동 포트**:
> - PostgreSQL: `localhost:5432` (계정: `autoops` / `autoops1234`, DB: `autoops_db`)
> - RabbitMQ AMQP: `localhost:5672` (기본 포트)
> - RabbitMQ 웹 관리자: `http://localhost:15672` (계정: `guest` / `guest`)

### 2.3 백엔드 애플리케이션 기동
```cmd
cd C:\jun\포트폴리오
gradlew bootRun
```
- 정상 기동 시 콘솔에 `Tomcat initialized with port 8090 (http)` 로그가 출력됩니다.
- 브라우저를 열고 **`http://localhost:8090`** 에 접속합니다.

---

## 3. 웹 운영 콘솔 화면 구성 안내

AutoOps 콘솔(`http://localhost:8090`)은 직관적인 4개 핵심 영역으로 구성되어 있습니다.

```
┌────────────────────────────────────────────────────────────────────────┐
│ ① 상단: 자연어 운영 업무 지시바 (Prompt Input & 샘플 버튼)            │
├───────────────────────────────────┬────────────────────────────────────┤
│ ② 좌측: 실시간 AI 사고 타임라인 │ ③ 우측: HITL 관리자 승인 게이트웨이 │
│    - SSE 실시간 스트리밍          │    - 위험 작업 일시 정지 카드      │
│    - 도구 실행 및 청크 진행률    │    - [승인 (Approve)] / [반려]     │
├───────────────────────────────────┴────────────────────────────────────┤
│ ④ 하단: 5대 탭 통합 데이터 보드 (Data Explorer & History)              │
│    [AI 작업 내역 게시판] [정산 오류 목록] [전자결재 이력] [감사 로그] [MQ 청크] │
└────────────────────────────────────────────────────────────────────────┘
```

### ① 자연어 업무 지시바 (Top)
- **입력 필드**: 관리자가 일상적인 자연어로 업무를 지시하는 영역입니다.
- **추천 샘플 버튼**:
  - `💡 9월 정산오류 일괄 분석 & 결재 상신`: 조회 ➡️ 소명서 작성 ➡️ 승인 대기 ➡️ MQ 분할 발행 풀 파이프라인.
  - `🔍 202609 오류 가맹점 현황 조회`: 단순 조회성 읽기(READ) 파이프라인.

### ② 실시간 AI 사고 & 도구 실행 타임라인 (Left)
- **SSE 실시간 스트리밍**: AI가 어떤 도구를 왜 선택했는지(Reasoning), 현재 도구의 반환값이 무엇인지 실시간 카드로 출력됩니다.
- **새 작업 시작 버튼**: 브라우저 로컬 저장소에 보존된 이전 타임라인을 비우고 신규 작업 세션을 시작합니다.

### ③ Human-in-the-Loop 관리자 승인 게이트웨이 (Right)
- AI가 `findSettlementErrors` 및 `generateNoticeTemplate`을 완료한 후, 대량 발송/결재 상신 직전에 작업을 일시 중지(`SUSPENDED`)하고 나타납니다.
- 대상 가맹점 수(48곳), 총 보류 금액(5억 831만 원), 생성된 소명 메일 제목을 검토하고 **[승인 (Approve)]** 또는 **[반려 (Reject)]** 버튼을 누릅니다.

### ④ 5대 통합 데이터 보드 (Bottom)
1. **📋 AI 작업 내역 게시판 (신규)**: 수행된 모든 오케스트레이션 이력(실행 ID, 프롬프트, 대상 규모, 결재 품의 링크, MQ 청크 수, 승인자, 상태)을 영구 보관/조회. 행 클릭 시 작업 상세 모달 팝업.
2. **🧾 가맹점 정산 오류 목록 (Mock 50건)**: PG 오류, 계좌 불일치 등 원천 데이터 현황. 행 클릭 시 AI 소명 안내문 이메일 템플릿 모달 팝업.
3. **📄 전자결재 상신 이력 (TB_APPROVAL_DRAFT)**: AI가 자동 상신한 품의서 문서. 행 클릭 시 품의서 원문 모달 팝업.
4. **🛡️ AI 불변 감사 로그 (TB_AI_AUDIT_LOG)**: AI가 호출한 도구명, 입출력 JSONB 인자, 실행 시간, 승인 여부 전수 기록.
5. **📦 RabbitMQ 청크 발송 모니터링**: 10건 단위로 브로커를 거쳐 비동기 Worker에 의해 소비(ACK)된 실시간 처리 이력.

---

## 4. 단계별 표준 업무 시나리오 실습

### [시나리오] 2026년 9월 정산 오류 가맹점 전수 분석 및 일괄 소명 조치

#### 1단계: 자연어 업무 지시 접수
1. 상단 입력창 우측의 **`💡 9월 정산오류 일괄 분석 & 결재 상신`** 샘플 버튼을 클릭합니다.
2. `2026년 9월 정산 오류 가맹점들을 조회하고 소명 안내문 작성 후 결재 올려줘` 문구가 입력되면 **[실행 (Run)]** 버튼을 클릭합니다.

#### 2단계: AI 자율 탐색 및 계획 수립 (자율 실행)
- 타임라인에 실시간 카드가 순차적으로 출력됩니다:
  1. `[THINKING]` AI 오케스트레이터 계획 수립 (정산 오류 조회 도구 선정)
  2. `[TOOL_START]` `findSettlementErrors` 실행 (202609 대상 가맹점 조회)
  3. `[TOOL_END]` 오류 가맹점 48건 발견 (총 보류금액: 508,312,000 원)
  4. `[TOOL_START]` `generateNoticeTemplate` 실행 (소명 요청 템플릿 생성)
  5. `[TOOL_END]` 템플릿 생성 완료 (`[AutoOps 정산안내] ... 정산 보류 안내 및 조치 요청 건`)

#### 3단계: Human-in-the-Loop 관리자 검토 (작업 일시 중지)
- 우측 승인 게이트웨이에 **노란색 대기 카드**가 팝업됩니다:
  - **작업명**: `executeNoticePipeline`
  - **영향 규모**: 정산 오류 가맹점 48곳 대상 대량 알림 발송 및 결재 상신 (총 508,312,000 원)
  - 하단 **`가맹점 정산 오류 목록`** 탭에서 임의의 가맹점을 클릭하여 AI가 작성한 맞춤 안내문 템플릿을 사전 검토합니다.

#### 4단계: 관리자 승인 (Approve)
- 우측 게이트웨이의 **`[승인 (Approve)]`** 버튼을 클릭합니다.
- 확인 팝업창에서 확인을 누르면 일시 중단되었던 작업이 즉시 재개(`RESUME`)됩니다.

#### 5단계: Saga 분산 트랜잭션 및 RabbitMQ 비동기 청크 발송
- 타임라인에 비동기 청크 발송 및 품의서 상신 로그가 실시간 출력됩니다:
  - `[SAGA_CHUNK]` 총 48개 가맹점을 10건씩 5개 청크로 분할 발행
  - `[CHUNK_PROGRESS]` 청크 #1/5 소비 완료 (20%) ➡️ 카카오 알림톡/메일 비동기 발송 성공 (ACK)
  - `[CHUNK_PROGRESS]` 청크 #2/5 소비 완료 (40%)
  - `[CHUNK_PROGRESS]` 청크 #3/5 소비 완료 (60%)
  - `[CHUNK_PROGRESS]` 청크 #4/5 소비 완료 (80%)
  - `[CHUNK_PROGRESS]` 청크 #5/5 소비 완료 (100%)
  - `[COMPLETED]` 🎉 AutoOps 전체 자율 오케스트레이션 성공 완료

#### 6단계: 결과 확인
- 화면이 자동으로 하단 **`📋 AI 작업 내역 게시판`**으로 전환됩니다.
- 방금 실행한 작업이 `COMPLETED` 상태로 등록되어 있으며:
  - **결재 품의 번호**(`DRAFT-XXXX`) 클릭 ➡️ **전자결재 품의서 모달** 오픈
  - **행 전체 클릭** ➡️ **AI 오케스트레이션 작업 상세 이력 모달** 오픈

---

## 5. 데이터 연계 및 모니터링 확인 방법

### 5.1 RabbitMQ 관리자 콘솔 확인 (`http://localhost:15672`)
1. 브라우저로 접속 (계정: `guest` / `guest`)
2. **`Queues and Streams` 탭** ➡️ **`q.settlement.notice`** 클릭
3. **통계 확인 포인트**:
   - 그래프 제목 옆의 시간 범위를 `last minute`에서 **`last 10 minutes`** 또는 **`last hour`**로 변경합니다.
   - 방금 분할 발송된 청크 수(5건)만큼 `Publish` 및 `Deliver / Ack` 곡선이 선명하게 나타납니다.
   - 현재 대기 메시지(`Ready`)가 `0`인 것은 컨슈머가 정상적으로 소비하여 큐를 비웠음을 의미합니다.

### 5.2 데이터베이스(PostgreSQL) 직접 검증
DBeaver 또는 psql로 데이터베이스를 직접 조회할 수 있습니다:
```sql
-- 1. AI 오케스트레이션 작업 이력 전수 조회
SELECT task_id, execution_id, prompt, status, draft_id, target_merchant_count, total_pending_amount, published_chunks, approver_id, created_at 
FROM tb_ops_task_history ORDER BY task_id DESC;

-- 2. 상신된 전자결재 기안 품의서 확인
SELECT draft_id, title, status, created_by_ai, created_at 
FROM tb_approval_draft ORDER BY created_at DESC;

-- 3. AI 불변 감사 로그(입출력 JSONB) 확인
SELECT audit_id, execution_id, tool_name, is_mutation, approval_status, execution_time_ms, created_at 
FROM tb_ai_audit_log ORDER BY audit_id DESC;
```

---

## 6. 자주 묻는 질문 및 트러블슈팅 (FAQ)

### Q1. 브라우저를 새로고침(F5)하면 타임라인이 날아가나요?
* **아닙니다.** 브라우저의 `localStorage`를 통해 마지막 수행된 타임라인이 자동 복원됩니다.
* 완전히 새로운 업무를 처음부터 시작하시려면 타임라인 우측 상단의 **`[새 작업 시작]`** 버튼을 클릭하시면 됩니다.

### Q2. RabbitMQ 큐에 메시지가 '0'으로 남아있는데 정상인가요?
* **정상입니다.** RabbitMQ는 영구 보관 저장소가 아닌 비동기 파이프라인입니다.
* 스프링 부트의 `NoticeChunkConsumer`가 메시지가 유입되는 즉시 정상 소비(ACK)하고 큐에서 제거했기 때문에 0건으로 유지되는 것이 정상 동작입니다.

### Q3. 거부(Reject) 버튼을 누르면 어떻게 되나요?
* 관리자가 반려 사유를 입력하고 반려하면, 파이프라인이 즉시 중단되며 전자결재 상신 및 RabbitMQ 대량 발송이 일체 실행되지 않습니다.
* 게시판 상태는 `REJECTED`로 기록되어 안전하게 내부 통제가 유지됩니다.

### Q4. 8080 포트가 이미 사용 중이라는 에러가 납니다.
* AutoOps-MCP는 로컬 PC 환경의 포트 충돌을 방지하기 위해 **기본 포트를 `8090`으로 설정**해 두었습니다. 브라우저에서 `http://localhost:8090`으로 접속해 주십시오.
