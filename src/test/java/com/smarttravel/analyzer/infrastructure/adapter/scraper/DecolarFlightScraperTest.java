package com.smarttravel.analyzer.infrastructure.adapter.scraper;

import com.smarttravel.analyzer.domain.model.flight.FlightOffer;
import com.smarttravel.analyzer.domain.model.shared.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DecolarFlightScraperTest {

    private static final SearchCriteria CRITERIA =
        new SearchCriteria("MGF", "FLN", LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 14), 2, false, false);

    private static List<FlightOffer> offers() {
        return new DecolarFlightScraper(null, null).parse(Fixtures.read("decolar-mgf-fln"), CRITERIA);
    }

    @Test void readsEveryItineraryOnThePage() {
        assertThat(offers()).hasSizeGreaterThan(10);
    }

    @Test void readsDepartureAndArrivalOfTheFirstItinerary() {
        var primeiro = offers().getFirst();
        assertThat(primeiro.departureTime()).isEqualTo(LocalTime.of(16, 35));
        assertThat(primeiro.arrivalTime()).isEqualTo(LocalTime.of(21, 45));
    }

    @Test void readsTheDurationAsShownOnThePage() {
        assertThat(offers().getFirst().totalDuration()).isEqualTo(java.time.Duration.ofHours(5).plusMinutes(10));
    }

    @Test void readsThePriceThatSitsWithTheItinerary() {
        assertThat(offers().getFirst().rawFare().amount()).isEqualByComparingTo("838.00");
    }

    @Test void theDecolarPriceAlreadyCoversTheWholeParty() {
        // A URL leva o numero de passageiros, entao o preco ja vem do grupo.
        // Multiplicar de novo, como e preciso no Google Voos, dobraria a conta.
        var sozinho = new SearchCriteria("MGF", "FLN", LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 14), 1, false, false);
        var html = Fixtures.read("decolar-mgf-fln");
        assertThat(new DecolarFlightScraper(null, null).parse(html, sozinho).getFirst().rawFare().amount())
            .isEqualByComparingTo(offers().getFirst().rawFare().amount());
    }

    @Test void countsStopsFromTheItinerary() {
        assertThat(offers()).anySatisfy(offer -> assertThat(offer.stops()).isEqualTo(1));
    }

    @Test void everyItineraryStartsAtTheRequestedOrigin() {
        assertThat(offers()).allSatisfy(offer ->
            assertThat(offer.legs().getFirst().origin().code()).isEqualTo("MGF"));
    }

    @Test void everyFareIsInBrl() {
        assertThat(offers()).allSatisfy(offer -> assertThat(offer.rawFare().currency()).isEqualTo(Money.BRL));
    }

    @Test void namesTheCarrierEvenThoughThePageOnlyShowsItsLogo() {
        // A Decolar nao escreve o nome: ele esta no alt do logo. Sem ler dali, todo card
        // dizia "companhia nao identificada", o que e pior do que nao ter a informacao.
        assertThat(offers()).anySatisfy(offer -> {
            assertThat(offer.airline().name()).isEqualTo("LATAM");
            assertThat(offer.airline().iataCode()).isEqualTo("LA");
        });
        assertThat(offers()).noneSatisfy(offer ->
            assertThat(offer.airline().iataCode()).isEqualTo("--"));
    }

    @Test void declaresItselfAsRealDataNotDemo() {
        assertThat(new DecolarFlightScraper(null, null).isDemo()).isFalse();
    }
}
