# Fontes Reais, React e Docker — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Trocar os dados de demonstração por preço real raspado de Google Voos, Booking, Google Hotels e Airbnb; front em React; Docker para subir.

**Architecture:** Playwright para Java apontando para o Chrome do sistema, atrás de uma porta `PageFetcherPort`. Um scraper por site, seletores em YAML, parser testado contra fixture gravada — nenhum teste toca a rede. Os scrapers implementam as portas que já existem, então domínio e aplicação não mudam.

**Tech Stack:** Java 21, Spring Boot 3.5.5, Playwright Java, Jsoup, SnakeYAML, React + Vite, Docker.

## Global Constraints

- Tudo do `CLAUDE.md` continua valendo. Em especial: `domain/` não importa Spring; portas em `domain/repository/` com sufixo `Port`; dinheiro sempre `Money` em BRL; record imutável validado no construtor compacto.
- **Nenhum teste no `mvn test` padrão toca a rede.** Teste que precisa de internet leva `@Tag("rede")` e fica fora do build.
- **Seletor CSS nunca em código Java** — sempre no YAML de `scrapers/`.
- Falha de fonte vira `SourceStatus DEGRADADO`, nunca exceção que sobe.
- Filtros e resumo por bairro sobre `session.candidates()`, nunca `session.packages()`.
- Comando de teste: `mvn test`. Um só: `mvn test -Dtest=Classe#metodo`.
- Fixtures em `src/test/resources/fixtures/*.html.gz`, lidas com `GZIPInputStream`.

## Fatos apurados na sondagem (não re-descubra)

Chrome do sistema em `/usr/bin/google-chrome`. Node 20 disponível. As quatro fontes abaixo
responderam; Decolar (403) e Hoteis.com (429 + captcha) bloqueiam mesmo com Chrome real.

**Booking** — `div[data-testid=property-card]`, 25 por página. Dentro de cada card:

| dado | `data-testid` | exemplo real |
|---|---|---|
| nome | `title` | `Valentina 24 HORAS` |
| bairro | `address-link` | `Campeche, Florianópolis` → bairro é o que vem antes da vírgula |
| distância | `distance` | `11,7 km do centro` |
| preço | `price-and-discounted-price` | `R$ 1.540 R$ 893` (original e atual) |
| taxas | `taxes-and-charges` | `+R$ 693 em impostos e taxas` ou `Impostos e taxas incluídos` |
| nota | `review-score` | `Com nota 7,7 7,7 Bom 700 avaliações` |
| período | `price-for-x-nights` | `1 semana, 2 adultos` |
| link | `title-link` (atributo `href`) | |
| imagem | `image` (atributo `src`) | |

Café da manhã aparece como o texto `Café da manhã incluído` dentro do card.

**Google Voos** — `li.pIav2d`, 16 por página. Texto de um item, em ordem:
`06:20 | 15:00 | LATAM | 8h 40 min | MGF | FLN | 1 parada | Parada de 5h 55 min | Escala longa | CGH | 142 kg CO2e`.
Há `aria-label` úteis: `Duração total: 8h 40 min.`, `Voo com 1 escala.`. O preço do item aparece
como `R$ 762` com o texto `ida e volta` próximo.

---

### Task 1: Playwright atrás de uma porta

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/com/smarttravel/analyzer/domain/repository/PageFetcherPort.java`
- Create: `src/main/java/com/smarttravel/analyzer/infrastructure/adapter/scraper/PlaywrightPageFetcher.java`
- Test: `src/test/java/com/smarttravel/analyzer/infrastructure/adapter/scraper/PlaywrightPageFetcherIT.java`

**Interfaces:**
- Produces:
  - `PageFetcherPort.fetch(String url, String waitForSelector, Duration timeout)` → `String` (HTML renderizado); lança `PageFetchException` (nova, em `domain/exception/`) quando não consegue
  - `PlaywrightPageFetcher` — `@Component`, um `Browser` por aplicação, um `BrowserContext` por chamada

- [ ] **Step 1: Dependências**

Em `pom.xml`, dentro de `<dependencies>`:

```xml
<dependency><groupId>com.microsoft.playwright</groupId><artifactId>playwright</artifactId><version>1.49.0</version></dependency>
<dependency><groupId>org.jsoup</groupId><artifactId>jsoup</artifactId><version>1.18.3</version></dependency>
<dependency><groupId>org.yaml</groupId><artifactId>snakeyaml</artifactId><version>2.3</version></dependency>
```

E, para que `@Tag("rede")` fique fora do build, configurar o surefire já existente:

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-surefire-plugin</artifactId>
  <version>3.5.2</version>
  <configuration><excludedGroups>rede</excludedGroups></configuration>
</plugin>
```

Run: `mvn -q test` — deve continuar com 81 testes verdes.

- [ ] **Step 2: Criar a porta e a exceção**

`domain/exception/PageFetchException.java`:

```java
package com.smarttravel.analyzer.domain.exception;

public class PageFetchException extends RuntimeException {
    public PageFetchException(String message, Throwable cause) { super(message, cause); }
}
```

`domain/repository/PageFetcherPort.java`:

```java
package com.smarttravel.analyzer.domain.repository;

import java.time.Duration;

public interface PageFetcherPort {
    /** HTML ja renderizado. waitForSelector pode ser null; timeout vale para a pagina inteira. */
    String fetch(String url, String waitForSelector, Duration timeout);
}
```

