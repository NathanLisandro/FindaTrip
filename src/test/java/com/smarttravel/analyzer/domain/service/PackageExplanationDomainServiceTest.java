package com.smarttravel.analyzer.domain.service;

import com.smarttravel.analyzer.domain.model.flight.*;
import com.smarttravel.analyzer.domain.model.lodging.*;
import com.smarttravel.analyzer.domain.model.packagebundle.*;
import com.smarttravel.analyzer.domain.model.shared.*;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PackageExplanationDomainServiceTest {

    private final PackageExplanationDomainService explanations = new PackageExplanationDomainService();

    private static TravelPackage build(String id, String total, double rating, int reviews, double quality) {
        var flightOffer = new FlightOffer(id + "-f", new Airline("G3", "GOL"), Money.brl("800.00"), Money.brl("90.00"),
            Money.brl("0.00"), List.of(new FlightLeg(new Location("CWB", "Curitiba", "BR"),
            new Location("REC", "Recife", "BR"), null, false, false)), .9);
        var lodgingOffer = new LodgingOffer(id + "-l", "Hotel " + id, "Centro", Money.brl("100.00"), 1, Money.brl("30.00"),
            Money.brl("0.00"), Money.brl("0.00"), new HotelRating(rating, reviews), Set.of(Amenity.BREAKFAST_INCLUDED), 1.0, "Booking.com", "https://www.booking.com/hotel/br/teste.html");
        return new TravelPackage(id, Money.brl(total), new Score(85), new Score(quality), new Score(80), null,
            new PackagePart<>(flightOffer, flightOffer.normalize(false)),
            new PackagePart<>(lodgingOffer, lodgingOffer.normalize()), null);
    }

    @Test void translatesEveryCostAdjustmentCodeToPortuguese() {
        assertThat(explanations.translateAdjustment("mandatory_airport_taxes")).isEqualTo("taxa de embarque");
        assertThat(explanations.translateAdjustment("zero_deductible_full_coverage")).isEqualTo("seguro sem franquia");
    }

    @Test void unknownAdjustmentCodesFallBackToTheRawCode() {
        assertThat(explanations.translateAdjustment("mystery_fee")).isEqualTo("mystery_fee");
    }

    @Test void explanationMentionsTheHiddenCostTheUserWouldNotHaveSeen() {
        var chosen = build("a", "1020.00", 8.9, 2000, 90);
        assertThat(explanations.explain(chosen, List.of(chosen)))
            .contains("120,00")
            .contains("taxa de embarque")
            .contains("taxa de serviço");
    }

    @Test void explanationCreditsReviewVolumeWhenTheChosenHotelIsNotTheTopRated() {
        var chosen = build("chosen", "1020.00", 8.9, 2000, 90);
        var flashy = build("flashy", "1020.00", 10.0, 3, 60);
        assertThat(explanations.explain(chosen, List.of(chosen, flashy)))
            .contains("2.000 avaliações")
            .contains("3 avaliações");
    }

    @Test void neverLeavesACardWithoutAReason() {
        // Um card mudo e pior que um card simples: o usuario fica sem saber por que aquilo venceu.
        var chosen = build("chosen", "1020.00", 8.9, 2000, 90);
        var cheaper = build("cheaper", "900.00", 8.9, 2000, 90);
        assertThat(explanations.explain(chosen, List.of(chosen, cheaper))).isNotBlank();
    }

    @Test void saysThePriceIsClosedWhenThereIsNothingHiddenToWarnAbout() {
        var chosen = build("chosen", "900.00", 8.9, 2000, 90);   // igual ao anunciado: nada escondido
        var cheaper = build("cheaper", "880.00", 8.9, 2000, 90);
        assertThat(explanations.explain(chosen, List.of(chosen, cheaper)))
            .containsIgnoringCase("sem cobran");
    }

    @Test void explanationSaysItIsTheCheapestWhenItActuallyIs() {
        var chosen = build("chosen", "1020.00", 8.9, 2000, 90);
        var pricier = build("pricier", "2500.00", 8.9, 2000, 90);
        assertThat(explanations.explain(chosen, List.of(chosen, pricier))).contains("mais barato");
    }
}
