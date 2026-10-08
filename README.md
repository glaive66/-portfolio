# 🚀 AutoOps-MCP: 자율형 백엔드 운영 오케스트레이션 플랫폼
> **Autonomous Backend Operations Orchestrator powered by Spring Boot 3.4 & Spring AI (MCP Standard)**

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0.0--M6-blue.svg)](https://spring.io/projects/spring-ai)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3.13-orange.svg)](https://www.rabbitmq.com/)
[![License](https://img.shields.io/badge/License-Apache%202.0-lightgrey.svg)]()

---

## 📌 1. 프로젝트 개요 (Executive Summary)

**AutoOps-MCP**는 운영자가 자연어로 업무를 지시하면, AI 에이전트가 표준 **MCP(Model Context Protocol)** 규격을 통해 백엔드 시스템(JPA RDBMS, RabbitMQ 분산 큐, 전자결재 시스템)을 자율적으로 조합·실행하는 **차세대 엔터프라이즈 백엔드 운영 플랫폼**입니다.

기존 단순 챗봇의 한계인 줄글 텍스트 답변을 극복하고, **14년 차 시니어 백엔드 아키텍처** 관점에서 **Human-in-the-Loop(승인 게이트웨이)**, **Saga 패턴(보상 트랜잭션)**, **동적 의도 분류(Intent Classification)**, **1-Click 엑셀 다운로드/보고서 생성**을 결합하여 실무 운영진이 즉시 도입 가능한 수준으로 완성되었습니다.

---

## 🛠️ 2. 개발 스펙 및 기술 구성 (Technical Specifications)

| 레이어 | 기술 스택 | 버전 및 상세 스펙 | 도입 이유 및 역할 |
| :--- | :--- | :--- | :--- |
| **Language** | **Java** | **17 LTS** | 불변 `record`, Pattern Matching, Stream API를 통한 높은 타입 안정성 |
| **Framework** | **Spring Boot** | **3.4.3** | 가상 스레드 친화적 최신 엔터프라이즈 백엔드 아키텍처 |
| **AI Engine** | **Spring AI** | **1.0.0-M6** | Google Gemini 2.0 Flash / OpenAI / Claude 무중단 멀티 LLM 표준 바인딩 |
| **Protocol** | **MCP Core** | **Model Context Protocol** | 함수 호출(Function Calling) 기반의 백엔드 도구 표준화 및 메타데이터 정의 |
| **Database** | **PostgreSQL** | **16.15 (Alpine)** | 안정적인 ACID 트랜잭션 및 비정형 감사 로그 저장을 위한 JSONB 네이티브 지원 |
| **ORM / Data**| **Spring Data JPA**| **Hibernate 6.6** | 도메인 엔티티 생명주기 관리 및 자율 오케스트레이션 이력 영속화 |
| **Messaging** | **RabbitMQ** | **3.13-management** | 대량 발송 시 WAS 스레드 고갈 방지를 위한 10건 단위 청크(Chunk) 비동기 큐잉 |
| **Frontend** | **Thymeleaf + SSE** | **Tailwind CSS** | 단방향 SSE 실시간 스트리밍 타임라인, 인라인 데이터 그리드, HITL 승인 뷰 |
| **Container** | **Docker Compose**| **v2** | PostgreSQL, RabbitMQ 로컬 인프라 원클릭 자동 격리 구동 |

---

## 🏛️ 3. 시스템 아키텍처 (Architecture Overview)

```mermaid
flowchart TD
    User(["운영 관리자"]) --> UI["운영 콘솔 UI (Thymeleaf + Tailwind CSS)"]
    UI --> Controller["Ops Web & API Controller (:8090)"]
    Controller --> Coordinator["Interactive Orchestration Coordinator"]
    
    subgraph AI Engine & Intent Classifier
        Coordinator --> Classifier{"자연어 의도 분석<br/>(Intent Classification)"}
        Classifier -->|READ: 단순 조회/합산| ReadFlow["자율 실행 & 집계 파이프라인"]
        Classifier -->|MUTATION: 발송/결재| MutationFlow["위험 작업 보호 파이프라인"]
    end

    subgraph MCP Tool Ecosystem
        ReadFlow --> T1["Tool: 정산 오류 조회 (findSettlementErrors)"]
        MutationFlow --> T1
        MutationFlow --> T2["Tool: 소명 안내문 템플릿 생성 (generateNoticeTemplate)"]
        MutationFlow --> Gate{"Human-in-the-Loop Gateway<br/>(작업 일시 중지)"}
        Gate -->|SUSPENDED| UI
        UI -->|APPROVE (관리자 승인)| Gate
        Gate --> Saga["Saga 분산 트랜잭션 오케스트레이터"]
        Saga --> T3["Tool: 전자결재 기안 상신 (createApprovalDraft)"]
        Saga --> T4["Tool: 대량 청크 분할 발행 (enqueueBulkNotice)"]
    end

    ReadFlow --> Export["1-Click 엑셀 다운로드 / 표 복사 / A4 보고서 모달"]
    T3 --> DB[("PostgreSQL 16")]
    T4 --> MQ[("RabbitMQ 10건 Chunk Queue")]
    MQ --> Worker["비동기 Worker Consumer"]
```

---

## 🔌 4. 무한한 업무 확장 가능성 (Domain-Agnostic Extensibility)

AutoOps-MCP는 특정 정산 업무에 종속되지 않는 **플러그인 도구 확장형 아키텍처**로 설계되어 있습니다.  
회사의 새로운 업무(예: **배송 지연 보상**, **FDS 이상 결제 차단**, **세금계산서 일괄 발행**, **휴면 계정 안내** 등)를 추가할 때, **UI 화면이나 인프라를 일체 고칠 필요 없이 단 3단계만 진행**하면 즉시 시스템에 편입됩니다.

```
[1. 회사 DB 테이블 생성 및 실데이터 적재]
                   ▼
[2. Spring Data JPA Entity & Repository 선언]
                   ▼
[3. 비즈니스 로직 메서드에 @Tool 어노테이션 부여] ──▶ 확장 완료!
```

### 업무 확장 시 계층별 영향도
| 계층 | 수정 필요 여부 | 설명 |
| :--- | :---: | :--- |
| **데이터 계층** | **신규 추가** | 새로운 업무용 DB 테이블 및 Spring Data JPA 엔티티/리포지토리 작성 |
| **비즈니스 도구** | **신규 추가** | `@Component` 클래스에 `@Tool(description = "한글 설명")` 메서드 정의 |
| **AI 오케스트레이터** | **수정 불필요** | Spring AI가 등록된 `@Tool`의 메타데이터를 자동 수집하여 자연어 의도에 맞춰 스스로 바인딩 |
| **운영 콘솔 UI (웹)** | **수정 불필요 (0%)** | 실시간 타임라인, 승인 게이트웨이, 인라인 그리드가 공통 JSON 규격으로 자동 렌더링 |
| **실무 데이터 도구** | **수정 불필요 (0%)** | **1-Click 엑셀(CSV) 다운로드, 엑셀용 표 복사, PDF 보고서 인쇄 모달이 100% 자동 재사용** |

---

## 🎯 5. 핵심 엔지니어링 차별화 포인트

### ① 동적 의도 분석 (Dynamic Intent Classification)
* **`READ / AGGREGATION` (조회, 금액 합산, 통계)**:
  * 예: *"9월 정산오류중 최근 10건 금액 합해줘"*, *"오류 가맹점 목록 보여줘"*
  * 부수 효과(Side-effect)가 없으므로 **불필요한 승인창 없이 즉시 계산 리포트 출력 및 완료(`COMPLETED`)**.
* **`MUTATION / DISPATCH` (알림 발송, 전자결재 상신, 데이터 변경)**:
  * 예: *"소명 안내문 작성 후 결재 올려줘"*, *"일괄 알림 발송해줘"*
  * 실제 외부로 메시지가 나가거나 결재가 상신되는 위험 작업이므로 **HITL 승인 게이트웨이(`SUSPENDED`)로 안전하게 인터셉트**.

### ② 실무 중심 엔터프라이즈 UX (Beyond Chat UI)
* **인라인 미니 데이터 테이블**: 챗봇 특유의 읽기 힘든 줄글 답변을 제거하고, 결과 카드 안에 정돈된 격자 테이블 렌더링.
* **📥 1-Click 엑셀(CSV) 다운로드**: `UTF-8 BOM`이 포함되어 엑셀에서 한글이 전혀 깨지지 않는 표준 CSV 즉시 생성.
* **📋 엑셀/스프레드시트용 표 서식 복사**: 원클릭으로 탭 구분자(TSV) 형태로 복사되어 엑셀에 `Ctrl + V` 시 셀 단위로 즉시 정렬.
* **📄 공식 비즈니스 보고서 (A4 인쇄/PDF)**: 결재란, 문서번호, 요약 통계, 상세 명세표, 종합 소견이 포함된 공식 인쇄 팝업 지원.

### ③ Human-in-the-Loop & Saga 분산 트랜잭션
* **Human-in-the-Loop 승인 게이트웨이**: 위험 작업 감지 시 UUID 기반 세션 토큰을 생성하고 작업을 안전하게 일시 중지, 관리자가 검토 후 `Approve` 또는 `Reject` 결정.
* **Saga 보상 트랜잭션**: 전자결재 상신 ➡️ RabbitMQ 청크 발행 단계 중 브로커 장애 발생 시, 선행 기안을 `REJECTED`로 즉시 보상 롤백.
* **RabbitMQ Chunk 분할 메시징**: 대량 건수를 10건 단위 청크로 쪼개어 비동기 브로커로 분할 스트리밍 발행 (WAS 메모리 고갈 방지).

### ④ AI 작업 내역 게시판 (Task History Board) & 불변 감사 추적성
* **TB_OPS_TASK_HISTORY**: 과거 실행된 모든 자연어 지시, 분석 대상 규모, 결재 품의 링크, MQ 청크 수, 승인자, 처리 상태를 영구 보존하고 웹 게시판에서 즉시 조회.
* **TB_AI_AUDIT_LOG**: AI가 실행한 모든 도구의 입력 인자(JSONB), 실행 결과, 소요 시간(ms)을 전수 영구 기록하여 AI 의사결정의 블랙박스 해소.

---

## 🚦 6. 빠른 시작 가이드 (Quick Start)

### 1) 인프라 기동 (Docker Compose)
```cmd
docker compose -f docker/docker-compose.yml up -d
```
* **PostgreSQL 16**: `localhost:5432`
* **RabbitMQ 관리 콘솔**: `http://localhost:15672` (계정: `guest` / `guest`)

### 2) 백엔드 기동
```cmd
gradlew bootRun
```
* **운영 웹 콘솔 접속**: **`http://localhost:8090`**

### 3) 검증된 테스트 시나리오
1. **단순 집계 테스트**:
   > 입력: `"9월 정산오류중 최근 10건 금액 합해줘"`  
   > 결과: 승인 없이 즉시 인라인 표 렌더링 ➡️ **`[📥 엑셀 다운로드]`**, **`[📋 표 복사]`**, **`[📄 보고서 인쇄]`** 확인
2. **풀 파이프라인 (대량 발송 & 결재 상신) 테스트**:
   > 입력: `"2026년 9월 정산 오류 가맹점들을 조회하고 소명 안내문 작성 후 결재 올려줘"`  
   > 결과: HITL 승인 게이트웨이 일시 중단 ➡️ 관리자 **`[승인]`** ➡️ RabbitMQ 5개 청크 순차 소비(20%~100%) ➡️ 작업 내역 게시판 확인
