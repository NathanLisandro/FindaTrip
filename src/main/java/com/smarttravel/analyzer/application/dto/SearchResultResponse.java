package com.smarttravel.analyzer.application.dto;

import com.smarttravel.analyzer.domain.model.search.SearchStatus;
import java.util.List;

public record SearchResultResponse(String searchId, SearchStatus status, boolean demo,
                                   List<SourceStatusDTO> sources, List<PackageDTO> packages,
                                   List<NeighborhoodDTO> neighborhoods, List<DateOptionDTO> dateOptions,
                                   int totalMatching, List<StayTypeDTO> stayTypes) {}
