package com.smarttravel.analyzer.domain.service;

import com.smarttravel.analyzer.domain.model.flight.*;
import com.smarttravel.analyzer.domain.model.lodging.*;
import com.smarttravel.analyzer.domain.model.packagebundle.*;
import com.smarttravel.analyzer.domain.model.search.NeighborhoodSummary;
import com.smarttravel.analyzer.domain.model.search.PackageFilter;
import com.smarttravel.analyzer.domain.model.shared.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PackageFilterDomainServiceTest {

    private final PackageFilterDomainService filters = new PackageFilterDomainService();

    private static TravelPackage build(String id, String total, double rating, int legs, Set<Amenity> amenities, String neighborhood) {
        var leg = new FlightLeg(new Location("CWB", "Curitiba", "BR"), new Location("REC", "Recife", "BR"),
            Duration.ofHours(2), false, false);
        var flightOffer = new FlightOffer(id + "-f", new Airline("G3", "GOL"), Money.brl("800.00"), Money.brl("0.00"),
            Money.brl("0.00"), legs == 1 ? List.of(leg) : List.of(leg, leg), .9);
        var lodgingOffer = new LodgingOffer(id + "-l", "Hotel " + id, neighborhood, Money.brl("100.00"), 1, Money.brl("0.00"),
            Money.brl("0.00"), Money.brl("0.00"), new HotelRating(rating, 500), amenities, 1.0, "Booking.com", "https://www.booking.com/hotel/br/teste.html");
        return new TravelPackage(id, Money.brl(total), new Score(80), new Score(80), new Score(80), null,
            new PackagePart<>(flightOffer, flightOffer.normalize(false)),
            new PackagePart<>(lodgingOffer, lodgingOffer.normalize()), null);
    }

    private static final TravelPackage CHEAP_DIRECT = build("cheap", "900.00", 9.0, 1, Set.of(Amenity.BREAKFAST_INCLUDED), "Boa Viagem");
    private static final TravelPackage PRICEY_STOPOVER = build("pricey", "2500.00", 7.0, 2, Set.of(Amenity.FREE_FLEXIBLE_CANCELLATION), "Centro");

    private static final List<TravelPackage> ALL = List.of(CHEAP_DIRECT, PRICEY_STOPOVER);

    @Test void noFilterKeepsEveryPackage() {
        assertThat(filters.apply(ALL, PackageFilter.none())).containsExactlyElementsOf(ALL);
    }

    @Test void maxPriceDropsPackagesAboveTheCeiling() {
        var filter = new PackageFilter(new BigDecimal("1000.00"), null, false, false, false, null);
        assertThat(filters.apply(ALL, filter)).containsExactly(CHEAP_DIRECT);
    }

    @Test void minRatingDropsPackagesBelowTheFloor() {
        var filter = new PackageFilter(null, 8.5, false, false, false, null);
        assertThat(filters.apply(ALL, filter)).containsExactly(CHEAP_DIRECT);
    }

    @Test void directFlightOnlyDropsPackagesWithConnections() {
        var filter = new PackageFilter(null, null, true, false, false, null);
        assertThat(filters.apply(ALL, filter)).containsExactly(CHEAP_DIRECT);
    }

    @Test void breakfastFilterKeepsOnlyLodgingWithBreakfast() {
        var filter = new PackageFilter(null, null, false, true, false, null);
        assertThat(filters.apply(ALL, filter)).containsExactly(CHEAP_DIRECT);
    }

    @Test void freeCancellationFilterKeepsOnlyFlexibleLodging() {
        var filter = new PackageFilter(null, null, false, false, true, null);
        assertThat(filters.apply(ALL, filter)).containsExactly(PRICEY_STOPOVER);
    }

    @Test void combinedFiltersMayLeaveNothing() {
        var filter = new PackageFilter(new BigDecimal("1000.00"), null, false, false, true, null);
        assertThat(filters.apply(ALL, filter)).isEmpty();
    }

    @Test void neighborhoodFilterKeepsOnlyPackagesInThatNeighborhood() {
        var filter = new PackageFilter(null, null, false, false, false, "Boa Viagem");
        assertThat(filters.apply(ALL, filter)).containsExactly(CHEAP_DIRECT);
    }

    @Test void neighborhoodFilterIgnoresAccentsAndCase() {
        var filter = new PackageFilter(null, null, false, false, false, "boa viagem");
        assertThat(filters.apply(ALL, filter)).containsExactly(CHEAP_DIRECT);
    }

    @Test void summariseGroupsPackagesByNeighborhoodWithCountAndCheapestPrice() {
        var summaries = filters.summarise(ALL);
        assertThat(summaries).hasSize(2);
        assertThat(summaries.getFirst().neighborhood()).isEqualTo("Boa Viagem");
        assertThat(summaries.getFirst().packages()).isEqualTo(1);
        assertThat(summaries.getFirst().cheapest().amount()).isEqualByComparingTo("900.00");
    }

    @Test void summariesComeSortedByCheapestPriceSoTheBestAreaIsFirst() {
        assertThat(filters.summarise(ALL))
            .extracting(NeighborhoodSummary::neighborhood)
            .containsExactly("Boa Viagem", "Centro");
    }
}
