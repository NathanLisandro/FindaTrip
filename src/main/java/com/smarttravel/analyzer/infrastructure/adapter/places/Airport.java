package com.smarttravel.analyzer.infrastructure.adapter.places;

/** Um aeroporto brasileiro: o codigo IATA e o nome da cidade que o usuario conhece. */
public record Airport(String code, String city, String name, String search) {}
