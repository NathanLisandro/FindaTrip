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
            links(travelPackage, criteria));
    }

    /**
     * O link da hospedagem aponta para O ANUNCIO, no site de onde ele veio.
     * Antes todo card oferecia "Reservar no Booking.com" mesmo quando o anuncio era do Airbnb,
     * e caia na pagina de busca, obrigando o usuario a procurar de novo o que ja tinhamos achado.
     * Para o voo nao existe link direto: a busca do parceiro e o melhor honesto.
     */
    private List<BookingLinkDTO> links(TravelPackage travelPackage, SearchCriteria criteria) {
        var links = new java.util.ArrayList<BookingLinkDTO>();
        var lodging = travelPackage.lodging().offer();
        if (lodging.hasLink()) {
            links.add(new BookingLinkDTO("Ver no " + lodging.source(), lodging.url()));
        }
        linkBuilders.stream()
            .filter(builder -> builder.partnerName().equals("Google Flights"))
            .forEach(builder -> links.add(new BookingLinkDTO("Ver voos no Google", builder.searchUrl(criteria).toString())));
        return List.copyOf(links);
    }

    public SourceStatusDTO toDto(SourceStatus source) {
        return new SourceStatusDTO(source.source(), source.health(), source.offers(), source.message(), source.demo());
    }
}
