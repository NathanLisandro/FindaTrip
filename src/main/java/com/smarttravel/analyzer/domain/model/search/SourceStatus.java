package com.smarttravel.analyzer.domain.model.search;

/**
 * Estado de uma fonte numa busca.
 * `demo` diz se o dado daquela fonte e real ou simulado: com voo raspado de verdade e
 * carro ainda de demonstracao, um aviso global mentiria nos dois sentidos.
 */
public record SourceStatus(String source, SourceHealth health, int offers, String message, boolean demo) {

    public static SourceStatus ok(String source, int offers, boolean demo) {
        return new SourceStatus(source, SourceHealth.OK, offers, null, demo);
    }

    public static SourceStatus degraded(String source, String message, boolean demo) {
        return new SourceStatus(source, SourceHealth.DEGRADADO, 0, message, demo);
    }
}
