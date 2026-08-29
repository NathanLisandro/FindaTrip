package com.smarttravel.analyzer.infrastructure.adapter.external;

import com.smarttravel.analyzer.domain.model.history.PriceHistoryPoint;
import com.smarttravel.analyzer.domain.model.shared.Money;
import com.smarttravel.analyzer.domain.repository.PriceHistoryStorePort;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryPriceHistoryStoreAdapter implements PriceHistoryStorePort {
    @Override public List<PriceHistoryPoint> findByMarketKey(String marketKey) {
        var brl = Money.BRL;
        return List.of(
            new PriceHistoryPoint(marketKey, LocalDate.now().minusDays(30), new Money(java.math.BigDecimal.valueOf(1500), brl)),
            new PriceHistoryPoint(marketKey, LocalDate.now().minusDays(20), new Money(java.math.BigDecimal.valueOf(1620), brl)),
            new PriceHistoryPoint(marketKey, LocalDate.now().minusDays(10), new Money(java.math.BigDecimal.valueOf(1710), brl)),
            new PriceHistoryPoint(marketKey, LocalDate.now().minusDays(5), new Money(java.math.BigDecimal.valueOf(1580), brl)));
    }
}
