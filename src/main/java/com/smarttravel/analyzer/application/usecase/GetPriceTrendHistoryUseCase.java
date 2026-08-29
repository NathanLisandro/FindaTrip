package com.smarttravel.analyzer.application.usecase;

import com.smarttravel.analyzer.application.dto.TrendDataDTO;
import com.smarttravel.analyzer.domain.model.shared.Money;
import com.smarttravel.analyzer.domain.repository.PriceHistoryStorePort;
import com.smarttravel.analyzer.domain.service.PriceTrendDomainService;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
public class GetPriceTrendHistoryUseCase {
    private final PriceHistoryStorePort store; private final PriceTrendDomainService trendService;
    public GetPriceTrendHistoryUseCase(PriceHistoryStorePort store, PriceTrendDomainService trendService) { this.store = store; this.trendService = trendService; }
    public TrendDataDTO trend(String marketKey, BigDecimal currentPrice) {
        var trend = trendService.calculateTrend(marketKey, store.findByMarketKey(marketKey));
        var rating = trend.classify(new Money(currentPrice, Money.BRL));
        return new TrendDataDTO(marketKey, trend.mean().amount(), trend.median().amount(), trend.standardDeviation(), trend.sampleSize(), rating);
    }
}