- [ ] **Step 3: Implementar o fetcher**

`infrastructure/adapter/scraper/PlaywrightPageFetcher.java`:

```java
package com.smarttravel.analyzer.infrastructure.adapter.scraper;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitUntilState;
import com.smarttravel.analyzer.domain.exception.PageFetchException;
import com.smarttravel.analyzer.domain.repository.PageFetcherPort;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PlaywrightPageFetcher implements PageFetcherPort {

    private static final String USER_AGENT =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36";

    private Playwright playwright;
    private Browser browser;

    @Override
    public synchronized String fetch(String url, String waitForSelector, Duration timeout) {
        try {
            var context = browser().newContext(new Browser.NewContextOptions()
                .setLocale("pt-BR").setTimezoneId("America/Sao_Paulo")
                .setViewportSize(1440, 900).setUserAgent(USER_AGENT));
            try (context) {
                var page = context.newPage();
                page.navigate(url, new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(timeout.toMillis()));
                if (waitForSelector != null && !waitForSelector.isBlank()) {
                    try {
                        page.waitForSelector(waitForSelector, new Page.WaitForSelectorOptions().setTimeout(timeout.toMillis()));
                    } catch (PlaywrightException ignored) {
                        // O seletor pode nao aparecer e ainda assim haver resultado util na pagina.
                        // Quem decide se o HTML serve e o parser, nao o fetcher.
                    }
                }
                page.waitForTimeout(4000);
                page.mouse().wheel(0, 2500);
                page.waitForTimeout(2500);
                return page.content();
            }
        } catch (PlaywrightException failure) {
            throw new PageFetchException("Nao foi possivel carregar " + url, failure);
        }
    }

    private Browser browser() {
        if (browser == null) {
            playwright = Playwright.create();
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setChannel("chrome").setHeadless(true)
                .setArgs(List.of("--disable-blink-features=AutomationControlled", "--no-sandbox")));
        }
        return browser;
    }

    @PreDestroy public void close() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }
}
```

- [ ] **Step 4: Teste de integração, fora do build padrão**

```java
package com.smarttravel.analyzer.infrastructure.adapter.scraper;

import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("rede")
class PlaywrightPageFetcherIT {

    @Test void rendersJavaScriptSoThePriceIsInTheHtml() {
        var fetcher = new PlaywrightPageFetcher();
        try {
            var html = fetcher.fetch("https://www.booking.com/searchresults.html?ss=Florian%C3%B3polis"
                + "&checkin=2026-11-07&checkout=2026-11-14&group_adults=2&selected_currency=BRL",
                "[data-testid='property-card']", Duration.ofSeconds(45));
            assertThat(html).contains("property-card").contains("R$");
        } finally { fetcher.close(); }
    }
}
```

Run: `mvn test` — o IT NÃO deve rodar (está excluído por tag). Confirme que o total continua 81.
Run: `mvn test -Dgroups=rede -Dtest=PlaywrightPageFetcherIT` — este toca a rede e deve passar.

- [ ] **Step 5: Commit**

```bash
git add pom.xml src/main src/test
git commit -m "feat: render pages through a real Chrome behind a port"
```

---

### Task 2: Configuração de site em YAML

**Files:**
- Create: `scrapers/booking.yml`, `scrapers/google-flights.yml`, `scrapers/google-hotels.yml`, `scrapers/airbnb.yml`
- Create: `src/main/java/com/smarttravel/analyzer/domain/model/scraper/SiteConfig.java`
- Create: `src/main/java/com/smarttravel/analyzer/infrastructure/adapter/scraper/SiteConfigLoader.java`
- Test: `src/test/java/com/smarttravel/analyzer/infrastructure/adapter/scraper/SiteConfigLoaderTest.java`

**Interfaces:**
- Produces:
  - `SiteConfig(String name, String searchUrl, String waitFor, Map<String,String> selectors, int rateLimitMs, LocalDate verifiedOn)`
  - `SiteConfig.url(Map<String,String> values)` → `String` — troca `{chave}` pelos valores, **URL-encodando** cada um; lança `DomainException` se sobrar placeholder não preenchido
  - `SiteConfig.selector(String key)` → `String`; lança `DomainException` com o nome da chave se não existir
  - `SiteConfig.isStale(LocalDate today)` → `boolean` — mais de 90 dias desde `verifiedOn`
  - `SiteConfigLoader.load(Path directory)` → `Map<String, SiteConfig>` por nome

- [ ] **Step 1: Escrever o teste falhando**

```java
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
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn test -Dtest=SiteConfigLoaderTest`
Expected: erro de compilação — `SiteConfig` e `SiteConfigLoader` não existem.

- [ ] **Step 3: Implementar SiteConfig**

