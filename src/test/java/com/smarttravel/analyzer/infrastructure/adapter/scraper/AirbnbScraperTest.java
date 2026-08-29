package com.smarttravel.analyzer.infrastructure.adapter.scraper;

import com.smarttravel.analyzer.domain.model.lodging.Amenity;
import com.smarttravel.analyzer.domain.model.lodging.LodgingOffer;
import com.smarttravel.analyzer.domain.model.shared.*;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AirbnbScraperTest {

    private static final SearchCriteria CRITERIA =
        new SearchCriteria("MGF", "Florianópolis", LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 14), 2, true, false);

    private static List<LodgingOffer> offers() {
        return new AirbnbScraper(null, null).parse(Fixtures.read("airbnb-florianopolis"), CRITERIA);
    }

    @Test void readsEveryListingOnThePage() {
        assertThat(offers()).hasSizeGreaterThan(15);
    }

    @Test void readsTheRealNameOfTheFirstListing() {
        assertThat(offers().getFirst().name()).isEqualTo("Studio em Cond. c/ Piscina e Vista Cid. OGD0804");
    }

    @Test void takesTheDiscountedTotalNotTheInstallmentAmount() {
        // O card mostra "Total: R$ 3.116 R$ 2.766" e depois "6x R$ 461".
        // 461 e parcela, nao diaria: usar o ultimo valor da string desprezaria a estadia inteira.
        var first = offers().getFirst();
        assertThat(first.stayTotal().amount()).isEqualByComparingTo("2766.00");
    }

    @Test void nightsComeFromTheSearchDates() {
        assertThat(offers()).allSatisfy(offer -> assertThat(offer.nights()).isEqualTo(7));
    }

    @Test void convertsTheFiveStarScaleToTheTenPointOneTheDomainUses() {
        // O Airbnb pontua de 0 a 5 e o Booking de 0 a 10. Sem converter, um 4,92 excelente
        // entraria no ranking como se fosse 4,92 de 10, ou seja, pessimo.
        assertThat(offers()).anySatisfy(offer -> {
            assertThat(offer.rating().average()).isEqualTo(9.84);
            assertThat(offer.rating().reviewCount()).isEqualTo(88);
        });
    }

    @Test void noRatingEverExceedsTheTenPointScale() {
        assertThat(offers()).allSatisfy(offer ->
            assertThat(offer.rating().average()).isBetween(0.0, 10.0));
    }

    @Test void aListingWithoutReviewsScoresZeroInsteadOfAnInventedRating() {
        assertThat(offers()).anySatisfy(offer -> {
            assertThat(offer.rating().reviewCount()).isZero();
            assertThat(offer.rating().average()).isZero();
        });
    }

    @Test void readsFreeCancellationAsAnAmenity() {
        assertThat(offers()).anySatisfy(offer ->
            assertThat(offer.has(Amenity.FREE_FLEXIBLE_CANCELLATION)).isTrue());
    }

    @Test void airbnbDoesNotPublishNeighborhoodSoItSaysSoInsteadOfGuessing() {
        assertThat(offers()).allSatisfy(offer ->
            assertThat(offer.neighborhood()).isEqualTo("Não informado"));
    }

    @Test void everyPriceIsInBrl() {
        assertThat(offers()).allSatisfy(offer -> assertThat(offer.stayTotal().currency()).isEqualTo(Money.BRL));
    }

    @Test void everyOfferSaysItCameFromAirbnb() {
        assertThat(offers()).allSatisfy(offer -> assertThat(offer.source()).isEqualTo("Airbnb"));
    }

    @Test void everyOfferCarriesTheLinkToItsOwnListing() {
        assertThat(offers()).allSatisfy(offer ->
            assertThat(offer.url()).startsWith("https://www.airbnb.com.br/rooms/"));
    }

    @Test void theListingLinkKeepsTheDatesAndGuestsOfTheSearch() {
        assertThat(offers().getFirst().url()).contains("check_in=2026-11-07").contains("adults=2");
    }

    @Test void dropsListingsTheSiteRepricedForOtherDates() {
        // O Airbnb mistura, no meio dos resultados, anuncios INDISPONIVEIS no periodo pedido,
        // mostrando datas proximas ("check_in=2026-11-05"). Aceitar isso e precificar 6 noites
        // de outra data como se fossem as 7 noites que o usuario pediu.
        assertThat(offers()).allSatisfy(offer -> assertThat(offer.url())
            .contains("check_in=2026-11-07").contains("check_out=2026-11-14"));
    }

    @Test void neverDoublesTheHostWhenTheSiteOmitsTheScheme() {
        assertThat(offers()).allSatisfy(offer ->
            assertThat(offer.url()).doesNotContain("com.brwww"));
    }

    @Test void declaresItselfAsRealDataNotDemo() {
        assertThat(new AirbnbScraper(null, null).isDemo()).isFalse();
    }
}
