package com.smarttravel.analyzer.domain.service;

import com.smarttravel.analyzer.domain.model.carrental.*;
import com.smarttravel.analyzer.domain.model.flight.*;
import com.smarttravel.analyzer.domain.model.lodging.*;
import com.smarttravel.analyzer.domain.model.packagebundle.TravelPackage;
import com.smarttravel.analyzer.domain.model.search.TravelOffers;
import com.smarttravel.analyzer.domain.model.shared.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PackageAssemblerDomainServiceTest {

    private final PackageAssemblerDomainService assembler = new PackageAssemblerDomainService(
        new CostNormalizerDomainService(), new ValueScoringDomainService(), new PriceTrendDomainService());

    private static SearchCriteria criteria(boolean carRequired) {
        return new SearchCriteria("CWB", "REC", LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 13), 2, true, carRequired);
    }

    private static FlightOffer flight(String id, String fare) {
        return new FlightOffer(id, new Airline("G3", "GOL"), Money.brl(fare), Money.brl("90.00"), Money.brl("60.00"),
            List.of(new FlightLeg(new Location("CWB", "Curitiba", "BR"), new Location("REC", "Recife", "BR"), null, false, false)), .9,
            java.time.LocalTime.of(8, 0), java.time.LocalTime.of(11, 30), java.time.Duration.ofMinutes(210));
    }

    /** O segundo argumento e a diaria; o record guarda o total da estadia. */
    private static LodgingOffer lodging(String id, String nightly) {
        return new LodgingOffer(id, "Hotel " + id, "Boa Viagem", Money.brl(nightly).multiply(3), 3, Money.brl("30.00"), Money.brl("20.00"),
            Money.brl("0.00"), new HotelRating(8.5, 500), Set.of(Amenity.BREAKFAST_INCLUDED), 1.0, "Booking.com", "https://www.booking.com/hotel/br/teste.html", null, StayType.HOTEL);
    }

    private static CarRentalOffer car(String id, String daily) {
        return new CarRentalOffer(id, "Movida", CarCategory.COMPACT, Money.brl(daily), 3,
            InsuranceCoverageType.BASIC_CDW_TP, Money.brl("25.00"), Money.brl("40.00"), true, PickupMode.IN_TERMINAL, .9);
    }

    @Test void buildsOnePackageForEveryFlightAndLodgingCombinationWhenNoCarIsRequired() {
        var offers = new TravelOffers(List.of(flight("f1", "800.00"), flight("f2", "900.00")),
                                      List.of(lodging("l1", "200.00"), lodging("l2", "250.00")), List.of());
        assertThat(assembler.assemble(criteria(false), offers)).hasSize(4);
    }

    @Test void packagesWithoutCarCarryNoCarPart() {
        var offers = new TravelOffers(List.of(flight("f1", "800.00")), List.of(lodging("l1", "200.00")), List.of(car("c1", "90.00")));
        assertThat(assembler.assemble(criteria(false), offers)).allSatisfy(p -> assertThat(p.hasCar()).isFalse());
    }

    @Test void includesTheCarInEveryCombinationWhenTheUserRequiresOne() {
        var offers = new TravelOffers(List.of(flight("f1", "800.00")), List.of(lodging("l1", "200.00")),
                                      List.of(car("c1", "90.00"), car("c2", "110.00")));
        var packages = assembler.assemble(criteria(true), offers);
        assertThat(packages).hasSize(2).allSatisfy(p -> assertThat(p.hasCar()).isTrue());
    }

    @Test void totalPriceIsTheSumOfNormalizedCostsNotAdvertisedPrices() {
        var offers = new TravelOffers(List.of(flight("f1", "800.00")), List.of(lodging("l1", "200.00")), List.of());
        var only = assembler.assemble(criteria(false), offers).getFirst();
        assertThat(only.totalPrice().amount()).isEqualByComparingTo("1600.00");
        assertThat(only.advertisedPrice().amount()).isEqualByComparingTo("1400.00");
    }

    @Test void keepsManyStaysSoTheNeighborhoodListDoesNotCollapse() {
        // Cortar a hospedagem em 5 fazia Florianopolis inteira virar dois bairros na tela.
        // Voo multiplica pouco a variedade; hospedagem e o que o usuario escolhe por regiao.
        var flights = List.of(flight("f1", "800.00"), flight("f2", "810.00"));
        var lodgings = new java.util.ArrayList<LodgingOffer>();
        for (int i = 0; i < 20; i++) lodgings.add(lodging("l" + i, String.valueOf(200 + i * 10) + ".00"));
        var packages = assembler.assemble(criteria(false), new TravelOffers(flights, lodgings, List.of()));
        var distintas = packages.stream().map(p -> p.lodging().offer().id()).collect(java.util.stream.Collectors.toSet());
        assertThat(distintas).hasSize(20);
    }

    @Test void stillCapsTheFlightsSoTheCombinationDoesNotExplode() {
        var flights = List.of(flight("f1", "800.00"), flight("f2", "810.00"), flight("f3", "820.00"),
                              flight("f4", "830.00"), flight("f5", "840.00"), flight("f6", "850.00"));
        var lodgings = List.of(lodging("l1", "200.00"), lodging("l2", "210.00"), lodging("l3", "220.00"),
                               lodging("l4", "230.00"), lodging("l5", "240.00"), lodging("l6", "250.00"));
        var packages = assembler.assemble(criteria(false), new TravelOffers(flights, lodgings, List.of()));
        assertThat(packages).noneSatisfy(p -> assertThat(p.id()).contains("f6"));
    }

    @Test void theCheapestPackageScoresHigherOnPriceThanTheMostExpensiveOne() {
        var offers = new TravelOffers(List.of(flight("f1", "800.00"), flight("f2", "2000.00")),
                                      List.of(lodging("l1", "200.00")), List.of());
        var packages = assembler.assemble(criteria(false), offers);
        var cheapest = packages.stream().min(java.util.Comparator.comparing(TravelPackage::totalPrice)).orElseThrow();
        var priciest = packages.stream().max(java.util.Comparator.comparing(TravelPackage::totalPrice)).orElseThrow();
        assertThat(cheapest.valueScore().value()).isGreaterThan(priciest.valueScore().value());
    }

    @Test void returnsEmptyWhenAnEssentialDimensionHasNoOffers() {
        assertThat(assembler.assemble(criteria(false), new TravelOffers(List.of(), List.of(lodging("l1", "200.00")), List.of()))).isEmpty();
    }

    @Test void returnsEmptyWhenTheUserRequiresACarAndThereIsNone() {
        var offers = new TravelOffers(List.of(flight("f1", "800.00")), List.of(lodging("l1", "200.00")), List.of());
        assertThat(assembler.assemble(criteria(true), offers)).isEmpty();
    }
}
