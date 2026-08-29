package com.smarttravel.analyzer.presentation.rest;

import com.smarttravel.analyzer.application.dto.TrendDataDTO;
import com.smarttravel.analyzer.application.usecase.GetPriceTrendHistoryUseCase;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/travel-analysis")
public class TravelAnalysisController {
    private final GetPriceTrendHistoryUseCase priceTrend;
    public TravelAnalysisController(GetPriceTrendHistoryUseCase priceTrend) { this.priceTrend = priceTrend; }
    @GetMapping("/trends/{marketKey}") public TrendDataDTO trend(@PathVariable String marketKey, @RequestParam BigDecimal currentPrice) { return priceTrend.trend(marketKey, currentPrice); }
}
