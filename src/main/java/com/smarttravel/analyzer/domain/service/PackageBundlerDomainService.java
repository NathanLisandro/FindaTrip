package com.smarttravel.analyzer.domain.service;

import com.smarttravel.analyzer.domain.model.packagebundle.*;
import java.util.Comparator;
import java.util.List;

public class PackageBundlerDomainService {
    public List<TravelPackage> topRecommendations(List<TravelPackage> candidates) {
        var best = candidates.stream().max(Comparator.comparingDouble(p -> p.valueScore().value())).orElseThrow();
        var budget = candidates.stream().filter(TravelPackage::meetsSmartBudgetFloor).min(Comparator.comparing(p -> p.totalPrice())).orElse(best);
        var comfort = candidates.stream().max(Comparator.comparingDouble(p -> p.qualityScore().value() + p.convenienceScore().value())).orElse(best);
        return List.of(tag(best, PackageBundleType.BEST_VALUE_OVERALL), tag(budget, PackageBundleType.SMART_BUDGET), tag(comfort, PackageBundleType.MAX_COMFORT));
    }
    private TravelPackage tag(TravelPackage p, PackageBundleType type) { return p.withRecommendationType(type); }
}
