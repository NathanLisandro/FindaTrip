package com.smarttravel.analyzer.domain.model.lodging;

import com.smarttravel.analyzer.domain.model.shared.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

/**
 * Guarda o total da estadia, nao a diaria.
 * Os sites anunciam o total ("R$ 2.766 para 7 noites") e nem todo total e divisivel por
 * noite em centavos: guardar a diaria e multiplicar devolvia R$ 2.765,98 onde o site diz
 * R$ 2.766,00. Num comparador de precos, essa diferenca corroi a confianca do usuario.
 * A diaria continua disponivel, derivada, para exibicao.
 */
public record LodgingOffer(String id, String name, String neighborhood, Money stayTotal, int nights,
                           Money serviceFees, Money cityTaxes, Money resortFees, HotelRating rating,
                           Set<Amenity> amenities, double distanceToAttractionsKm) {

    public static final String UNKNOWN_NEIGHBORHOOD = "Não informado";

    public LodgingOffer {
        amenities = Set.copyOf(amenities);
        neighborhood = (neighborhood == null || neighborhood.isBlank()) ? UNKNOWN_NEIGHBORHOOD : neighborhood.trim();
    }

    /** Derivada, so para exibir. O valor de verdade e o total. */
    public Money nightlyRate() {
        return new Money(stayTotal.amount().divide(BigDecimal.valueOf(Math.max(1, nights)), 2, RoundingMode.HALF_UP),
            stayTotal.currency());
    }

    public NormalizedPrice normalize() {
        return new NormalizedPrice(stayTotal, stayTotal.add(serviceFees).add(cityTaxes).add(resortFees),
            java.util.List.of("service_fees", "city_taxes", "resort_fees"));
    }

    public boolean has(Amenity amenity) { return amenities.contains(amenity); }
}