```java
package com.smarttravel.analyzer.domain.model.scraper;

import com.smarttravel.analyzer.domain.exception.DomainException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.regex.Pattern;

public record SiteConfig(String name, String searchUrl, String waitFor,
                         Map<String, String> selectors, int rateLimitMs, LocalDate verifiedOn) {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z_]+)\\}");
    private static final int MAX_AGE_DAYS = 90;

    public SiteConfig {
        if (name == null || name.isBlank()) throw new DomainException("Site config requires a name");
        if (searchUrl == null || searchUrl.isBlank()) throw new DomainException("Site config " + name + " requires url_busca");
        selectors = Map.copyOf(selectors);
    }

    public String url(Map<String, String> values) {
        var filled = PLACEHOLDER.matcher(searchUrl).replaceAll(match -> {
            var key = match.group(1);
            var value = values.get(key);
            if (value == null) throw new DomainException("Faltou o valor de {" + key + "} para montar a URL de " + name);
            return java.util.regex.Matcher.quoteReplacement(URLEncoder.encode(value, StandardCharsets.UTF_8));
        });
        return filled;
    }

    public String selector(String key) {
        var value = selectors.get(key);
        if (value == null) throw new DomainException("scrapers/" + name + ".yml nao define o seletor '" + key + "'");
        return value;
    }

    public boolean isStale(LocalDate today) {
        return verifiedOn == null || ChronoUnit.DAYS.between(verifiedOn, today) > MAX_AGE_DAYS;
    }
}
```

- [ ] **Step 4: Implementar o loader**

```java
package com.smarttravel.analyzer.infrastructure.adapter.scraper;

import com.smarttravel.analyzer.domain.exception.DomainException;
import com.smarttravel.analyzer.domain.model.scraper.SiteConfig;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
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

    @SuppressWarnings("unchecked")
    private SiteConfig parse(Path path) {
        try (var input = Files.newInputStream(path)) {
            Map<String, Object> raw = new Yaml().load(input);
            var meta = (Map<String, Object>) raw.getOrDefault("_meta", Map.of());
            var verifiedOn = meta.get("verificado_em") == null ? null : LocalDate.parse(meta.get("verificado_em").toString());
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
```

- [ ] **Step 5: Escrever os quatro YAML**

`scrapers/booking.yml` — os seletores vêm da sondagem, não invente:

```yaml
nome: booking
tipo: spa
url_busca: "https://www.booking.com/searchresults.html?ss={cidade}&checkin={entrada}&checkout={saida}&group_adults={adultos}&selected_currency=BRL"
espera: "[data-testid='property-card']"
rate_limit_ms: 5000
seletores:
  card: "[data-testid='property-card']"
  nome: "[data-testid='title']"
  endereco: "[data-testid='address-link']"
  distancia: "[data-testid='distance']"
  preco: "[data-testid='price-and-discounted-price']"
  taxas: "[data-testid='taxes-and-charges']"
  nota: "[data-testid='review-score']"
  periodo: "[data-testid='price-for-x-nights']"
  link: "[data-testid='title-link']"
  imagem: "[data-testid='image']"
_meta:
  verificado_em: 2026-08-29
```

`scrapers/google-flights.yml`:

```yaml
nome: google-flights
tipo: spa
url_busca: "https://www.google.com/travel/flights?q=Flights%20from%20{origem}%20to%20{destino}%20on%20{ida}%20through%20{volta}&curr=BRL&hl=pt-BR"
espera: "li.pIav2d"
rate_limit_ms: 6000
seletores:
  card: "li.pIav2d"
  duracao: "div[aria-label^='Duração total']"
  escalas: "div[aria-label^='Voo com']"
_meta:
  verificado_em: 2026-08-29
```

`scrapers/google-hotels.yml`:

```yaml
nome: google-hotels
tipo: spa
url_busca: "https://www.google.com/travel/search?q=hoteis%20{cidade}&hl=pt-BR&curr=BRL"
espera: "c-wiz"
rate_limit_ms: 6000
seletores:
  card: "c-wiz div[role='listitem'], div[jsname]"
_meta:
  verificado_em: 2026-08-29
```

`scrapers/airbnb.yml`:

```yaml
nome: airbnb
tipo: spa
url_busca: "https://www.airbnb.com.br/s/{cidade}/homes?checkin={entrada}&checkout={saida}&adults={adultos}"
espera: "[itemprop='itemListElement']"
rate_limit_ms: 6000
seletores:
  card: "[itemprop='itemListElement']"
_meta:
  verificado_em: 2026-08-29
```

- [ ] **Step 6: Rodar, ver passar, commitar**

Run: `mvn test -Dtest=SiteConfigLoaderTest` — 6 testes.
Run: `mvn test` — total 87.

```bash
git add scrapers src/main src/test
git commit -m "feat: keep every site selector in YAML instead of Java"
```

---

### Task 3: BookingScraper contra a fixture

O parser é o coração. Ele é escrito contra o HTML real gravado, sem rede.

**Files:**
- Create: `src/test/java/com/smarttravel/analyzer/infrastructure/adapter/scraper/Fixtures.java`
- Create: `src/main/java/com/smarttravel/analyzer/infrastructure/adapter/scraper/BrazilianText.java`
- Create: `src/main/java/com/smarttravel/analyzer/infrastructure/adapter/scraper/BookingScraper.java`
- Test: `src/test/java/com/smarttravel/analyzer/infrastructure/adapter/scraper/BookingScraperTest.java`

**Interfaces:**
- Produces:
  - `Fixtures.read(String name)` → `String` (descompacta `src/test/resources/fixtures/{name}.html.gz`)
  - `BrazilianText.money(String)` → `Optional<Money>` — entende `R$ 1.540`, `R$&nbsp;893`, `+R$ 693 em impostos`
  - `BrazilianText.decimal(String)` → `OptionalDouble` — entende `7,7` e `11,7 km`
  - `BrazilianText.integer(String)` → `OptionalInt` — entende `700 avaliações`, `1.238 avaliações`
  - `BookingScraper implements LodgingProviderPort`, `isDemo()` = `false`
  - `BookingScraper.parse(String html, SearchCriteria criteria)` → `List<LodgingOffer>` — **público, para o teste chamar sem rede**

