package com.autoops.domain.settlement;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MerchantSettlementMapper {

    SettlementSummaryDto selectSettlementSummary(@Param("settlementYm") String settlementYm);
}
