package com.smarttravel.analyzer.application.dto;

import com.smarttravel.analyzer.domain.model.search.SourceHealth;

public record SourceStatusDTO(String source, SourceHealth health, int offers, String message, boolean demo) {}
