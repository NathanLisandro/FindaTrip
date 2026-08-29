package com.smarttravel.analyzer.application.service;

import com.smarttravel.analyzer.application.search.DateOption;
import com.smarttravel.analyzer.domain.model.packagebundle.TravelPackage;
import com.smarttravel.analyzer.domain.model.search.TravelOffers;
import com.smarttravel.analyzer.domain.model.shared.*;
import com.smarttravel.analyzer.domain.repository.*;
import com.smarttravel.analyzer.domain.service.PackageAssemblerDomainService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class DateFlexibilityService {

    private static final int WINDOW = 3;

    private final FlightProviderPort flights;
    private final LodgingProviderPort lodgings;
    private final CarRentalProviderPort cars;
    private final PackageAssemblerDomainService assembler;

    public DateFlexibilityService(FlightProviderPort flights, LodgingProviderPort lodgings,
                                  CarRentalProviderPort cars, PackageAssemblerDomainService assembler) {
        this.flights = flights;
        this.lodgings = lodgings;
        this.cars = cars;
        this.assembler = assembler;
    }

    public List<DateOption> neighbouringDates(SearchCriteria criteria) {
        var reference = cheapestTotal(criteria);
        var options = new ArrayList<DateOption>();
        for (int offset = -WINDOW; offset <= WINDOW; offset++) {
            if (offset == 0) continue;
            var departure = criteria.departureDate().plusDays(offset);
            if (departure.isBefore(LocalDate.now())) continue;
            var shifted = new SearchCriteria(criteria.origin(), criteria.destination(), departure,
                criteria.returnDate().plusDays(offset), criteria.travelers(),
                criteria.checkedBagRequested(), criteria.carRequired());
            cheapestTotal(shifted).ifPresent(total ->
                options.add(new DateOption(shifted.departureDate(), shifted.returnDate(), total, difference(reference, total))));
        }
        options.sort(Comparator.comparing(DateOption::departureDate));
        return List.copyOf(options);
    }

    private Optional<Money> cheapestTotal(SearchCriteria criteria) {
        try {
            var offers = new TravelOffers(flights.searchFlights(criteria), lodgings.searchLodging(criteria),
                criteria.carRequired() ? cars.searchCars(criteria) : List.of());
            return assembler.assemble(criteria, offers).stream()
                .map(TravelPackage::totalPrice)
                .min(Comparator.naturalOrder());
        } catch (RuntimeException failure) {
            return Optional.empty();
        }
    }

    private static Money difference(Optional<Money> reference, Money total) {
        if (reference.isEmpty()) return new Money(BigDecimal.ZERO, total.currency());
        var base = reference.get();
        return base.compareTo(total) >= 0 ? base.subtract(total) : new Money(BigDecimal.ZERO, total.currency());
    }
}
