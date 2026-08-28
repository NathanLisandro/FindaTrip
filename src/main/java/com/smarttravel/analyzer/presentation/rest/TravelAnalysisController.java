package com.smarttravel.analyzer.presentation.rest;

import com.smarttravel.analyzer.application.dto.*;
import com.smarttravel.analyzer.application.usecase.*;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/travel-analysis")
public class TravelAnalysisController {
    private final SearchBestValuePackagesUseCase searchPackages;
    private final GetPriceTrendHistoryUseCase priceTrend;
    public TravelAnalysisController(SearchBestValuePackagesUseCase searchPackages, GetPriceTrendHistoryUseCase priceTrend) { this.searchPackages = searchPackages; this.priceTrend = priceTrend; }
    @PostMapping("/packages") public List<PackageResponseRecord> search(@Valid @RequestBody SearchCriteriaRequest request) { return searchPackages.search(request); }
    @GetMapping("/trends/{marketKey}") public TrendDataDTO trend(@PathVariable String marketKey, @RequestParam BigDecimal currentPrice) { return priceTrend.trend(marketKey, currentPrice); }
}
