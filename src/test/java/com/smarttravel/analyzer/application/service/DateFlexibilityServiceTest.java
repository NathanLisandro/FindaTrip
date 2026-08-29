package com.smarttravel.analyzer.application.service;

import com.smarttravel.analyzer.domain.model.shared.SearchCriteria;
import com.smarttravel.analyzer.domain.service.*;
import com.smarttravel.analyzer.infrastructure.adapter.demo.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DateFlexibilityServiceTest {

    private static final LocalDate DEPARTURE = LocalDate.now().plusDays(60);
    private static final SearchCriteria CRITERIA =
        new SearchCriteria("CWB", "REC", DEPARTURE, DEPARTURE.plusDays(3), 2, true, false);

    private DateFlexibilityService service() {
        var assembler = new PackageAssemblerDomainService(new CostNormalizerDomainService(),
            new ValueScoringDomainService(), new PriceTrendDomainService());
        return new DateFlexibilityService(new DemoFlightProvider(), new DemoLodgingProvider(),
            new DemoCarRentalProvider(), assembler);
    }

    @Test void returnsSixNeighbouringDatesWithoutTheOriginalOne() {
        var options = service().neighbouringDates(CRITERIA);
        assertThat(options).hasSize(6);
        assertThat(options).noneSatisfy(option -> assertThat(option.departureDate()).isEqualTo(DEPARTURE));
    }

    @Test void everyOptionKeepsTheOriginalTripLength() {
        assertThat(service().neighbouringDates(CRITERIA)).allSatisfy(option ->
            assertThat(ChronoUnit.DAYS.between(option.departureDate(), option.returnDate())).isEqualTo(3));
    }

    @Test void optionsComeSortedByDepartureDate() {
        var options = service().neighbouringDates(CRITERIA);
        assertThat(options).isSortedAccordingTo(java.util.Comparator.comparing(option -> option.departureDate()));
    }

    @Test void theDifferenceIsNeverNegativeBecauseMoneyCannotBeNegative() {
        assertThat(service().neighbouringDates(CRITERIA)).allSatisfy(option ->
            assertThat(option.difference().amount().signum()).isNotNegative());
    }

    @Test void datesInThePastAreDiscarded() {
        var tomorrow = LocalDate.now().plusDays(1);
        var criteria = new SearchCriteria("CWB", "REC", tomorrow, tomorrow.plusDays(3), 2, true, false);
        assertThat(service().neighbouringDates(criteria)).allSatisfy(option ->
            assertThat(option.departureDate()).isAfterOrEqualTo(LocalDate.now()));
    }
}
