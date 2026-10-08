-- ==========================================================
-- AutoOps-MCP Enterprise Database Initialization Script
-- Target: PostgreSQL 16
-- ==========================================================

-- 1. 가맹점 정산 테이블 (TB_MERCHANT_SETTLEMENT)
CREATE TABLE IF NOT EXISTS TB_MERCHANT_SETTLEMENT (
    SETTLEMENT_ID       VARCHAR(32) PRIMARY KEY,
    MERCHANT_NAME       VARCHAR(100) NOT NULL,
    SETTLEMENT_YM       VARCHAR(6) NOT NULL,
    STATUS              VARCHAR(20) NOT NULL, -- PENDING, COMPLETED, ERROR
    FAIL_REASON         VARCHAR(255),
    PENDING_AMOUNT      NUMERIC(15, 2) NOT NULL,
    CREATED_AT          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UPDATED_AT          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS IDX_SETTLEMENT_YM_STATUS ON TB_MERCHANT_SETTLEMENT (SETTLEMENT_YM, STATUS);

-- 2. 전자결재 상신 테이블 (TB_APPROVAL_DRAFT)
CREATE TABLE IF NOT EXISTS TB_APPROVAL_DRAFT (
    DRAFT_ID            VARCHAR(32) PRIMARY KEY,
    TITLE               VARCHAR(200) NOT NULL,
    CONTENT             TEXT NOT NULL,
    STATUS              VARCHAR(20) NOT NULL, -- DRAFTED, APPROVED, REJECTED
    CREATED_BY_AI       BOOLEAN DEFAULT TRUE,
    APPROVAL_COMMENT    VARCHAR(500),
    CREATED_AT          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    APPROVED_AT         TIMESTAMP
);

-- 3. AI 오케스트레이션 실행 감사 로그 (TB_AI_AUDIT_LOG)
CREATE TABLE IF NOT EXISTS TB_AI_AUDIT_LOG (
    AUDIT_ID            BIGSERIAL PRIMARY KEY,
    EXECUTION_ID        VARCHAR(64) NOT NULL,
    USER_ID             VARCHAR(50) NOT NULL,
    USER_PROMPT         TEXT NOT NULL,
    TOOL_NAME           VARCHAR(100) NOT NULL,
    TOOL_ARGUMENTS      JSONB NOT NULL,
    TOOL_RESULT         JSONB,
    IS_MUTATION         BOOLEAN NOT NULL,
    APPROVAL_STATUS     VARCHAR(20) NOT NULL, -- NONE, PENDING, APPROVED, REJECTED
    EXECUTION_TIME_MS   BIGINT,
    CREATED_AT          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS IDX_AUDIT_EXECUTION_ID ON TB_AI_AUDIT_LOG (EXECUTION_ID);

-- ==========================================================
-- Mock Data Insertion: 2026년 09월 기준 가맹점 정산 보류/오류 데이터 50건
-- ==========================================================
INSERT INTO TB_MERCHANT_SETTLEMENT (SETTLEMENT_ID, MERCHANT_NAME, SETTLEMENT_YM, STATUS, FAIL_REASON, PENDING_AMOUNT, CREATED_AT)
VALUES
('SETTLE-202609-001', '(주)한국상사', '202609', 'ERROR', '가맹점 정산 계좌 예금주명 불일치 (사명 변경 미반영)', 12500000.00, NOW()),
('SETTLE-202609-002', '넥스트커머스', '202609', 'ERROR', 'PG사 결제 수수료 단수 차액 발생 (15원 오차)', 8420000.00, NOW()),
('SETTLE-202609-003', '스타패션몰', '202609', 'ERROR', '국세청 사업자등록 상태 조회 결과 휴업 의심', 15300000.00, NOW()),
('SETTLE-202609-004', '메가일렉트로닉스', '202609', 'ERROR', '환불 트랜잭션 음수 정산 누락 발생', 4320000.00, NOW()),
('SETTLE-202609-005', '센트럴푸드마켓', '202609', 'ERROR', '가맹점 계좌 한도 초과로 인한 송금 거부', 9800000.00, NOW()),
('SETTLE-202609-006', '오렌지디지털랩', '202609', 'PENDING', '수동 정산 대기 상태 (담당자 검토 진행 중)', 3100000.00, NOW()),
('SETTLE-202609-007', '글로벌뷰티코리아', '202609', 'ERROR', '외화 결제 환율 기준시각 적용 불일치', 22100000.00, NOW()),
('SETTLE-202609-008', '한빛리빙하우스', '202609', 'ERROR', '배송 완료 플래그 미수신 주문 정산 포함 의심', 5400000.00, NOW()),
('SETTLE-202609-009', '블루웨이브스포츠', '202609', 'ERROR', 'PG사 매입 취소 건 미차감 오류', 6750000.00, NOW()),
('SETTLE-202609-010', '아이티솔루션즈', '202609', 'ERROR', '세금계산서 역발행 미승인에 따른 정산 보류', 18900000.00, NOW()),
('SETTLE-202609-011', '동아문구유통', '202609', 'ERROR', '정산 계좌 은행 코드 유효성 검증 실패', 1200000.00, NOW()),
('SETTLE-202609-012', '더조은인테리어', '202609', 'ERROR', '부가세 영세율 적용 증빙서류 누락', 7600000.00, NOW()),
('SETTLE-202609-013', '스마트오피스', '202609', 'ERROR', 'PG사 승인번호 매핑 누락 트랜잭션 존재', 3450000.00, NOW()),
('SETTLE-202609-014', '네이처헬스케어', '202609', 'ERROR', '가맹점 담보한도 초과에 따른 자동 지급 보류', 14200000.00, NOW()),
('SETTLE-202609-015', '에이스모바일', '202609', 'ERROR', '할부 수수료 분담 비율 산식 불일치', 8900000.00, NOW()),
('SETTLE-202609-016', '프라임북스', '202609', 'ERROR', '도서공제 대상 거래 부가세 비과세 분리 오류', 2300000.00, NOW()),
('SETTLE-202609-017', '골든키즈월드', '202609', 'PENDING', '정산 데이터 생성 중 배치 비정상 종료 후 재시도 대기', 4100000.00, NOW()),
('SETTLE-202609-018', '미래테크놀로지', '202609', 'ERROR', 'API 웹훅 타임아웃으로 인한 정산 승인 플래그 미수신', 11200000.00, NOW()),
('SETTLE-202609-019', '탑클래스어패럴', '202609', 'ERROR', '정산일자 주말/공휴일 캘린더 롤오버 계산 착오', 6300000.00, NOW()),
('SETTLE-202609-020', '그린팜농수산', '202609', 'ERROR', '신선식품 부분환불에 대한 적립금 환원 불일치', 1950000.00, NOW()),
('SETTLE-202609-021', '태양종합물류', '202609', 'ERROR', '운임비 보조금 정산 합산 산식 누락', 8700000.00, NOW()),
('SETTLE-202609-022', '클라우드소프트', '202609', 'ERROR', '정기결제 구독 취소건 일할계산 차액 검증 오류', 5120000.00, NOW()),
('SETTLE-202609-023', '비전오토모티브', '202609', 'ERROR', '차량 부품 특수 수수료율(2.1%) 미적용', 16500000.00, NOW()),
('SETTLE-202609-024', '하모니음향', '202609', 'ERROR', '가맹점 폐업 신고 접수에 따른 강제 출금 정지', 3800000.00, NOW()),
('SETTLE-202609-025', '모던가구공방', '202609', 'ERROR', '대형화물 배송비 예치금 차감 불일치', 9400000.00, NOW()),
('SETTLE-202609-026', '에코패키징', '202609', 'ERROR', '친환경 포재 지원금 보조 내역 불일치', 2800000.00, NOW()),
('SETTLE-202609-027', '다온베이커리', '202609', 'ERROR', '프랜차이즈 로열티 선차감 프로세스 실패', 4600000.00, NOW()),
('SETTLE-202609-028', '루미너스조명', '202609', 'ERROR', 'KC인증 검증 지연에 따른 정산 일시 정지', 7200000.00, NOW()),
('SETTLE-202609-029', '스카이항공여행', '202609', 'ERROR', '항공권 취소 수수료 VAT 계산 불일치', 31000000.00, NOW()),
('SETTLE-202609-030', '유니크악세사리', '202609', 'ERROR', '간이과세자 정산 산식 오류 (일반과세 세율 오적용)', 1500000.00, NOW()),
('SETTLE-202609-031', '바로퀵서비스', '202609', 'ERROR', '기사 정산 수수료 마이너스 잔액 발생', 3200000.00, NOW()),
('SETTLE-202609-032', '인사이트미디어', '202609', 'ERROR', '디지털 콘텐츠 저작권료 분배율 합계 초과(100.2%)', 8100000.00, NOW()),
('SETTLE-202609-033', '베스트반려용품', '202609', 'ERROR', '체험단 리워드 포인트 지급 정산 충돌', 2400000.00, NOW()),
('SETTLE-202609-034', '제일철강유통', '202609', 'ERROR', 'B2B 구매안전서비스(에스크로) 검수 미완료', 45000000.00, NOW()),
('SETTLE-202609-035', '오아시스워터', '202609', 'ERROR', '정수기 렌탈 계약 위약금 정산 상계 실패', 6200000.00, NOW()),
('SETTLE-202609-036', '실버케어몰', '202609', 'ERROR', '노인장기요양보험 공단부담금 매칭 불일치', 13400000.00, NOW()),
('SETTLE-202609-037', '트렌디슈즈', '202609', 'ERROR', '해외 직구 관부가세 대납 금액 정산 착오', 5800000.00, NOW()),
('SETTLE-202609-038', '케이바이오팜', '202609', 'ERROR', '의약외품 유통 라이선스 만료 경고로 보류', 27000000.00, NOW()),
('SETTLE-202609-039', '아쿠아마린수산', '202609', 'ERROR', '산지 직송 파손 보상금 자동 차감 오류', 3600000.00, NOW()),
('SETTLE-202609-040', '스마트캠퍼스', '202609', 'ERROR', '대학 교재 비과세 영수증 번호 매핑 누락', 4900000.00, NOW()),
('SETTLE-202609-041', '보람상조케어', '202609', 'ERROR', '할부 매입 수수료 정산 테이블 FK 무결성 위배', 17800000.00, NOW()),
('SETTLE-202609-042', '펀펀토이즈', '202609', 'ERROR', '어린이날 사전예약 프로모션 할인분 분담금 미정산', 8900000.00, NOW()),
('SETTLE-202609-043', '신선유통네트워크', '202609', 'ERROR', '콜드체인 배송 패널티 금액 차감 실패', 6700000.00, NOW()),
('SETTLE-202609-044', '탑클라우드호스팅', '202609', 'ERROR', '서버 호스팅 사용량 종량제 과금 정산 누락', 12100000.00, NOW()),
('SETTLE-202609-045', '하나로골프샵', '202609', 'ERROR', '중고 골프채 보상판매 거래 대금 상계 오류', 9300000.00, NOW()),
('SETTLE-202609-046', '청춘안경원', '202609', 'ERROR', '렌즈 맞춤 제작 취소 주문의 공임비 미반영', 1800000.00, NOW()),
('SETTLE-202609-047', '월드와이드악기', '202609', 'ERROR', '수입 통관 부대비용 배부 금액 검증 불일치', 14500000.00, NOW()),
('SETTLE-202609-048', '파워헬스짐', '202609', 'ERROR', 'PT 수강권 다회차 환불 계산식 검증 에러', 7400000.00, NOW()),
('SETTLE-202609-049', '글로리아웨딩', '202609', 'ERROR', '예식장 계약금 에스크로 해제 승인 누락', 21000000.00, NOW()),
('SETTLE-202609-050', '대박종합유통', '202609', 'ERROR', '대량 발주 B2B 여신 한도 재산정 미반영', 38000000.00, NOW())
ON CONFLICT (SETTLEMENT_ID) DO NOTHING;
