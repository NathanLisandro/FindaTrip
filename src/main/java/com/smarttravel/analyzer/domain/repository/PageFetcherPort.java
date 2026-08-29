package com.smarttravel.analyzer.domain.repository;

import java.time.Duration;

public interface PageFetcherPort {

    /** HTML ja renderizado. waitForSelector pode ser null; timeout vale para a pagina inteira. */
    String fetch(String url, String waitForSelector, Duration timeout);

    /**
     * Entra por outra pagina do site e so entao navega ate a busca, pelo lado do cliente.
     * A Decolar responde 403 a quem pede a URL de resultados direto, mas serve a mesma pagina
     * para quem chega nela navegando — que e o que um usuario de verdade faz.
     */
    default String fetchAfterVisiting(String entryUrl, String url, String waitForSelector, Duration timeout) {
        return fetch(url, waitForSelector, timeout);
    }
}
