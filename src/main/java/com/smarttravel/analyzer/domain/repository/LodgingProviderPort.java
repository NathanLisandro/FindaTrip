package com.smarttravel.analyzer.domain.repository;
import com.smarttravel.analyzer.domain.model.lodging.LodgingOffer;
import com.smarttravel.analyzer.domain.model.shared.SearchCriteria;
import java.util.List;
public interface LodgingProviderPort { List<LodgingOffer> searchLodging(SearchCriteria criteria); }
