package com.smarttravel.analyzer.domain.model.search;

public record SourceStatus(String source, SourceHealth health, int offers, String message) {
    public static SourceStatus ok(String source, int offers) { return new SourceStatus(source, SourceHealth.OK, offers, null); }
    public static SourceStatus degraded(String source, String message) { return new SourceStatus(source, SourceHealth.DEGRADADO, 0, message); }
}
