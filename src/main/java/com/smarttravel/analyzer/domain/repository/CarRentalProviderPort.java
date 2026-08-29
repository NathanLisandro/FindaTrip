package com.smarttravel.analyzer.domain.repository;
import com.smarttravel.analyzer.domain.model.carrental.CarRentalOffer;
import com.smarttravel.analyzer.domain.model.shared.SearchCriteria;
import java.util.List;
public interface CarRentalProviderPort {
    List<CarRentalOffer> searchCars(SearchCriteria criteria);
    default boolean isDemo() { return false; }
}
