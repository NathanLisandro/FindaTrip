package com.smarttravel.analyzer.application.usecase;

import com.smarttravel.analyzer.application.dto.*;
import com.smarttravel.analyzer.application.mapper.DomainToDtoMapper;
import com.smarttravel.analyzer.domain.model.packagebundle.TravelPackage;
import com.smarttravel.analyzer.domain.model.shared.*;
import com.smarttravel.analyzer.domain.service.PackageBundlerDomainService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SearchBestValuePackagesUseCase {
    private final DomainToDtoMapper mapper;
    private final PackageBundlerDomainService bundler;
    public SearchBestValuePackagesUseCase(DomainToDtoMapper mapper, PackageBundlerDomainService bundler) { this.mapper = mapper; this.bundler = bundler; }
    public List<PackageResponseRecord> search(SearchCriteriaRequest criteria) {
        var usd = java.util.Currency.getInstance("USD");
        var candidates = List.of(
            new TravelPackage("overall-value", new Money(java.math.BigDecimal.valueOf(1850), usd), new Score(91), new Score(86), new Score(88), null),
            new TravelPackage("smart-budget", new Money(java.math.BigDecimal.valueOf(1390), usd), new Score(84), new Score(74), new Score(71), null),
            new TravelPackage("max-comfort", new Money(java.math.BigDecimal.valueOf(2440), usd), new Score(89), new Score(97), new Score(96), null));
        return bundler.topRecommendations(candidates).stream().map(mapper::toResponse).toList();
    }
}
