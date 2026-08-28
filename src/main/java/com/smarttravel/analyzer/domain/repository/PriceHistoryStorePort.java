package com.smarttravel.analyzer.domain.repository;
import com.smarttravel.analyzer.domain.model.history.PriceHistoryPoint;
import java.util.List;
public interface PriceHistoryStorePort { List<PriceHistoryPoint> findByMarketKey(String marketKey); }
