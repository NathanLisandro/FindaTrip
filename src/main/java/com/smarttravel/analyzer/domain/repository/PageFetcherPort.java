package com.smarttravel.analyzer.domain.repository;

import java.time.Duration;

public interface PageFetcherPort {
    /** HTML ja renderizado. waitForSelector pode ser null; timeout vale para a pagina inteira. */
    String fetch(String url, String waitForSelector, Duration timeout);
}
