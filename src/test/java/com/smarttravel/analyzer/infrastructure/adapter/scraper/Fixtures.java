package com.smarttravel.analyzer.infrastructure.adapter.scraper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

/** Le o HTML real gravado na sondagem. Nenhum teste toca a rede. */
final class Fixtures {
    private Fixtures() {}

    static String read(String name) {
        var path = Path.of("src/test/resources/fixtures", name + ".html.gz");
        try (var gzip = new GZIPInputStream(Files.newInputStream(path))) {
            return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException("Fixture ausente: " + path, failure);
        }
    }
}
