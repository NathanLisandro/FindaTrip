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

    private static TravelPackage build(String id, String total, double value, double quality, double convenience) {
        var flightOffer = new FlightOffer(id + "-f", new Airline("G3", "GOL"), Money.brl("800.00"), Money.brl("0.00"),
            Money.brl("0.00"), List.of(new FlightLeg(new Location("CWB", "Curitiba", "BR"),
            new Location("REC", "Recife", "BR"), null, false, false)), .9);
        var lodgingOffer = new LodgingOffer(id + "-l", "Hotel " + id, "Boa Viagem", Money.brl("100.00"), 1,
            Money.brl("0.00"), Money.brl("0.00"), Money.brl("0.00"), new HotelRating(8.5, 500),
            Set.of(Amenity.BREAKFAST_INCLUDED), 1.0);
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

    @Test void anEmptyCandidateListProducesNoRecommendations() {
        assertThat(bundler.topRecommendations(List.of())).isEmpty();
    }
}
