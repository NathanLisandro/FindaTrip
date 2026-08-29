package com.smarttravel.analyzer.infrastructure.adapter.demo;

import com.smarttravel.analyzer.domain.model.shared.*;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DemoProvidersTest {

    private static final SearchCriteria CRITERIA =
        new SearchCriteria("CWB", "REC", LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 13), 2, true, true);

    @Test void everyDemoProviderDeclaresItselfAsDemo() {
        assertThat(new DemoFlightProvider().isDemo()).isTrue();
        assertThat(new DemoLodgingProvider().isDemo()).isTrue();
        assertThat(new DemoCarRentalProvider().isDemo()).isTrue();
    }

    @Test void flightProviderReturnsSeveralOffersAllInBrl() {
        var offers = new DemoFlightProvider().searchFlights(CRITERIA);
        assertThat(offers).hasSize(6);
        assertThat(offers).allSatisfy(offer -> assertThat(offer.rawFare().currency()).isEqualTo(Money.BRL));
    }

    @Test void flightOffersDepartFromTheRequestedOrigin() {
        assertThat(new DemoFlightProvider().searchFlights(CRITERIA))
            .allSatisfy(offer -> assertThat(offer.legs().getFirst().origin().code()).isEqualTo("CWB"));
    }

    @Test void lodgingNightsMatchTheTripLength() {
        assertThat(new DemoLodgingProvider().searchLodging(CRITERIA))
            .allSatisfy(offer -> assertThat(offer.nights()).isEqualTo(3));
    }

    @Test void carRentalDaysMatchTheTripLength() {
        assertThat(new DemoCarRentalProvider().searchCars(CRITERIA))
            .allSatisfy(offer -> assertThat(offer.rentalDays()).isEqualTo(3));
    }

    @Test void theSameSearchAlwaysReturnsTheSameOffers() {
        var first = new DemoLodgingProvider().searchLodging(CRITERIA);
        var second = new DemoLodgingProvider().searchLodging(CRITERIA);
        assertThat(first).isEqualTo(second);
    }

    @Test void differentSearchesReturnDifferentOffers() {
        var other = new SearchCriteria("CWB", "SSA", LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 13), 2, true, true);
        assertThat(new DemoLodgingProvider().searchLodging(CRITERIA))
            .isNotEqualTo(new DemoLodgingProvider().searchLodging(other));
    }

    @Test void lodgingOffersSpreadAcrossSeveralNeighborhoodsWithMoreThanOnePerArea() {
        var neighborhoods = new DemoLodgingProvider().searchLodging(CRITERIA).stream()
            .map(offer -> offer.neighborhood()).toList();
        assertThat(neighborhoods).doesNotContainNull();
        assertThat(java.util.Set.copyOf(neighborhoods)).hasSizeGreaterThan(2).hasSizeLessThan(neighborhoods.size());
    }

    @Test void atLeastOneFlightIsDirectAndAtLeastOneHasAConnection() {
        var offers = new DemoFlightProvider().searchFlights(CRITERIA);
        assertThat(offers).anySatisfy(offer -> assertThat(offer.stops()).isZero());
        assertThat(offers).anySatisfy(offer -> assertThat(offer.stops()).isPositive());
    }
}
