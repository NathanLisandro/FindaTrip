package com.smarttravel.analyzer.infrastructure.adapter.scraper;

import com.smarttravel.analyzer.domain.exception.DomainException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.*;

class SiteConfigLoaderTest {

    private static final String YAML = """
        nome: booking
        url_busca: "https://www.booking.com/searchresults.html?ss={cidade}&checkin={entrada}"
        espera: "[data-testid='property-card']"
        rate_limit_ms: 4000
        seletores:
          card: "[data-testid='property-card']"
          nome: "[data-testid='title']"
        _meta:
          verificado_em: 2026-08-29
        """;

    private Path write(@TempDir Path dir, String file, String content) throws Exception {
        Files.writeString(dir.resolve(file), content);
        return dir;
    }

    @Test void loadsEveryConfigInTheDirectoryKeyedByName(@TempDir Path dir) throws Exception {
        var loaded = new SiteConfigLoader().load(write(dir, "booking.yml", YAML));
        assertThat(loaded).containsOnlyKeys("booking");
        assertThat(loaded.get("booking").rateLimitMs()).isEqualTo(4000);
        assertThat(loaded.get("booking").selector("nome")).isEqualTo("[data-testid='title']");
    }

    @Test void fillsPlaceholdersAndEncodesEachValue(@TempDir Path dir) throws Exception {
        var config = new SiteConfigLoader().load(write(dir, "booking.yml", YAML)).get("booking");
        assertThat(config.url(Map.of("cidade", "Florianópolis", "entrada", "2026-11-07")))
            .contains("ss=Florian%C3%B3polis").contains("checkin=2026-11-07");
    }

    @Test void aMissingPlaceholderFailsLoudlyInsteadOfLeakingIntoTheUrl(@TempDir Path dir) throws Exception {
        var config = new SiteConfigLoader().load(write(dir, "booking.yml", YAML)).get("booking");
        assertThatThrownBy(() -> config.url(Map.of("cidade", "Floripa")))
            .isInstanceOf(DomainException.class).hasMessageContaining("entrada");
    }

    @Test void anUnknownSelectorNamesTheKeyThatIsMissing(@TempDir Path dir) throws Exception {
        var config = new SiteConfigLoader().load(write(dir, "booking.yml", YAML)).get("booking");
        assertThatThrownBy(() -> config.selector("preco"))
            .isInstanceOf(DomainException.class).hasMessageContaining("preco");
    }

    @Test void aConfigOlderThanNinetyDaysIsStale(@TempDir Path dir) throws Exception {
        var config = new SiteConfigLoader().load(write(dir, "booking.yml", YAML)).get("booking");
        assertThat(config.isStale(LocalDate.of(2026, 9, 29))).isFalse();
        assertThat(config.isStale(LocalDate.of(2027, 1, 1))).isTrue();
    }

    @Test void everyShippedConfigIsStillWithinItsNinetyDayWindow() {
        var loaded = new SiteConfigLoader().load(Path.of("scrapers"));
        assertThat(loaded).isNotEmpty();
        assertThat(loaded.values()).allSatisfy(config ->
            assertThat(config.isStale(LocalDate.now()))
                .withFailMessage("scrapers/%s.yml passou de 90 dias: reveja os seletores no site e atualize _meta.verificado_em",
                    config.name())
                .isFalse());
    }
}
