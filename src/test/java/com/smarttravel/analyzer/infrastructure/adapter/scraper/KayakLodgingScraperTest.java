package com.smarttravel.analyzer.infrastructure.adapter.scraper;

import com.smarttravel.analyzer.domain.model.lodging.LodgingOffer;
import com.smarttravel.analyzer.domain.model.shared.*;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KayakLodgingScraperTest {

    private static final SearchCriteria CRITERIA =
        new SearchCriteria("MGF", "Florianópolis", LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 14), 2, false, false);

    private static List<LodgingOffer> offers() {
        return new KayakLodgingScraper(null, null).parse(Fixtures.read("kayak-florianopolis"), CRITERIA);
    }

    @Test void readsEveryHotelCardOnThePage() {
        assertThat(offers()).hasSizeGreaterThan(15);
    }

    @Test void readsTheNameOutOfTheAccessibilityDescription() {
        assertThat(offers().getFirst().name()).isEqualTo("Rede Andrade Cecomtur");
    }

    @Test void takesTheCheapestProviderForEachHotel() {
        // O Kayak mostra o MESMO hotel em varios sites: 318 no Booking e 280 no Hotels.com.
        // O comparador existe para achar o menor, entao fica com o menor — e diz de onde veio.
        var primeiro = offers().getFirst();
        assertThat(primeiro.stayTotal().amount()).isEqualByComparingTo("1960.00");   // 280 x 7 noites
        assertThat(primeiro.source()).contains("Hotels.com");
    }

    @Test void namesEveryProviderItCompared() {
        assertThat(offers().getFirst().source()).contains("2 sites");
    }

    @Test void readsTheNeighborhoodWhenTheDescriptionCarriesOne() {
        assertThat(offers()).anySatisfy(offer -> assertThat(offer.neighborhood()).isEqualTo("Downtown"));
    }

    @Test void aDescriptionWithoutANeighborhoodDoesNotInventOne() {
        assertThat(offers()).anySatisfy(offer -> assertThat(offer.neighborhood()).isEqualTo("Não informado"));
    }

    @Test void readsRatingAndReviewCount() {
        var primeiro = offers().getFirst();
        assertThat(primeiro.rating().average()).isEqualTo(7.8);
        assertThat(primeiro.rating().reviewCount()).isEqualTo(10879);
    }

    @Test void nightsComeFromTheSearchDates() {
        assertThat(offers()).allSatisfy(offer -> assertThat(offer.nights()).isEqualTo(7));
    }

    @Test void everyPriceIsInBrl() {
        assertThat(offers()).allSatisfy(offer -> assertThat(offer.stayTotal().currency()).isEqualTo(Money.BRL));
    }

    @Test void kayakPricesAlreadyIncludeTaxesSoNoExtraFeeIsAdded() {
        // A pagina declara "Diaria total - Incluindo todos impostos e taxas".
        assertThat(offers()).allSatisfy(offer ->
            assertThat(offer.serviceFees().amount()).isEqualByComparingTo("0.00"));
    }

    @Test void declaresItselfAsRealDataNotDemo() {
        assertThat(new KayakLodgingScraper(null, null).isDemo()).isFalse();
    }
}