**Regras de mapeamento, todas verificadas na fixture:**
- Nome: texto de `title`.
- Bairro: `address-link` é `"Campeche, Florianópolis"` → bairro é o trecho antes da primeira vírgula. Quando não há vírgula (`"Florianópolis"`), não há bairro: passe `null` e o record vira `"Não informado"`.
- Preço: `price-and-discounted-price` traz até dois valores; o **último** é o preço atual (`R$ 1.540 R$ 893` → 893). É o total do período inteiro, não a diária.
- `nights` vem da diferença entre as datas do critério. `nightlyRate` = total ÷ noites, arredondado.
- Taxas: `taxes-and-charges` com `+R$ 693` vira `serviceFees`; `"Impostos e taxas incluídos"` vira zero.
- Nota e avaliações: de `review-score`, primeiro decimal e primeiro inteiro.
- Café da manhã: card contém o texto `Café da manhã incluído`.
- Cancelamento grátis: card contém `Cancelamento grátis`.
- Distância: `distance` → `11,7 km do centro` → `11.7`.
- **Card sem preço é descartado** e não derruba a lista.

- [ ] **Step 1: Escrever o teste falhando**

```java
package com.smarttravel.analyzer.infrastructure.adapter.scraper;

import com.smarttravel.analyzer.domain.model.lodging.Amenity;
import com.smarttravel.analyzer.domain.model.shared.*;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BookingScraperTest {

    private static final SearchCriteria CRITERIA =
        new SearchCriteria("MGF", "Florianópolis", LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 14), 2, true, false);

    private static java.util.List<com.smarttravel.analyzer.domain.model.lodging.LodgingOffer> offers() {
        return new BookingScraper(null, null).parse(Fixtures.read("booking-florianopolis"), CRITERIA);
    }

    @Test void readsEveryPropertyCardOnThePage() {
        assertThat(offers()).hasSizeGreaterThan(15);
    }

    @Test void readsTheRealNameOfTheFirstProperty() {
        assertThat(offers().getFirst().name()).isEqualTo("Valentina 24 HORAS");
    }

    @Test void takesTheNeighborhoodFromTheAddressBeforeTheComma() {
        assertThat(offers()).anySatisfy(offer -> assertThat(offer.neighborhood()).isEqualTo("Campeche"));
        assertThat(offers()).anySatisfy(offer -> assertThat(offer.neighborhood()).isEqualTo("Canasvieiras"));
    }

    @Test void aPropertyWithoutANeighborhoodFallsBackToTheReadableLabel() {
        assertThat(offers()).anySatisfy(offer -> assertThat(offer.neighborhood()).isEqualTo("Não informado"));
    }

    @Test void takesTheDiscountedPriceNotTheStruckThroughOne() {
        var valentina = offers().stream().filter(o -> o.name().equals("Valentina 24 HORAS")).findFirst().orElseThrow();
        assertThat(valentina.nightlyRate().multiply(valentina.nights()).amount()).isEqualByComparingTo("893.00");
    }

    @Test void readsTheTaxesBookingAddsOnTopOfTheAdvertisedPrice() {
        var valentina = offers().stream().filter(o -> o.name().equals("Valentina 24 HORAS")).findFirst().orElseThrow();
        assertThat(valentina.serviceFees().amount()).isEqualByComparingTo("693.00");
    }

    @Test void taxesAlreadyIncludedMeanNoExtraFee() {
        var included = offers().stream().filter(o -> o.name().startsWith("Refúgio do Cacupé")).findFirst().orElseThrow();
        assertThat(included.serviceFees().amount()).isEqualByComparingTo("0.00");
    }

    @Test void nightsComeFromTheSearchDatesNotFromThePage() {
        assertThat(offers()).allSatisfy(offer -> assertThat(offer.nights()).isEqualTo(7));
    }

    @Test void readsRatingAndReviewCount() {
        var valentina = offers().stream().filter(o -> o.name().equals("Valentina 24 HORAS")).findFirst().orElseThrow();
        assertThat(valentina.rating().average()).isEqualTo(7.7);
        assertThat(valentina.rating().reviewCount()).isEqualTo(700);
    }

    @Test void readsBreakfastAsAnAmenity() {
        assertThat(offers()).anySatisfy(offer -> assertThat(offer.has(Amenity.BREAKFAST_INCLUDED)).isTrue());
    }

    @Test void everyPriceIsInBrl() {
        assertThat(offers()).allSatisfy(offer -> assertThat(offer.nightlyRate().currency()).isEqualTo(Money.BRL));
    }

    @Test void declaresItselfAsRealDataNotDemo() {
        assertThat(new BookingScraper(null, null).isDemo()).isFalse();
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn test -Dtest=BookingScraperTest`
Expected: erro de compilação — `Fixtures` e `BookingScraper` não existem.

- [ ] **Step 3: Criar o leitor de fixture**

```java
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
```

- [ ] **Step 4: Criar o utilitário de texto brasileiro**

