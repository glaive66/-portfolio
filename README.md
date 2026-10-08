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

**AutoOps-MCP**는 백오피스 운영자가 자연어로 업무를 지시하면, AI(LLM)가 표준 **MCP(Model Context Protocol)** 규격을 통해 백엔드 시스템(JPA/MyBatis RDBMS, RabbitMQ 분산 큐, 전자결재)을 자율적으로 조합·실행하는 **차세대 엔터프라이즈 운영 오케스트레이션 플랫폼**입니다.

기존 단순 프롬프트 래퍼(Wrapper)나 챗봇의 한계를 극복하고, **14년 차 시니어 백엔드 아키텍처** 관점에서 **Human-in-the-Loop(승인 게이트웨이)**, **Saga 패턴(보상 트랜잭션)**, **불변 감사 로그(Audit Trail)**를 결합하여 실제 엔터프라이즈 환경에서 안전하게 배포 가능한 아키텍처를 구현했습니다.

---

## 🏛️ 2. 시스템 아키텍처 (Architecture Overview)

```mermaid
flowchart TD
    User(["운영 관리자"]) --> UI["운영 콘솔 UI (Thymeleaf + SSE)"]
    UI --> Controller["Orchestration Controller"]
    Controller --> Agent["Spring AI Agent (Google Gemini 2.0 Flash / Multi-LLM)"]
    
    subgraph MCP Ecosystem
        Agent <-->|MCP Protocol (Function Calling)| MCP_Registry["MCP Tool Registry"]
        MCP_Registry --> T1["Tool: 가맹점 정산 오류 조회 (READ)"]
        MCP_Registry --> T2["Tool: 맞춤 안내문 템플릿 생성 (READ)"]
        MCP_Registry --> Gate{"Human-in-the-Loop Gateway<br/>(위험 작업 승인 대기)"}
        Gate -->|SUSPEND: 승인 요청 카드| UI
        UI -->|APPROVE: 관리자 승인 확인| Gate
        Gate --> Saga["Saga Orchestrator"]
        Saga --> T3["Tool: 전자결재 기안 자동 상신"]
        Saga --> T4["Tool: RabbitMQ 10건 단위 Chunk 분할 발행"]
    end

    T3 --> DB[("PostgreSQL 16")]
    T4 --> RabbitMQ[("RabbitMQ Queue")]
    Agent --> Audit["불변 감사 로그 (TB_AI_AUDIT_LOG)"]
```

---

## 🎯 3. 4대 핵심 엔지니어링 차별화 포인트

### ① Spring AI 기반 표준 MCP Tool 등록 및 자율 체이닝
* 백엔드 비즈니스 로직을 Spring AI의 표준 `@Tool` 규격으로 노출.
* Java 17 `record` 및 `@JsonPropertyDescription` 기반으로 엄격한 JSON Schema를 정의하여 LLM 환각(Hallucination) 방지.
* `findSettlementErrors` ➡️ `generateNoticeTemplate` ➡️ `createApprovalDraft` ➡️ `enqueueBulkNotice`로 이어지는 자율 체이닝 파이프라인.

### ② Human-in-the-Loop (승인 게이트웨이 및 세션 중단/재개)
* **조회성 도구(`READ`)**: AI가 자율적으로 연속 실행.
* **부수 효과 유발 도구(`MUTATION`)**: 대량 발송 및 전자결재 전 세션을 일시 중지(`SUSPEND`)하고, 웹 콘솔에 승인 카드 출력.
* 관리자의 명시적 승인(`APPROVE`) 클릭 시 메모리 세션을 복원하여 잔여 파이프라인 재개(`RESUME`).

### ③ RabbitMQ Chunk 분할 메시징 & Saga 보상 트랜잭션
* 대량 발송 시 WAS 스레드 고갈 및 브로커 부하를 방지하기 위해 **10건 단위 Chunk 분할 메시지 발행**.
* 다단계 작업(결재 기안 등록 ➡️ RabbitMQ 청크 발행) 중 장애 발생 시, **선행 결재 기안을 `REJECTED`로 자동 롤백하는 Saga 보상 트랜잭션** 수행.

