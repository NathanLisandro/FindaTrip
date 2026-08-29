package com.smarttravel.analyzer.infrastructure.adapter.scraper;

import com.smarttravel.analyzer.domain.model.lodging.Amenity;
import com.smarttravel.analyzer.domain.model.shared.*;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BookingScraperTest {

    private static final SearchCriteria CRITERIA =
        new SearchCriteria("MGF", "Florianópolis", LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 14), 2, true, false);

    private static java.util.List<com.smarttravel.analyzer.domain.model.lodging.LodgingOffer> offers() {
        return new BookingScraper(null, null).parse(Fixtures.read("booking-florianopolis"), CRITERIA);
    }

    @Test void readsEveryPropertyCardOnThePage() {
        assertThat(offers()).hasSizeGreaterThan(15);
    }

    @Test void readsTheRealNameOfTheFirstProperty() {
        assertThat(offers().getFirst().name()).isEqualTo("Valentina 24 HORAS");
    }

    @Test void takesTheNeighborhoodFromTheAddressBeforeTheComma() {
        assertThat(offers()).anySatisfy(offer -> assertThat(offer.neighborhood()).isEqualTo("Campeche"));
        assertThat(offers()).anySatisfy(offer -> assertThat(offer.neighborhood()).isEqualTo("Canasvieiras"));
    }

    @Test void aPropertyWithoutANeighborhoodFallsBackToTheReadableLabel() {
        assertThat(offers()).anySatisfy(offer -> assertThat(offer.neighborhood()).isEqualTo("Não informado"));
    }

    /**
     * O card anuncia "R$ 1.540 R$ 893": 893 e o que se paga, pelas 7 noites inteiras.
     * A diaria e 893/7 = 127,5714..., e Money tem duas casas: 127,57 x 7 = 892,99.
     * Nao existe diaria de dois decimais que multiplicada por 7 de 893 exatos, entao o
     * teste cobra o centavo de arredondamento em vez de fingir que ele nao existe.
     */
    @Test void takesTheDiscountedPriceNotTheStruckThroughOne() {
        var valentina = offers().stream().filter(o -> o.name().equals("Valentina 24 HORAS")).findFirst().orElseThrow();
        assertThat(valentina.stayTotal().amount()).isEqualByComparingTo("893.00");
        assertThat(valentina.stayTotal().amount()).isEqualByComparingTo("893.00");
    }

    @Test void readsTheTaxesBookingAddsOnTopOfTheAdvertisedPrice() {
        var valentina = offers().stream().filter(o -> o.name().equals("Valentina 24 HORAS")).findFirst().orElseThrow();
        assertThat(valentina.serviceFees().amount()).isEqualByComparingTo("693.00");
    }

    @Test void taxesAlreadyIncludedMeanNoExtraFee() {
        var included = offers().stream().filter(o -> o.name().startsWith("Refúgio do Cacupé")).findFirst().orElseThrow();
        assertThat(included.serviceFees().amount()).isEqualByComparingTo("0.00");
    }

    @Test void nightsComeFromTheSearchDatesNotFromThePage() {
        assertThat(offers()).allSatisfy(offer -> assertThat(offer.nights()).isEqualTo(7));
    }

    @Test void readsRatingAndReviewCount() {
        var valentina = offers().stream().filter(o -> o.name().equals("Valentina 24 HORAS")).findFirst().orElseThrow();
        assertThat(valentina.rating().average()).isEqualTo(7.7);
        assertThat(valentina.rating().reviewCount()).isEqualTo(700);
    }

    @Test void readsBreakfastAsAnAmenity() {
        assertThat(offers()).anySatisfy(offer -> assertThat(offer.has(Amenity.BREAKFAST_INCLUDED)).isTrue());
    }

    @Test void everyPriceIsInBrl() {
        assertThat(offers()).allSatisfy(offer -> assertThat(offer.stayTotal().currency()).isEqualTo(Money.BRL));
    }

    @Test void everyOfferSaysItCameFromBooking() {
        assertThat(offers()).allSatisfy(offer -> assertThat(offer.source()).isEqualTo("Booking.com"));
    }

    @Test void everyOfferCarriesTheLinkToItsOwnListing() {
        // Mandar o usuario para a pagina de busca e fazer ele procurar de novo o que ja achamos.
        assertThat(offers()).allSatisfy(offer -> {
            assertThat(offer.url()).isNotBlank();
            assertThat(offer.url()).startsWith("https://www.booking.com/hotel/");
        });
    }

    @Test void declaresItselfAsRealDataNotDemo() {
        assertThat(new BookingScraper(null, null).isDemo()).isFalse();
    }
}
