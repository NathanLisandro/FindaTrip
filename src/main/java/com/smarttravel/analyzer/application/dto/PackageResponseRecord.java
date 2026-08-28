package com.smarttravel.analyzer.application.dto;

import com.smarttravel.analyzer.domain.model.packagebundle.PackageBundleType;
import java.math.BigDecimal;

public record PackageResponseRecord(String id, BigDecimal totalPrice, String currency, double valueScore, PackageBundleType recommendationType) {}
