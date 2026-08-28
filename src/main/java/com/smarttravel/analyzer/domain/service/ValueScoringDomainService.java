package com.smarttravel.analyzer.domain.service;

import com.smarttravel.analyzer.domain.model.carrental.*;
import com.smarttravel.analyzer.domain.model.flight.FlightOffer;
import com.smarttravel.analyzer.domain.model.history.RoutePriceTrend;
import com.smarttravel.analyzer.domain.model.lodging.*;
import com.smarttravel.analyzer.domain.model.shared.*;

public class ValueScoringDomainService {
    public Score calculate(Score price, Score quality, Score convenience) { return Score.weighted(price, .45, quality, .35, convenience, .20); }
    public Score priceScore(Money current, RoutePriceTrend trend) { double pct = 1 - current.ratioTo(trend.mean()); return new Score(70 + pct * 160); }
    public Score calculateBayesianRating(HotelRating rating, double destinationMean, int confidenceWeight) {
        return new Score(((confidenceWeight * destinationMean) + rating.totalRatingPoints()) / (confidenceWeight + rating.reviewCount()) * 10);
    }
    public Score flightConvenience(FlightOffer offer) {
        if (offer.hasAirportChangeConnection()) return new Score(10);
        if (offer.stops() == 0) return new Score(100);
        if (offer.legs().stream().anyMatch(l -> l.hasLongLayover() || l.overnightConnection())) return new Score(35);
        if (offer.legs().stream().anyMatch(l -> l.hasSmartLayover())) return new Score(80);
        return new Score(60);
    }
    public Score lodgingConvenience(LodgingOffer offer) {
        double score = 60;
        if (offer.has(Amenity.BREAKFAST_INCLUDED)) score += 10;
        if (offer.has(Amenity.FREE_FLEXIBLE_CANCELLATION)) score += 15;
        if (offer.has(Amenity.WALKABLE_ATTRACTIONS) || offer.distanceToAttractionsKm() <= 1.5) score += 15;
        return new Score(score);
    }
    public Score carConvenience(CarRentalOffer offer) { return new Score(offer.pickupMode() == PickupMode.IN_TERMINAL ? 90 : 75); }
}
