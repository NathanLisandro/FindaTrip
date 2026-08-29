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
            logoDaCompanhia(travelPackage.flight().offer().airline().iataCode()),
            horaTexto(travelPackage.flight().offer().departureTime()),
            horaTexto(travelPackage.flight().offer().arrivalTime()),
            duracaoTexto(travelPackage.flight().offer().totalDuration()),
            lodging.name(),
            lodging.neighborhood(),
            lodging.rating().average(),
            lodging.rating().reviewCount(),
            lodging.source(),
            imagem(lodging),
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
    /** Logo pelo codigo IATA, servido pelo mesmo proxy das fotos. */
    private static String logoDaCompanhia(String iata) {
        if (iata == null || iata.isBlank() || "--".equals(iata)) return null;
        return "/api/img?ref=" + java.net.URLEncoder.encode(
            "https://images.kiwi.com/airlines/64/" + iata + ".png", java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String horaTexto(java.time.LocalTime hora) {
        return hora == null ? null : hora.toString();
    }

    /** "8h 40min" em vez de PT8H40M, que nao diz nada para quem le. */
    private static String duracaoTexto(java.time.Duration duracao) {
        if (duracao == null) return null;
        long horas = duracao.toHours();
        long minutos = duracao.toMinutesPart();
        return minutos == 0 ? horas + "h" : horas + "h " + minutos + "min";
    }

    /** A foto passa pelo proxy do backend: hotlink no CDN deles e bloqueado por referer. */
    private static String imagem(com.smarttravel.analyzer.domain.model.lodging.LodgingOffer lodging) {
        if (!lodging.hasImage()) return null;
        return "/api/img?ref=" + java.net.URLEncoder.encode(lodging.imageUrl(), java.nio.charset.StandardCharsets.UTF_8);
    }

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
