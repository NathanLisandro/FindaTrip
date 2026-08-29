package com.smarttravel.analyzer.application.service;

import com.smarttravel.analyzer.application.search.SearchSession;
import com.smarttravel.analyzer.domain.model.search.*;
import com.smarttravel.analyzer.domain.model.shared.SearchCriteria;
import com.smarttravel.analyzer.domain.repository.*;
import com.smarttravel.analyzer.domain.service.PackageAssemblerDomainService;
import com.smarttravel.analyzer.domain.service.PackageBundlerDomainService;
import java.util.List;
import java.util.function.Function;
import org.springframework.stereotype.Service;

@Service
public class SearchRunner {

    private final FlightProviderPort flights;
    private final LodgingProviderPort lodgings;
    private final CarRentalProviderPort cars;
    private final PackageAssemblerDomainService assembler;
    private final PackageBundlerDomainService bundler;
    private final DateFlexibilityService dateFlexibility;

    public SearchRunner(FlightProviderPort flights, LodgingProviderPort lodgings, CarRentalProviderPort cars,
                        PackageAssemblerDomainService assembler, PackageBundlerDomainService bundler,
                        DateFlexibilityService dateFlexibility) {
        this.flights = flights;
        this.lodgings = lodgings;
        this.cars = cars;
        this.assembler = assembler;
        this.bundler = bundler;
        this.dateFlexibility = dateFlexibility;
    }

    public void run(SearchSession session) { run(session, false); }

    public void run(SearchSession session, boolean flexibleDates) {
        runSearch(session);
        if (flexibleDates && session.status() != SearchStatus.ERRO) {
            session.dateOptions(dateFlexibility.neighbouringDates(session.criteria()));
        }
    }

    private void runSearch(SearchSession session) {
        var criteria = session.criteria();
        session.demo(flights.isDemo() && lodgings.isDemo() && cars.isDemo());

        var flightOffers = collect(session, "Voos", criteria, flights::searchFlights);
        var lodgingOffers = collect(session, "Hospedagem", criteria, lodgings::searchLodging);
        var carOffers = criteria.carRequired()
            ? collect(session, "Carros", criteria, cars::searchCars)
            : List.<com.smarttravel.analyzer.domain.model.carrental.CarRentalOffer>of();

        // Uma fonte degradada nunca derruba a busca: se a locadora falhou, o pacote sai sem carro.
        var effective = criteria.carRequired() && carOffers.isEmpty()
            ? new SearchCriteria(criteria.origin(), criteria.destination(), criteria.departureDate(),
                criteria.returnDate(), criteria.travelers(), criteria.checkedBagRequested(), false)
            : criteria;

        var candidates = assembler.assemble(effective, new TravelOffers(flightOffers, lodgingOffers, carOffers));
        if (candidates.isEmpty()) {
            session.packages(List.of());
            session.status(SearchStatus.ERRO);
            return;
        }

        session.packages(bundler.topRecommendations(candidates));
        boolean degraded = session.sources().stream().anyMatch(source -> source.health() == SourceHealth.DEGRADADO);
        session.status(degraded ? SearchStatus.PARCIAL : SearchStatus.PRONTO);
    }

    private <T> List<T> collect(SearchSession session, String name, SearchCriteria criteria, Function<SearchCriteria, List<T>> call) {
        try {
            var offers = call.apply(criteria);
            if (offers.isEmpty()) {
                session.addSource(SourceStatus.degraded(name, "nenhuma oferta encontrada"));
            } else {
                session.addSource(SourceStatus.ok(name, offers.size()));
            }
            return offers;
        } catch (RuntimeException failure) {
            session.addSource(SourceStatus.degraded(name, failure.getMessage()));
            return List.of();
        }
    }
}