```java
package com.smarttravel.analyzer.infrastructure.adapter.scraper;

import com.smarttravel.analyzer.domain.model.shared.Money;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.regex.Pattern;

/** Numeros como os sites brasileiros escrevem: "R$ 1.540", "7,7", "1.238 avaliacoes". */
public final class BrazilianText {

    private static final Pattern MONEY = Pattern.compile("R\\$\\s*([\\d.]+(?:,\\d{2})?)");
    private static final Pattern DECIMAL = Pattern.compile("(\\d+,\\d+)");
    private static final Pattern INTEGER = Pattern.compile("([\\d.]+)");

    private BrazilianText() {}

    /** O ULTIMO valor da string: o Booking mostra "R$ 1.540 R$ 893", e 893 e o que se paga. */
    public static Optional<Money> money(String text) {
        if (text == null) return Optional.empty();
        var matcher = MONEY.matcher(text.replace(' ', ' '));
        String last = null;
        while (matcher.find()) last = matcher.group(1);
        if (last == null) return Optional.empty();
        return Optional.of(new Money(new BigDecimal(last.replace(".", "").replace(",", ".")), Money.BRL));
    }

    public static Optional<Money> firstMoney(String text) {
        if (text == null) return Optional.empty();
        var matcher = MONEY.matcher(text.replace(' ', ' '));
        if (!matcher.find()) return Optional.empty();
        return Optional.of(new Money(new BigDecimal(matcher.group(1).replace(".", "").replace(",", ".")), Money.BRL));
    }

    public static OptionalDouble decimal(String text) {
        if (text == null) return OptionalDouble.empty();
        var matcher = DECIMAL.matcher(text);
        if (!matcher.find()) return OptionalDouble.empty();
        return OptionalDouble.of(Double.parseDouble(matcher.group(1).replace(",", ".")));
    }

    public static OptionalInt integer(String text) {
        if (text == null) return OptionalInt.empty();
        var matcher = INTEGER.matcher(text.replace(' ', ' '));
        while (matcher.find()) {
            var raw = matcher.group(1).replace(".", "");
            if (!raw.isEmpty() && raw.length() <= 9) return OptionalInt.of(Integer.parseInt(raw));
        }
        return OptionalInt.empty();
    }
}
```

- [ ] **Step 5: Implementar o BookingScraper**

```java
package com.smarttravel.analyzer.infrastructure.adapter.scraper;

import com.smarttravel.analyzer.domain.model.lodging.*;
import com.smarttravel.analyzer.domain.model.scraper.SiteConfig;
import com.smarttravel.analyzer.domain.model.shared.*;
import com.smarttravel.analyzer.domain.repository.LodgingProviderPort;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

public class BookingScraper implements LodgingProviderPort {

    private final com.smarttravel.analyzer.domain.repository.PageFetcherPort fetcher;
    private final SiteConfig config;

    public BookingScraper(com.smarttravel.analyzer.domain.repository.PageFetcherPort fetcher, SiteConfig config) {
        this.fetcher = fetcher;
        this.config = config;
    }

    @Override public boolean isDemo() { return false; }

    @Override public List<LodgingOffer> searchLodging(SearchCriteria criteria) {
        var url = config.url(Map.of(
            "cidade", criteria.destination(),
            "entrada", criteria.departureDate().toString(),
            "saida", criteria.returnDate().toString(),
            "adultos", String.valueOf(criteria.travelers())));
        return parse(fetcher.fetch(url, config.waitFor(), Duration.ofSeconds(45)), criteria);
    }

    /** Publico de proposito: o teste roda o parser contra HTML gravado, sem rede. */
    public List<LodgingOffer> parse(String html, SearchCriteria criteria) {
        int nights = Math.max(1, (int) ChronoUnit.DAYS.between(criteria.departureDate(), criteria.returnDate()));
        var offers = new ArrayList<LodgingOffer>();
        var cards = Jsoup.parse(html).select("[data-testid=property-card]");
        for (int index = 0; index < cards.size(); index++) {
            toOffer(cards.get(index), index, nights).ifPresent(offers::add);
        }
        return List.copyOf(offers);
    }

    private Optional<LodgingOffer> toOffer(Element card, int index, int nights) {
        var total = BrazilianText.money(text(card, "[data-testid=price-and-discounted-price]"));
        if (total.isEmpty()) return Optional.empty();   // card sem preco nao derruba a lista

        var name = text(card, "[data-testid=title]");
        if (name == null || name.isBlank()) return Optional.empty();

        var score = text(card, "[data-testid=review-score]");
        var rating = new HotelRating(BrazilianText.decimal(score).orElse(0), BrazilianText.integer(score).orElse(0));

        var body = card.text();
        var amenities = EnumSet.noneOf(Amenity.class);
        if (body.contains("Café da manhã incluído")) amenities.add(Amenity.BREAKFAST_INCLUDED);
        if (body.contains("Cancelamento grátis")) amenities.add(Amenity.FREE_FLEXIBLE_CANCELLATION);

        var nightly = new Money(total.get().amount().divide(BigDecimal.valueOf(nights), 2, RoundingMode.HALF_UP), Money.BRL);
        var taxes = BrazilianText.money(text(card, "[data-testid=taxes-and-charges]")).orElse(new Money(BigDecimal.ZERO, Money.BRL));
        var zero = new Money(BigDecimal.ZERO, Money.BRL);

        return Optional.of(new LodgingOffer("booking-" + index, name, neighborhood(card), nightly, nights,
            taxes, zero, zero, rating, Set.copyOf(amenities),
            BrazilianText.decimal(text(card, "[data-testid=distance]")).orElse(0)));
    }

    /** "Campeche, Florianopolis" -> "Campeche". Sem virgula, nao ha bairro. */
    private static String neighborhood(Element card) {
        var address = text(card, "[data-testid=address-link]");
        if (address == null) return null;
        int comma = address.indexOf(',');
        return comma > 0 ? address.substring(0, comma).trim() : null;
    }

    private static String text(Element card, String selector) {
        var found = card.selectFirst(selector);
        return found == null ? null : found.text();
    }
}
```

