package com.smarttravel.analyzer.application.mapper;

import com.smarttravel.analyzer.application.dto.*;
import com.smarttravel.analyzer.domain.link.DeepLinkBuilder;
import com.smarttravel.analyzer.domain.model.packagebundle.TravelPackage;
import com.smarttravel.analyzer.domain.model.search.SourceStatus;
import com.smarttravel.analyzer.domain.model.shared.SearchCriteria;
import com.smarttravel.analyzer.domain.service.PackageExplanationDomainService;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DomainToDtoMapper {

    private final PackageExplanationDomainService explanations;
    private final List<DeepLinkBuilder> linkBuilders;

    public DomainToDtoMapper(PackageExplanationDomainService explanations, List<DeepLinkBuilder> linkBuilders) {
        this.explanations = explanations;
        this.linkBuilders = List.copyOf(linkBuilders);
    }

    public PackageDTO toDto(TravelPackage travelPackage, List<TravelPackage> candidates, SearchCriteria criteria) {
        var lodging = travelPackage.lodging().offer();
        return new PackageDTO(
            travelPackage.id(),
            travelPackage.recommendationType(),
            travelPackage.advertisedPrice().amount(),
            travelPackage.totalPrice().amount(),
            travelPackage.hiddenCosts().amount(),
            travelPackage.totalPrice().currency().getCurrencyCode(),
            travelPackage.valueScore().value(),
            travelPackage.qualityScore().value(),
            travelPackage.convenienceScore().value(),
            travelPackage.costAdjustments().stream().map(explanations::translateAdjustment).toList(),
            explanations.explain(travelPackage, candidates),
            travelPackage.flight().offer().airline().name(),
            travelPackage.flight().offer().stops(),
            lodging.name(),
            lodging.neighborhood(),
            lodging.rating().average(),
            lodging.rating().reviewCount(),
            travelPackage.hasCar() ? travelPackage.car().offer().supplier() : null,
            travelPackage.hasCar() ? travelPackage.car().offer().category().name() : null,
            linkBuilders.stream().map(builder -> new BookingLinkDTO(builder.partnerName(), builder.searchUrl(criteria).toString())).toList());
    }

    public SourceStatusDTO toDto(SourceStatus source) {
        return new SourceStatusDTO(source.source(), source.health(), source.offers(), source.message(), source.demo());
    }
}
