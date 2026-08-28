package com.smarttravel.analyzer.domain.service;

import com.smarttravel.analyzer.domain.model.carrental.CarRentalOffer;
import com.smarttravel.analyzer.domain.model.flight.FlightOffer;
import com.smarttravel.analyzer.domain.model.lodging.LodgingOffer;
import com.smarttravel.analyzer.domain.model.shared.NormalizedPrice;

public class CostNormalizerDomainService {
    public NormalizedPrice normalizeFlight(FlightOffer offer, boolean checkedBagRequested) { return offer.normalize(checkedBagRequested); }
    public NormalizedPrice normalizeLodging(LodgingOffer offer) { return offer.normalize(); }
    public NormalizedPrice normalizeCarRental(CarRentalOffer offer) { return offer.normalizeWithFullInsurance(); }
}
