package com.smarttravel.analyzer.infrastructure.adapter.demo;

import com.smarttravel.analyzer.domain.model.lodging.*;
import com.smarttravel.analyzer.domain.model.shared.*;
import com.smarttravel.analyzer.domain.repository.LodgingProviderPort;
import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class DemoLodgingProvider implements LodgingProviderPort {

    private static final List<String> NAMES = List.of("Pousada Maré Alta", "Hotel Centro Histórico",
        "Apartamento Beira-Mar", "Hostel do Porto", "Casa de Temporada Jardim", "Apart-Hotel Executivo");

    private static final List<String> NEIGHBORHOODS = List.of("Boa Viagem", "Centro",
        "Boa Viagem", "Recife Antigo", "Espinheiro", "Centro");

    @Override public boolean isDemo() { return true; }

    @Override public List<LodgingOffer> searchLodging(SearchCriteria criteria) {
        var random = new Random(criteria.hashCode());
        int nights = nights(criteria);
        var offers = new ArrayList<LodgingOffer>();
        for (int index = 0; index < NAMES.size(); index++) {
            var amenities = EnumSet.noneOf(Amenity.class);
            if (index % 2 == 0) amenities.add(Amenity.BREAKFAST_INCLUDED);
            if (index % 3 != 0) amenities.add(Amenity.FREE_FLEXIBLE_CANCELLATION);
            if (index % 3 == 0) amenities.add(Amenity.WALKABLE_ATTRACTIONS);
            offers.add(new LodgingOffer("demo-lo-" + index, NAMES.get(index), NEIGHBORHOODS.get(index), money((110 + random.nextInt(320)) * nights), nights,
                money(random.nextInt(60)), money(random.nextInt(35)), money(index % 4 == 0 ? 45 : 0),
                new HotelRating(round(7.2 + random.nextDouble() * 2.6), 40 + random.nextInt(2400)),
                Set.copyOf(amenities), round(random.nextDouble() * 4)));
        }
        return List.copyOf(offers);
    }

    static int nights(SearchCriteria criteria) {
        return Math.max(1, (int) ChronoUnit.DAYS.between(criteria.departureDate(), criteria.returnDate()));
    }

    private static double round(double value) { return Math.round(value * 10) / 10.0; }

    private static Money money(int amount) { return new Money(BigDecimal.valueOf(amount), Money.BRL); }
}
