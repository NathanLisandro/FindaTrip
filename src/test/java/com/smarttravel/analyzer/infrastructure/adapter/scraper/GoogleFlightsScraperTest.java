package com.smarttravel.analyzer.infrastructure.adapter.scraper;

import com.smarttravel.analyzer.domain.model.flight.FlightOffer;
import com.smarttravel.analyzer.domain.model.shared.*;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleFlightsScraperTest {

    private static final SearchCriteria CRITERIA =
        new SearchCriteria("MGF", "FLN", LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 14), 1, false, false);

    private static List<FlightOffer> offers() {
        return new GoogleFlightsScraper(null, null).parse(Fixtures.read("google-flights-mgf-fln"), CRITERIA);
    }

    @Test void readsSeveralRealFlightsFromMaringaToFlorianopolis() {
        assertThat(offers()).hasSizeGreaterThan(3);
    }

    @Test void everyFareIsInBrlAndAbovePocketChange() {
        assertThat(offers()).allSatisfy(offer ->
            assertThat(offer.rawFare().currency()).isEqualTo(Money.BRL));
        assertThat(offers()).allSatisfy(offer ->
            assertThat(offer.rawFare().amount().doubleValue()).isGreaterThan(100));
    }

    @Test void recognisesTheBrazilianCarriers() {
        assertThat(offers()).extracting(offer -> offer.airline().name())
            .contains("LATAM");
    }

    @Test void countsStopsFromTheItinerary() {
        assertThat(offers()).anySatisfy(offer -> assertThat(offer.stops()).isEqualTo(1));
    }

    @Test void aFlightWithALongLayoverIsMarkedSoTheScoringCanPenaliseIt() {
        assertThat(offers()).anySatisfy(offer ->
            assertThat(offer.legs().getFirst().hasLongLayover()).isTrue());
    }

    @Test void theItineraryStartsAtTheRequestedOrigin() {
        assertThat(offers()).allSatisfy(offer ->
            assertThat(offer.legs().getFirst().origin().code()).isEqualTo("MGF"));
    }

    @Test void theSameFlightIsNotListedTwiceEvenThoughThePageRepeatsIt() {
        // O Google monta "Principais voos de ida" e "Outros voos de ida" com os mesmos itens.
        // Duplicata envenena a montagem: o mesmo voo contaria duas vezes entre os cinco mais baratos.
        var signatures = offers().stream()
            .map(offer -> offer.airline().name() + "|" + offer.rawFare().amount() + "|" + offer.stops()
                + "|" + offer.legs().getFirst().destination().code()
                + "|" + offer.legs().getFirst().layoverBeforeNextLeg())
            .toList();
        assertThat(signatures).doesNotHaveDuplicates();
    }

    @Test void theFareCoversEveryTravellerNotJustOne() {
        // A propria pagina avisa: "os precos incluem os tributos e tarifas obrigatorios para 1 adulto".
        // Sem multiplicar, o voo de 2 pessoas entrava pela metade e o pacote saia barato demais.
        var umAdulto = new SearchCriteria("MGF", "FLN", LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 14), 1, false, false);
        var doisAdultos = new SearchCriteria("MGF", "FLN", LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 14), 2, false, false);
        var html = Fixtures.read("google-flights-mgf-fln");
        var sozinho = new GoogleFlightsScraper(null, null).parse(html, umAdulto).getFirst();
        var casal = new GoogleFlightsScraper(null, null).parse(html, doisAdultos).getFirst();
        assertThat(casal.rawFare().amount()).isEqualByComparingTo(sozinho.rawFare().amount().multiply(java.math.BigDecimal.TWO));
    }

    @Test void readsDepartureAndArrivalTimes() {
        // Sem horario o usuario nao sabe se sai de madrugada nem se chega a tempo do check-in.
        var primeiro = offers().getFirst();
        assertThat(primeiro.departureTime()).isNotNull();
        assertThat(primeiro.arrivalTime()).isNotNull();
        assertThat(offers()).anySatisfy(offer ->
            assertThat(offer.departureTime()).isEqualTo(java.time.LocalTime.of(6, 20)));
        assertThat(offers()).anySatisfy(offer ->
            assertThat(offer.arrivalTime()).isEqualTo(java.time.LocalTime.of(15, 0)));
    }

    @Test void readsTheTotalDurationOfTheItinerary() {
        assertThat(offers()).anySatisfy(offer ->
            assertThat(offer.totalDuration()).isEqualTo(java.time.Duration.ofHours(8).plusMinutes(40)));
    }

    @Test void everyOfferKeepsTheCarrierCodeSoTheLogoCanBeShown() {
        assertThat(offers()).anySatisfy(offer -> assertThat(offer.airline().iataCode()).isEqualTo("G3"));
        assertThat(offers()).anySatisfy(offer -> assertThat(offer.airline().iataCode()).isEqualTo("LA"));
    }

    @Test void declaresItselfAsRealDataNotDemo() {
        assertThat(new GoogleFlightsScraper(null, null).isDemo()).isFalse();
    }
}
