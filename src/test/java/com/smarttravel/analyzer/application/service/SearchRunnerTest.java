package com.smarttravel.analyzer.application.service;

import com.smarttravel.analyzer.application.search.SearchSession;
import com.smarttravel.analyzer.domain.model.carrental.CarRentalOffer;
import com.smarttravel.analyzer.domain.model.flight.FlightOffer;
import com.smarttravel.analyzer.domain.model.lodging.LodgingOffer;
import com.smarttravel.analyzer.domain.model.search.*;
import com.smarttravel.analyzer.domain.model.shared.SearchCriteria;
import com.smarttravel.analyzer.domain.repository.*;
import com.smarttravel.analyzer.domain.service.*;
import com.smarttravel.analyzer.infrastructure.adapter.demo.*;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchRunnerTest {

    private static final SearchCriteria CRITERIA =
        new SearchCriteria("CWB", "REC", LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 13), 2, true, false);

    private static final SearchCriteria CRITERIA_WITH_CAR =
        new SearchCriteria("CWB", "REC", LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 13), 2, true, true);

    private static SearchRunner runner(FlightProviderPort flights, LodgingProviderPort lodgings, CarRentalProviderPort cars) {
        var scoring = new ValueScoringDomainService();
        var assembler = new PackageAssemblerDomainService(new CostNormalizerDomainService(), scoring, new PriceTrendDomainService());
        var dateFlexibility = new DateFlexibilityService(flights, lodgings, cars, assembler);
        return new SearchRunner(flights, lodgings, cars, assembler, new PackageBundlerDomainService(), dateFlexibility);
    }

    private static SearchRunner healthyRunner() {
        return runner(new DemoFlightProvider(), new DemoLodgingProvider(), new DemoCarRentalProvider());
    }

    @Test void aHealthySearchEndsReadyWithThreeRecommendations() {
        var session = new SearchSession("s1", CRITERIA);
        healthyRunner().run(session);
        assertThat(session.status()).isEqualTo(SearchStatus.PRONTO);
        assertThat(session.packages()).hasSize(3);
    }

    @Test void aHealthySearchMarksEverySourceAsOk() {
        var session = new SearchSession("s1", CRITERIA);
        healthyRunner().run(session);
        assertThat(session.sources()).isNotEmpty()
            .allSatisfy(source -> assertThat(source.health()).isEqualTo(SourceHealth.OK));
    }

    @Test void aSearchBuiltOnlyFromDemoProvidersIsFlaggedAsDemo() {
        var session = new SearchSession("s1", CRITERIA);
        healthyRunner().run(session);
        assertThat(session.demo()).isTrue();
    }

    @Test void aFailingCarSourceDegradesTheSourceWithoutBreakingTheSearch() {
        CarRentalProviderPort broken = criteria -> { throw new IllegalStateException("locadora fora do ar"); };
        var session = new SearchSession("s1", CRITERIA_WITH_CAR);
        runner(new DemoFlightProvider(), new DemoLodgingProvider(), broken).run(session);
        assertThat(session.status()).isEqualTo(SearchStatus.PARCIAL);
        assertThat(session.sources()).anySatisfy(source ->
            assertThat(source.health()).isEqualTo(SourceHealth.DEGRADADO));
    }

    @Test void aCarSourceIsNotEvenConsultedWhenTheUserDoesNotWantACar() {
        CarRentalProviderPort broken = criteria -> { throw new IllegalStateException("nunca deveria ser chamado"); };
        var session = new SearchSession("s1", CRITERIA);
        runner(new DemoFlightProvider(), new DemoLodgingProvider(), broken).run(session);
        assertThat(session.status()).isEqualTo(SearchStatus.PRONTO);
        assertThat(session.sources()).extracting(SourceStatus::source).doesNotContain("Carros");
    }

    @Test void aFailingEssentialSourceEndsInErrorInsteadOfThrowing() {
        LodgingProviderPort broken = criteria -> { throw new IllegalStateException("hotelaria fora do ar"); };
        var session = new SearchSession("s1", CRITERIA);
        runner(new DemoFlightProvider(), broken, new DemoCarRentalProvider()).run(session);
        assertThat(session.status()).isEqualTo(SearchStatus.ERRO);
        assertThat(session.packages()).isEmpty();
    }

    @Test void aSourceThatReturnsNothingIsDegradedNotSilentlyIgnored() {
        FlightProviderPort empty = criteria -> List.of();
        var session = new SearchSession("s1", CRITERIA);
        runner(empty, new DemoLodgingProvider(), new DemoCarRentalProvider()).run(session);
        assertThat(session.sources()).anySatisfy(source ->
            assertThat(source.health()).isEqualTo(SourceHealth.DEGRADADO));
    }
}
