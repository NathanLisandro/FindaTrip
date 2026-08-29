package com.smarttravel.analyzer.domain.service;

import com.smarttravel.analyzer.domain.model.flight.*;
import com.smarttravel.analyzer.domain.model.lodging.*;
import com.smarttravel.analyzer.domain.model.packagebundle.*;
import com.smarttravel.analyzer.domain.model.shared.*;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PackageBundlerDomainServiceTest {

    private final PackageBundlerDomainService bundler = new PackageBundlerDomainService();

    private static TravelPackage withLodging(String id, String total, double value, double quality,
                                             double convenience, String lodgingId) {
        var base = build(id, total, value, quality, convenience);
        var offer = base.lodging().offer();
        var renamed = new com.smarttravel.analyzer.domain.model.lodging.LodgingOffer(lodgingId, offer.name(),
            offer.neighborhood(), offer.stayTotal(), offer.nights(), offer.serviceFees(), offer.cityTaxes(),
            offer.resortFees(), offer.rating(), offer.amenities(), offer.distanceToAttractionsKm(),
            offer.source(), offer.url(), offer.imageUrl(), offer.type());
        return new TravelPackage(base.id(), base.totalPrice(), base.valueScore(), base.qualityScore(),
            base.convenienceScore(), base.recommendationType(), base.flight(),
            new PackagePart<>(renamed, renamed.normalize()), base.car());
    }

    private static TravelPackage build(String id, String total, double value, double quality, double convenience) {
        var flightOffer = new FlightOffer(id + "-f", new Airline("G3", "GOL"), Money.brl("800.00"), Money.brl("0.00"),
            Money.brl("0.00"), List.of(new FlightLeg(new Location("CWB", "Curitiba", "BR"),
            new Location("REC", "Recife", "BR"), null, false, false)), .9,
            java.time.LocalTime.of(8, 0), java.time.LocalTime.of(11, 30), java.time.Duration.ofMinutes(210));
        var lodgingOffer = new LodgingOffer(id + "-l", "Hotel " + id, "Boa Viagem", Money.brl("100.00"), 1,
            Money.brl("0.00"), Money.brl("0.00"), Money.brl("0.00"), new HotelRating(8.5, 500),
            Set.of(Amenity.BREAKFAST_INCLUDED), 1.0, "Booking.com", "https://www.booking.com/hotel/br/teste.html", null, StayType.HOTEL);
        return new TravelPackage(id, Money.brl(total), new Score(value), new Score(quality), new Score(convenience), null,
            new PackagePart<>(flightOffer, flightOffer.normalize(false)),
            new PackagePart<>(lodgingOffer, lodgingOffer.normalize()), null);
    }

    @Test void aSingleCandidateProducesOneRecommendationNotThreeCopiesOfIt() {
        var only = build("only", "1000.00", 90, 90, 90);
        var recommendations = bundler.topRecommendations(List.of(only));
        assertThat(recommendations).hasSize(1);
        assertThat(recommendations.getFirst().recommendationType()).isEqualTo(PackageBundleType.BEST_VALUE_OVERALL);
    }

    @Test void neverRepeatsTheSamePackageUnderTwoLabels() {
        var strong = build("strong", "1000.00", 95, 95, 95);
        var weak = build("weak", "2000.00", 40, 40, 40);
        assertThat(bundler.topRecommendations(List.of(strong, weak)))
            .extracting(TravelPackage::id).doesNotHaveDuplicates();
    }

    @Test void threeDistinctProfilesProduceThreeDistinctRecommendations() {
        var bestValue = build("value", "1500.00", 95, 80, 80);
        var cheapest = build("budget", "900.00", 70, 75, 65);
        var comfiest = build("comfort", "3000.00", 60, 99, 99);
        var recommendations = bundler.topRecommendations(List.of(bestValue, cheapest, comfiest));
        assertThat(recommendations).hasSize(3)
            .extracting(TravelPackage::recommendationType)
            .containsExactly(PackageBundleType.BEST_VALUE_OVERALL, PackageBundleType.SMART_BUDGET, PackageBundleType.MAX_COMFORT);
    }

    @Test void whenOnePackageWinsEveryProfileTheNextBestOnesFillTheOtherSlots() {
        var dominant = build("dominant", "1000.00", 99, 99, 99);
        var second = build("second", "1200.00", 80, 80, 80);
        var third = build("third", "1400.00", 70, 70, 70);
        var recommendations = bundler.topRecommendations(List.of(dominant, second, third));
        assertThat(recommendations).hasSize(3).extracting(TravelPackage::id).doesNotHaveDuplicates();
        assertThat(recommendations.getFirst().id()).isEqualTo("dominant");
    }

    @Test void twoCandidatesProduceTwoRecommendationsNotThree() {
        var dominant = build("dominant", "1000.00", 99, 99, 99);
        var second = build("second", "1200.00", 80, 80, 80);
        assertThat(bundler.topRecommendations(List.of(dominant, second))).hasSize(2);
    }

    @Test void theThreeProfilesShowThreeDifferentStaysWhenThereAreEnoughOfThem() {
        // Tres cards com o mesmo hotel nao sao tres opcoes: sao um card repetido tres vezes.
        var a = withLodging("a", "1000.00", 95, 95, 95, "hotel-a");
        var b = withLodging("b", "1100.00", 90, 90, 90, "hotel-b");
        var c = withLodging("c", "1200.00", 85, 85, 85, "hotel-c");
        var aAgain = withLodging("a2", "1050.00", 94, 94, 94, "hotel-a");
        assertThat(bundler.topRecommendations(List.of(a, aAgain, b, c)))
            .extracting(pkg -> pkg.lodging().offer().id())
            .doesNotHaveDuplicates();
    }

    @Test void repeatsAStayOnlyWhenThereIsNoOtherOptionLeft() {
        var only = withLodging("a", "1000.00", 95, 95, 95, "hotel-a");
        var sameStay = withLodging("a2", "1200.00", 80, 80, 80, "hotel-a");
        assertThat(bundler.topRecommendations(List.of(only, sameStay))).hasSize(1);
    }

    @Test void listsTheRestBehindTheThreeProfilesInsteadOfThrowingThemAway() {
        // Tres opcoes escondem as outras 300. O usuario quer navegar a lista inteira,
        // com as escolhidas na frente.
        var candidatos = new java.util.ArrayList<TravelPackage>();
        for (int i = 0; i < 40; i++) {
            candidatos.add(withLodging("p" + i, String.valueOf(1000 + i * 50) + ".00",
                95 - i, 90 - i, 85 - i, "hotel-" + i));
        }
        var lista = bundler.rankedList(candidatos, 25);
        assertThat(lista).hasSize(25);
        assertThat(lista.subList(0, 3)).extracting(TravelPackage::recommendationType)
            .containsExactly(PackageBundleType.BEST_VALUE_OVERALL, PackageBundleType.SMART_BUDGET, PackageBundleType.MAX_COMFORT);
        assertThat(lista.subList(3, lista.size()))
            .allSatisfy(p -> assertThat(p.recommendationType()).isNull());
    }

    @Test void theRankedListNeverRepeatsAPackage() {
        var candidatos = java.util.List.of(
            withLodging("a", "1000.00", 95, 95, 95, "hotel-a"),
            withLodging("b", "1200.00", 80, 80, 80, "hotel-b"),
            withLodging("c", "1400.00", 70, 70, 70, "hotel-c"));
        assertThat(bundler.rankedList(candidatos, 20))
            .extracting(TravelPackage::id).doesNotHaveDuplicates();
    }

    @Test void theRestComesOrderedByValueScore() {
        var candidatos = java.util.List.of(
            withLodging("otimo", "1000.00", 99, 99, 99, "h1"),
            withLodging("medio", "1500.00", 70, 70, 70, "h2"),
            withLodging("fraco", "1600.00", 40, 40, 40, "h3"),
            withLodging("bom", "1400.00", 85, 85, 85, "h4"));
        var lista = bundler.rankedList(candidatos, 10);
        var resto = lista.subList(3, lista.size());
        assertThat(resto).isSortedAccordingTo(
            java.util.Comparator.comparingDouble((TravelPackage p) -> p.valueScore().value()).reversed());
    }

    @Test void anEmptyCandidateListProducesNoRecommendations() {
        assertThat(bundler.topRecommendations(List.of())).isEmpty();
    }
}