Repare que o construtor aceita `null` para fetcher e config: o teste só chama `parse`, que não usa
nenhum dos dois. É proposital e está coberto por teste.

- [ ] **Step 6: Rodar e ver passar**

Run: `mvn test -Dtest=BookingScraperTest`
Expected: PASS, 12 testes.

Se `takesTheNeighborhoodFromTheAddressBeforeTheComma` falhar, imprima os endereços lidos antes de
mudar o teste — o dado da fixture é real e foi conferido: existem cards com `Campeche, Florianópolis`
e `Canasvieiras, Florianópolis`.

- [ ] **Step 7: Commit**

```bash
git add src/main src/test
git commit -m "feat: read real Booking results, taxes and neighborhoods"
```

---

### Task 4: GoogleFlightsScraper

**Files:**
- Create: `src/main/java/com/smarttravel/analyzer/infrastructure/adapter/scraper/GoogleFlightsScraper.java`
- Test: `src/test/java/com/smarttravel/analyzer/infrastructure/adapter/scraper/GoogleFlightsScraperTest.java`

**Interfaces:**
- Produces: `GoogleFlightsScraper implements FlightProviderPort`, `isDemo()` = `false`, com `parse(String html, SearchCriteria)` público

**Mapeamento verificado na fixture** (`li.pIav2d`, texto em ordem):
`06:20 | 15:00 | LATAM | 8h 40 min | MGF | FLN | 1 parada | Parada de 5h 55 min | Escala longa | CGH | 142 kg CO2e`

- Companhia: primeiro nome entre os conhecidos — `LATAM`, `GOL`, `Azul`, `Voepass`. Não achou: `"Outra"`, código `"--"`.
- Preço: `BrazilianText.firstMoney` no texto do item; item sem preço é descartado.
- Escalas: `0` se contém `Sem escalas` ou `Direto`; senão o inteiro antes de `parada`.
- Duração da escala: o `5h 55 min` que segue `Parada de`, convertido em `Duration`.
- `overnightConnection`: contém `Pernoite`. `requiresAirportChange`: contém `Troca de aeroporto`.
- `legs`: uma perna direta quando `stops==0`; senão duas, com o `layoverBeforeNextLeg` na primeira.
  Isso importa: `ValueScoringDomainService.flightConvenience` já pontua escala longa e pernoite.
- `mandatoryAirportTaxes` e `checkedBagFee` ficam **zero**: a página não separa. O preço do Google
  já inclui tributos, e a bagagem não aparece. Isso é honesto e precisa estar no `CLAUDE.md`.

- [ ] **Step 1: Escrever o teste falhando**

```java
package com.smarttravel.analyzer.infrastructure.adapter.scraper;

import com.smarttravel.analyzer.domain.model.flight.FlightOffer;
import com.smarttravel.analyzer.domain.model.shared.*;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleFlightsScraperTest {

    private static final SearchCriteria CRITERIA =
        new SearchCriteria("MGF", "FLN", LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 14), 1, false, false);

    private static List<FlightOffer> offers() {
        return new GoogleFlightsScraper(null, null).parse(Fixtures.read("google-flights-mgf-fln"), CRITERIA);
    }

    @Test void readsSeveralRealFlightsFromMaringaToFlorianopolis() {
        assertThat(offers()).hasSizeGreaterThan(3);
    }

    @Test void everyFareIsInBrlAndAbovePocketChange() {
        assertThat(offers()).allSatisfy(offer ->
            assertThat(offer.rawFare().currency()).isEqualTo(Money.BRL));
        assertThat(offers()).allSatisfy(offer ->
            assertThat(offer.rawFare().amount().doubleValue()).isGreaterThan(100));
    }

    @Test void recognisesTheBrazilianCarriers() {
        assertThat(offers()).extracting(offer -> offer.airline().name())
            .contains("LATAM");
    }

    @Test void countsStopsFromTheItinerary() {
        assertThat(offers()).anySatisfy(offer -> assertThat(offer.stops()).isEqualTo(1));
    }

    @Test void aFlightWithALongLayoverIsMarkedSoTheScoringCanPenaliseIt() {
        assertThat(offers()).anySatisfy(offer ->
            assertThat(offer.legs().getFirst().hasLongLayover()).isTrue());
    }

    @Test void theItineraryStartsAtTheRequestedOrigin() {
        assertThat(offers()).allSatisfy(offer ->
            assertThat(offer.legs().getFirst().origin().code()).isEqualTo("MGF"));
    }

    @Test void declaresItselfAsRealDataNotDemo() {
        assertThat(new GoogleFlightsScraper(null, null).isDemo()).isFalse();
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn test -Dtest=GoogleFlightsScraperTest`
Expected: erro de compilação — a classe não existe.

- [ ] **Step 3: Implementar**

Siga o mesmo formato do `BookingScraper`: construtor `(PageFetcherPort, SiteConfig)`,
`searchFlights` monta a URL com `origem`, `destino`, `ida`, `volta` e delega a `parse`.

