package com.smarttravel.analyzer.infrastructure.adapter.demo;

import com.smarttravel.analyzer.domain.model.carrental.*;
import com.smarttravel.analyzer.domain.model.shared.*;
import com.smarttravel.analyzer.domain.repository.CarRentalProviderPort;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class DemoCarRentalProvider implements CarRentalProviderPort {

    private static final List<String> SUPPLIERS = List.of("Movida", "Localiza", "Unidas", "Rentcars");
    private static final List<CarCategory> CATEGORIES =
        List.of(CarCategory.ECONOMY, CarCategory.COMPACT, CarCategory.SUV, CarCategory.PREMIUM);

    @Override public boolean isDemo() { return true; }

    @Override public List<CarRentalOffer> searchCars(SearchCriteria criteria) {
        var random = new Random(criteria.hashCode());
        int days = DemoLodgingProvider.nights(criteria);
        var offers = new ArrayList<CarRentalOffer>();
        for (int index = 0; index < 4; index++) {
            var coverage = index % 2 == 0 ? InsuranceCoverageType.BASIC_CDW_TP : InsuranceCoverageType.ZERO_DEDUCTIBLE_FULL_COVERAGE;
            offers.add(new CarRentalOffer("demo-ca-" + index, SUPPLIERS.get(index), CATEGORIES.get(index),
                money(70 + random.nextInt(190)), days, coverage, money(22 + random.nextInt(25)),
                money(index % 2 == 0 ? 55 : 0), true,
                index % 2 == 0 ? PickupMode.IN_TERMINAL : PickupMode.SHUTTLE, 0.7 + random.nextDouble() * 0.29));
        }
        return List.copyOf(offers);
    }

    private static Money money(int amount) { return new Money(BigDecimal.valueOf(amount), Money.BRL); }
}
