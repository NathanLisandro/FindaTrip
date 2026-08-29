package com.smarttravel.analyzer.application.dto;

import java.math.BigDecimal;

/** Um tipo de hospedagem disponivel na busca, com quantos pacotes e a partir de quanto. */
public record StayTypeDTO(String type, String label, int packages, BigDecimal cheapest) {}
