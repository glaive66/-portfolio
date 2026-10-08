package com.autoops.domain.settlement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MerchantSettlementRepository extends JpaRepository<MerchantSettlement, String> {

    List<MerchantSettlement> findBySettlementYmAndStatus(String settlementYm, SettlementStatus status);

    List<MerchantSettlement> findBySettlementYm(String settlementYm);

    long countBySettlementYmAndStatus(String settlementYm, SettlementStatus status);
}
