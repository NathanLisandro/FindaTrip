package com.smarttravel.analyzer.domain.model.lodging;

import com.smarttravel.analyzer.domain.model.shared.Money;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LodgingOfferTest {

    private static LodgingOffer offer(String neighborhood) {
        return new LodgingOffer("lo-1", "Pousada Maré Alta", neighborhood, Money.brl("600.00"), 3,
            Money.brl("30.00"), Money.brl("20.00"), Money.brl("0.00"), new HotelRating(8.5, 500),
            Set.of(Amenity.BREAKFAST_INCLUDED), 1.0, "Booking.com", "https://www.booking.com/hotel/br/teste.html", null, StayType.HOTEL);
    }

    @Test void keepsTheNeighborhoodItWasGiven() {
        assertThat(offer("Boa Viagem").neighborhood()).isEqualTo("Boa Viagem");
    }

    @Test void aMissingNeighborhoodBecomesAReadableLabelInsteadOfNull() {
        assertThat(offer(null).neighborhood()).isEqualTo("Não informado");
        assertThat(offer("   ").neighborhood()).isEqualTo("Não informado");
    }

    @Test void normalizationStillAddsEveryFeeOnTopOfTheStayTotal() {
        assertThat(offer("Boa Viagem").normalize().totalPrice().amount()).isEqualByComparingTo("650.00");
    }
}