O `parse` seleciona `li.pIav2d`, e para cada item usa o texto (`element.text()`) com estes passos:

```java
    private static final List<String> AIRLINES = List.of("LATAM", "GOL", "Azul", "Voepass");
    private static final Pattern STOPS = Pattern.compile("(\\d+)\\s+parada");
    private static final Pattern LAYOVER = Pattern.compile("Parada de\\s+(?:(\\d+)\\s*h)?\\s*(?:(\\d+)\\s*min)?");
```

- `stops`: `text.contains("Sem escalas") || text.contains("Direto")` → 0; senão o grupo de `STOPS`, ou 0.
- `layover`: dos grupos de `LAYOVER`, `Duration.ofHours(h).plusMinutes(m)`; ausente → `null`.
- `airline`: primeiro de `AIRLINES` contido no texto; senão `new Airline("--", "Outra")`.
- `legs`: `stops == 0` → uma perna `origem → destino` com layover `null`; senão duas pernas, a
  primeira `origem → hub` com o layover lido, a segunda `hub → destino`. O hub pode ser o código de
  três letras que aparece depois de `Escala`/`Parada`; não achando, use `"---"`.
- `reliabilityIndex`: `0.9` fixo — a página não informa, e inventar variação seria pior.

- [ ] **Step 4: Rodar, ver passar, commitar**

Run: `mvn test -Dtest=GoogleFlightsScraperTest` — 7 testes.
Run: `mvn test`

```bash
git add src/main src/test
git commit -m "feat: read real flight results from Google Flights"
```

---

### Task 5: GoogleHotelsScraper e AirbnbScraper

Mesma forma dos anteriores. São as fontes que dão o "vários sites": o Google Hotels traz, por hotel,
o preço de Booking, Hoteis.com, Expedia e Decolar — justamente os que bloqueiam scraper direto.

**Files:**
- Create: `.../scraper/GoogleHotelsScraper.java`, `.../scraper/AirbnbScraper.java`
- Test: `.../scraper/GoogleHotelsScraperTest.java`, `.../scraper/AirbnbScraperTest.java`

**Interfaces:** ambos `implements LodgingProviderPort`, `isDemo()` = `false`, `parse` público.

**Antes de escrever o parser, INSPECIONE a fixture.** Não invente seletor. Rode:

```bash
python3 - <<'EOF'
import gzip, re
for name in ['google-hotels-florianopolis', 'airbnb-florianopolis']:
    s = gzip.open('src/test/resources/fixtures/%s.html.gz' % name, 'rt', encoding='utf-8', errors='replace').read()
    print('===', name, len(s))
    for attr in ['jsname', 'data-testid', 'itemprop', 'role']:
        vals = re.findall(r'%s="([^"]{2,40})"' % attr, s)
        top = sorted(set(vals), key=vals.count, reverse=True)[:12]
        print(' ', attr, top)
EOF
```

Escreva os testes a partir do que a fixture realmente contém: quantidade de anúncios, um nome real
conferido no dump, preço em BRL, e `isDemo()` falso. Os dumps de texto em
`docs/superpowers/probes/pw3-googlehotels.txt` e `pw3-airbnb.txt` ajudam a escolher os nomes.

Se o Airbnb não expuser nota e número de avaliações de forma confiável, use `new HotelRating(0, 0)`
— o `ValueScoringDomainService` já trata volume baixo com o rating bayesiano, e inventar nota seria
mentir. Registre isso no `CLAUDE.md`.

- [ ] **Step 1: Inspecionar as duas fixtures** com o script acima e anotar os seletores reais.
- [ ] **Step 2: Escrever os testes** contra o que foi apurado, e vê-los falhar.
- [ ] **Step 3: Implementar os dois scrapers.**
- [ ] **Step 4: Rodar `mvn test` e commitar.**

```bash
git commit -m "feat: read real lodging results from Google Hotels and Airbnb"
```

---

### Task 6: Ligar as fontes reais e dizer a verdade sobre cada uma

Hoje os `Demo*Provider` são `@Component` e o Spring injeta um de cada porta. Com dois
`LodgingProviderPort` reais, a injeção fica ambígua e a aplicação nem sobe.

**Files:**
- Modify: `.../infrastructure/adapter/demo/Demo*Provider.java` (tirar `@Component`)
- Modify: `.../infrastructure/configuration/BeanConfig.java`
- Create: `.../infrastructure/adapter/scraper/CompositeLodgingProvider.java`
- Modify: `.../application/service/SearchRunner.java`
- Modify: `.../application/dto/SourceStatusDTO.java`
- Test: `.../scraper/CompositeLodgingProviderTest.java`

**Interfaces:**
- `CompositeLodgingProvider(List<LodgingProviderPort> sources)` — consulta cada fonte, junta as
  ofertas, e uma fonte que falha não impede as outras. `isDemo()` só é `true` se **todas** forem demo.
- `SourceStatus` ganha `boolean demo` para a tela dizer qual fonte é real e qual é simulada.
- `SearchRunner` reporta uma linha por fonte de hospedagem, não uma linha só.

**Decisão de produto que precisa ficar explícita:** voos e hospedagem passam a ser reais; **carro
continua de demonstração**, porque nenhuma locadora foi raspada. A faixa global "tudo é demonstração"
vira aviso **por fonte** — dizer que tudo é simulado passaria a ser mentira, e esconder seria pior.