### ④ 불변 감사 추적성 (TB_AI_AUDIT_LOG)
* AI의 의사결정 블랙박스 해소를 위해 실행 ID, 프롬프트, 도구명, 입출력 인자(JSONB), 실행 시간(ms)을 전수 영구 기록.

---

## 🛠️ 4. 기술 스택 (Tech Stack)

| 구분 | 기술 스택 | 설명 |
| :--- | :--- | :--- |
| **Language** | **Java 17** | 불변 Record, Pattern Matching, Stream API |
| **Framework** | **Spring Boot 3.4.3** | 최신 엔터프라이즈 프레임워크 표준 |
| **AI Engine** | **Spring AI 1.0.0-M6** | Google Gemini 2.0 Flash (Free Tier) / OpenAI / Claude 무중단 호환 |
| **Database** | **PostgreSQL 16** | 안정적인 RDBMS 트랜잭션 및 JSONB 감사로그 |
| **Persistence** | **Spring Data JPA & MyBatis** | 도메인 트랜잭션(JPA)과 대량 통계 집계(MyBatis) 분리 |
| **Message Broker**| **RabbitMQ 3.13-management**| Chunk 분할 비동기 병렬 메시징 |
| **Frontend/UI** | **Thymeleaf + Tailwind CSS + SSE** | 실시간 AI 사고 타임라인 및 관리자 승인 콘솔 |
| **DevOps** | **Docker Compose** | 로컬 인프라(DB, MQ) 원클릭 컨테이너화 |

---

## 🚦 5. 빠른 시작 가이드 (Quick Start)

### 1) 사전 준비
* JDK 17 이상 설치
* Docker Desktop 실행

### 2) 인프라 원클릭 기동 (PostgreSQL + RabbitMQ)
```bash
docker compose -f docker/docker-compose.yml up -d
```
* **PostgreSQL**: `localhost:5432` (초기 Mock 데이터 50건 자동 적재)
* **RabbitMQ 콘솔**: `http://localhost:15672` (ID: `guest`, PW: `guest`)

### 3) 단위 테스트 전체 검증
```bash
./gradlew test
```

### 4) 애플리케이션 실행
```bash
# Google Gemini 무료 API 키를 환경변수로 지정 (선택사항, 미지정 시에도 모의 오케스트레이션 동작)
export GEMINI_API_KEY="your-gemini-free-api-key"

./gradlew bootRun
```
* **운영 대시보드 접속**: `http://localhost:8080`

---

## 📂 6. 프로젝트 디렉토리 구조

```text
autoops-mcp/
├── docker/
│   ├── docker-compose.yml              # PostgreSQL 16 & RabbitMQ 3-management
│   └── postgres/init.sql               # DDL 및 가상 정산 오류 Mock 50건 적재
├── src/main/java/com/autoops/
│   ├── domain/                         # 핵심 비즈니스 엔티티 (JPA & MyBatis)
│   │   ├── settlement/                 # 가맹점 정산 보류 도메인
│   │   ├── approval/                   # 전자결재 상신 도메인
│   │   └── audit/                      # TB_AI_AUDIT_LOG 불변 감사 로그
│   ├── mcp/                            # AI & MCP 오케스트레이션 코어
│   │   ├── tool/                       # 4대 표준 MCP Tool (@Tool)
│   │   ├── hitl/                       # Human-in-the-Loop 승인 게이트웨이
│   │   ├── saga/                       # Saga 패턴 보상 트랜잭션 오케스트레이터
│   │   └── service/                    # Spring AI Agent 실행 서비스
│   ├── infrastructure/rabbitmq/        # Chunk 분할 Producer & Consumer
│   └── web/                            # Thymeleaf 컨트롤러 & SSE 실시간 스트리밍
└── src/test/                           # 격리된 슬라이스 단위 테스트 스위트
```
