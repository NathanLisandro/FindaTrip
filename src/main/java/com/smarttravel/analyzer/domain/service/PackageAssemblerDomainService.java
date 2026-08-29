package com.smarttravel.analyzer.domain.service;

import com.smarttravel.analyzer.domain.model.carrental.CarRentalOffer;
import com.smarttravel.analyzer.domain.model.flight.FlightOffer;
import com.smarttravel.analyzer.domain.model.history.PriceHistoryPoint;
import com.smarttravel.analyzer.domain.model.history.RoutePriceTrend;
import com.smarttravel.analyzer.domain.model.lodging.LodgingOffer;
import com.smarttravel.analyzer.domain.model.packagebundle.PackagePart;
import com.smarttravel.analyzer.domain.model.packagebundle.TravelPackage;
import com.smarttravel.analyzer.domain.model.search.TravelOffers;
import com.smarttravel.analyzer.domain.model.shared.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PackageAssemblerDomainService {

    public static final int MAX_PER_DIMENSION = 5;
    private static final double DESTINATION_MEAN_RATING = 8.0;
    private static final int RATING_CONFIDENCE_WEIGHT = 100;

    private final CostNormalizerDomainService normalizer;
    private final ValueScoringDomainService scoring;
    private final PriceTrendDomainService trends;

    public PackageAssemblerDomainService(CostNormalizerDomainService normalizer, ValueScoringDomainService scoring, PriceTrendDomainService trends) {
        this.normalizer = normalizer;
        this.scoring = scoring;
        this.trends = trends;
    }

    public List<TravelPackage> assemble(SearchCriteria criteria, TravelOffers offers) {
        var flights = cheapest(offers.flights().stream()
            .map(offer -> new PackagePart<>(offer, normalizer.normalizeFlight(offer, criteria.checkedBagRequested()))).toList());
        var lodgings = cheapest(offers.lodgings().stream()
            .map(offer -> new PackagePart<>(offer, normalizer.normalizeLodging(offer))).toList());
        var cars = criteria.carRequired()
            ? cheapest(offers.cars().stream().map(offer -> new PackagePart<>(offer, normalizer.normalizeCarRental(offer))).toList())
            : List.<PackagePart<CarRentalOffer>>of();

        if (flights.isEmpty() || lodgings.isEmpty()) return List.of();
        if (criteria.carRequired() && cars.isEmpty()) return List.of();

        var combinations = combine(flights, lodgings, cars);
        if (combinations.isEmpty()) return List.of();

        var trend = trendOver(criteria, combinations);
        return combinations.stream().map(combination -> score(combination, trend)).toList();
    }

    private static <T> List<PackagePart<T>> cheapest(List<PackagePart<T>> parts) {
        return parts.stream()
            .sorted(Comparator.comparing(part -> part.price().totalPrice()))
            .limit(MAX_PER_DIMENSION)
            .toList();
    }

    private static List<Combination> combine(List<PackagePart<FlightOffer>> flights,
                                             List<PackagePart<LodgingOffer>> lodgings,
                                             List<PackagePart<CarRentalOffer>> cars) {
        var combinations = new ArrayList<Combination>();
        for (var flight : flights) {
            for (var lodging : lodgings) {
                if (cars.isEmpty()) {
                    combinations.add(new Combination(flight, lodging, null));
                } else {
                    for (var car : cars) combinations.add(new Combination(flight, lodging, car));
                }
            }
        }
        return combinations;
    }

    private RoutePriceTrend trendOver(SearchCriteria criteria, List<Combination> combinations) {
        var key = criteria.origin() + "-" + criteria.destination();
        var points = combinations.stream()
            .map(combination -> new PriceHistoryPoint(key, criteria.departureDate(), combination.total()))
            .toList();
        return trends.calculateTrend(key, points);
    }

    private TravelPackage score(Combination combination, RoutePriceTrend trend) {
        Money total = combination.total();
        Score price = scoring.priceScore(total, trend);
        Score quality = scoring.calculateBayesianRating(combination.lodging().offer().rating(), DESTINATION_MEAN_RATING, RATING_CONFIDENCE_WEIGHT);
        Score convenience = combination.convenience(scoring);
        return new TravelPackage(combination.id(), total, scoring.calculate(price, quality, convenience),
            quality, convenience, null, combination.flight(), combination.lodging(), combination.car());
    }

    private record Combination(PackagePart<FlightOffer> flight, PackagePart<LodgingOffer> lodging, PackagePart<CarRentalOffer> car) {

        Money total() {
            Money sum = flight.price().totalPrice().add(lodging.price().totalPrice());
            return car == null ? sum : sum.add(car.price().totalPrice());
        }

        String id() {
            var base = flight.offer().id() + "|" + lodging.offer().id();
            return car == null ? base : base + "|" + car.offer().id();
        }

        Score convenience(ValueScoringDomainService scoring) {
            double flightScore = scoring.flightConvenience(flight.offer()).value();
            double lodgingScore = scoring.lodgingConvenience(lodging.offer()).value();
            if (car == null) return new Score((flightScore + lodgingScore) / 2);
            return new Score((flightScore + lodgingScore + scoring.carConvenience(car.offer()).value()) / 3);
        }
    }
}
