package com.smarttravel.analyzer.domain.service;

import com.smarttravel.analyzer.domain.model.history.*;
import java.util.List;

public class PriceTrendDomainService {
    public RoutePriceTrend calculateTrend(String marketKey, List<PriceHistoryPoint> points) { return RoutePriceTrend.from(marketKey, points); }
}
