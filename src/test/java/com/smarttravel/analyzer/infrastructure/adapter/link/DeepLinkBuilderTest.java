package com.smarttravel.analyzer.infrastructure.adapter.link;

import com.smarttravel.analyzer.domain.model.shared.SearchCriteria;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeepLinkBuilderTest {

    private static SearchCriteria criteria(String destination) {
        return new SearchCriteria("CWB", destination, LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 13), 2, true, false);
    }

    @Test void bookingUrlCarriesDestinationDatesAndTravelers() {
        var url = new BookingDeepLinkBuilder().searchUrl(criteria("REC")).toString();
        assertThat(url).startsWith("https://www.booking.com/searchresults.html?")
            .contains("ss=REC")
            .contains("checkin=2026-11-10")
            .contains("checkout=2026-11-13")
            .contains("group_adults=2");
    }

    @Test void bookingUrlEncodesAccentsAndSpaces() {
        assertThat(new BookingDeepLinkBuilder().searchUrl(criteria("São Paulo")).toString())
            .contains("ss=S%C3%A3o+Paulo");
    }

    @Test void bookingUrlEncodesAmpersandSoItCannotInjectAnotherParameter() {
        assertThat(new BookingDeepLinkBuilder().searchUrl(criteria("Rio&Bahia")).toString())
            .contains("ss=Rio%26Bahia")
            .doesNotContain("ss=Rio&Bahia");
    }

    @Test void googleFlightsUrlCarriesBothAirportsAndBothDates() {
        var url = new GoogleFlightsDeepLinkBuilder().searchUrl(criteria("REC")).toString();
        assertThat(url).startsWith("https://www.google.com/travel/flights?q=")
            .contains("CWB").contains("REC").contains("2026-11-10").contains("2026-11-13");
    }

    @Test void everyBuilderNamesItsPartner() {
        assertThat(new BookingDeepLinkBuilder().partnerName()).isEqualTo("Booking.com");
        assertThat(new GoogleFlightsDeepLinkBuilder().partnerName()).isEqualTo("Google Flights");
    }
}
