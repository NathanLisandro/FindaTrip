package com.smarttravel.analyzer.infrastructure.adapter.scraper;

import com.smarttravel.analyzer.domain.exception.DomainException;
import com.smarttravel.analyzer.domain.model.scraper.SiteConfig;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

@Component
public class SiteConfigLoader {

    public Map<String, SiteConfig> load(Path directory) {
        try (Stream<Path> files = Files.list(directory)) {
            var configs = new LinkedHashMap<String, SiteConfig>();
            files.filter(path -> path.toString().endsWith(".yml")).sorted().forEach(path -> {
                var config = parse(path);
                configs.put(config.name(), config);
            });
            return Map.copyOf(configs);
        } catch (IOException failure) {
            throw new DomainException("Nao foi possivel ler os scrapers em " + directory + ": " + failure.getMessage());
        }
    }

    /** SnakeYAML resolve `2026-08-29` como java.util.Date; a data e sempre UTC, nao a hora local. */
    private LocalDate asLocalDate(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Date date) return date.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
        return LocalDate.parse(raw.toString());
    }

    @SuppressWarnings("unchecked")
    private SiteConfig parse(Path path) {
        try (var input = Files.newInputStream(path)) {
            Map<String, Object> raw = new Yaml().load(input);
            var meta = (Map<String, Object>) raw.getOrDefault("_meta", Map.of());
            var verifiedOn = asLocalDate(meta.get("verificado_em"));
            return new SiteConfig(
                (String) raw.get("nome"),
                (String) raw.get("url_busca"),
                (String) raw.get("espera"),
                (Map<String, String>) raw.getOrDefault("seletores", Map.of()),
                ((Number) raw.getOrDefault("rate_limit_ms", 4000)).intValue(),
                verifiedOn);
        } catch (IOException failure) {
            throw new DomainException("Nao foi possivel ler " + path + ": " + failure.getMessage());
        }
    }
}
