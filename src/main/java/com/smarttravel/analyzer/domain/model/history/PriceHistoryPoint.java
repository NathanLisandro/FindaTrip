package com.smarttravel.analyzer.domain.model.history;

import com.smarttravel.analyzer.domain.model.shared.Money;
import java.time.LocalDate;

public record PriceHistoryPoint(String marketKey, LocalDate observedOn, Money normalizedPrice) {}