- [ ] **Step 1: Escrever o teste do composite** — três fontes, uma quebrando, e o resultado junta as
  outras duas; `isDemo()` falso quando pelo menos uma é real.
- [ ] **Step 2: Ver falhar. Step 3: Implementar.**
- [ ] **Step 4: Trocar a fiação em `BeanConfig`:**

```java
    @Bean LodgingProviderPort lodgingProvider(PageFetcherPort fetcher, SiteConfigLoader loader) {
        var configs = loader.load(java.nio.file.Path.of("scrapers"));
        return new CompositeLodgingProvider(List.of(
            new BookingScraper(fetcher, configs.get("booking")),
            new GoogleHotelsScraper(fetcher, configs.get("google-hotels")),
            new AirbnbScraper(fetcher, configs.get("airbnb"))));
    }

    @Bean FlightProviderPort flightProvider(PageFetcherPort fetcher, SiteConfigLoader loader) {
        return new GoogleFlightsScraper(fetcher, loader.load(java.nio.file.Path.of("scrapers")).get("google-flights"));
    }

    @Bean CarRentalProviderPort carRentalProvider() { return new DemoCarRentalProvider(); }
```

- [ ] **Step 5: `mvn test` verde, e teste manual com a aplicação no ar** — busca MGF → Florianópolis,
  7 a 14/11, conferindo que os preços batem com o que o site mostra no navegador.
- [ ] **Step 6: Commit.**

```bash
git commit -m "feat: serve real flight and lodging data, flagging demo per source"
```

---

### Task 7: Front em React

**Files:**
- Create: `front/` (Vite + React), com `package.json`, `vite.config.js`, `index.html`, `src/`
- Delete: `src/main/resources/static/app.js`, `index.html`, `styles.css`
- Modify: `.gitignore` (já cobre `front/node_modules` e `front/dist`)

**Componentes**, um arquivo cada, em `front/src/components/`:
`SearchForm`, `SourceProgress`, `SourceBadge`, `NeighborhoodChips`, `FilterBar`, `PackageCard`,
`DateOptions`, `DemoNotice`.

Estado num hook `front/src/hooks/useSearch.js`: `start(criteria)` faz o POST, guarda o `searchId`,
faz polling de 1s (máx. 60) e reexpõe `{ status, sources, packages, neighborhoods, dateOptions, demo }`.
Mudar filtro ou bairro refaz só o GET.

**Build:** `vite build` com `outDir: '../src/main/resources/static'` e `emptyOutDir: true`, para o
Spring servir o resultado. Em desenvolvimento, `server.proxy` manda `/api` para `localhost:8080`.

Texto todo em português. O aviso de dado simulado é por fonte, vindo de `sources[].demo`.

- [ ] **Step 1: Criar o projeto Vite e os componentes.**
- [ ] **Step 2: `npm run build` e conferir que `src/main/resources/static/` recebeu o bundle.**
- [ ] **Step 3: Subir a aplicação e verificar a busca MGF → Florianópolis de ponta a ponta.**
- [ ] **Step 4: Commit.**

```bash
git commit -m "feat: rebuild the front end as React components"
```

---

### Task 8: Docker

**Files:**
- Create: `Dockerfile`, `.dockerignore`, `docker-compose.yml`

O runtime precisa do Chrome, que é o que torna a imagem grande. Não há como fugir disso raspando.

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
COPY scrapers ./scrapers
RUN mvn -q -DskipTests package

FROM node:20-slim AS front
WORKDIR /front
COPY front/package*.json ./
RUN npm ci
COPY front .
RUN npm run build

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends \
      wget gnupg ca-certificates fonts-liberation libnss3 libatk-bridge2.0-0 \
      libgtk-3-0 libgbm1 libasound2 \
 && wget -q -O /tmp/chrome.deb https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb \
 && apt-get install -y /tmp/chrome.deb && rm /tmp/chrome.deb \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
COPY --from=build /app/scrapers ./scrapers
COPY --from=front /front/dist ./static
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

`docker-compose.yml` publica a 8080 e monta um volume em `/app/.cache`.

- [ ] **Step 1: Escrever os três arquivos.**
- [ ] **Step 2: `docker build .` e conferir que termina.** Se o build falhar por falta de biblioteca
  do Chrome, o erro dirá qual; acrescente ao `apt-get`.
- [ ] **Step 3: `docker compose up` e buscar MGF → Florianópolis pela porta 8080.**
- [ ] **Step 4: Documentar no CLAUDE.md e commitar.**

```bash
git commit -m "build: run the whole thing from a container"
```

---

## Notas para quem for implementar

- **Não invente seletor.** Todos os seletores desta rodada saíram de fixture real. Se um teste não
  achar o que o plano diz, imprima o que a fixture tem antes de mudar o teste.
- **Nenhum teste no build padrão toca a rede.** Se você precisou de internet para um teste passar,
  ele está errado ou falta `@Tag("rede")`.
- **Não remova o aviso de dado simulado do carro.** Enquanto a locadora for demo, a tela tem que
  dizer. Um buscador que mostra preço inventado sem avisar é pior que não existir.
- **Se uma fonte parar de responder**, isso é resultado esperado, não obstáculo: ela vira DEGRADADO
  e as outras seguem. Não tente contornar bloqueio com proxy ou fingerprint novo sem falar com o dono.
