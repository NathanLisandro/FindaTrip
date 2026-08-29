package com.smarttravel.analyzer.domain.model.packagebundle;

import com.smarttravel.analyzer.domain.model.carrental.*;
import com.smarttravel.analyzer.domain.model.flight.*;
import com.smarttravel.analyzer.domain.model.lodging.*;
import com.smarttravel.analyzer.domain.model.shared.*;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TravelPackageTest {

    private static PackagePart<FlightOffer> flightPart() {
        var offer = new FlightOffer("fl-1", new Airline("G3", "GOL"), Money.brl("800.00"), Money.brl("90.00"),
            Money.brl("60.00"), List.of(new FlightLeg(new Location("CWB", "Curitiba", "BR"),
            new Location("REC", "Recife", "BR"), null, false, false)), .9);
        return new PackagePart<>(offer, offer.normalize(true));
    }

    private static PackagePart<LodgingOffer> lodgingPart() {
        var offer = new LodgingOffer("lo-1", "Pousada Boa Vista", "Boa Viagem", Money.brl("600.00"), 3, Money.brl("30.00"),
            Money.brl("20.00"), Money.brl("0.00"), new HotelRating(8.9, 2000),
            Set.of(Amenity.BREAKFAST_INCLUDED), 1.0, "Booking.com", "https://www.booking.com/hotel/br/teste.html");
        return new PackagePart<>(offer, offer.normalize());
    }

    private static TravelPackage packageWithoutCar() {
        var flight = flightPart();
        var lodging = lodgingPart();
        var total = flight.price().totalPrice().add(lodging.price().totalPrice());
        return new TravelPackage("fl-1|lo-1", total, new Score(90), new Score(85), new Score(80),
            null, flight, lodging, null);
    }

    @Test void advertisedPriceSumsTheRawPricesOfEveryPart() {
        assertThat(packageWithoutCar().advertisedPrice().amount()).isEqualByComparingTo("1400.00");
    }

    @Test void hiddenCostsAreTheDifferenceBetweenRealAndAdvertisedPrice() {
        assertThat(packageWithoutCar().hiddenCosts().amount()).isEqualByComparingTo("200.00");
    }

    @Test void costAdjustmentsListEveryAddedFeeWithoutRepeating() {
        assertThat(packageWithoutCar().costAdjustments())
            .containsExactly("mandatory_airport_taxes", "checked_baggage",
                             "service_fees", "city_taxes", "resort_fees");
    }

    @Test void packageWithoutCarReportsItHasNoCar() {
        assertThat(packageWithoutCar().hasCar()).isFalse();
    }

    @Test void withRecommendationTypeKeepsEveryOtherField() {
        var tagged = packageWithoutCar().withRecommendationType(PackageBundleType.BEST_VALUE_OVERALL);
        assertThat(tagged.recommendationType()).isEqualTo(PackageBundleType.BEST_VALUE_OVERALL);
        assertThat(tagged.id()).isEqualTo("fl-1|lo-1");
        assertThat(tagged.lodging().offer().name()).isEqualTo("Pousada Boa Vista");
    }
}
