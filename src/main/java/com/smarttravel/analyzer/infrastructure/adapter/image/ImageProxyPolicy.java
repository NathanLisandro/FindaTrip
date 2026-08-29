package com.smarttravel.analyzer.infrastructure.adapter.image;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * Quais imagens o proxy aceita buscar.
 * Sem esta lista, um endpoint que baixa "a URL que voce mandar" vira porta de entrada
 * para a rede interna de quem hospeda a aplicacao.
 */
public final class ImageProxyPolicy {

    private static final List<String> HOSTS = List.of("bstatic.com", "muscache.com");

    private ImageProxyPolicy() {}

    public static boolean allows(String url) {
        if (url == null || url.isBlank()) return false;
        try {
            var uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme())) return false;
            var host = uri.getHost();
            if (host == null) return false;
            var lower = host.toLowerCase(Locale.ROOT);
            // "termina com .bstatic.com" e nao "contem": cf.bstatic.com.malicioso.net nao passa.
            return HOSTS.stream().anyMatch(ok -> lower.equals(ok) || lower.endsWith("." + ok));
        } catch (IllegalArgumentException malformada) {
            return false;
        }
    }
}
