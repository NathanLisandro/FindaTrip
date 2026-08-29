# Buscador de Pacote Ideal — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fazer o buscador funcionar de ponta a ponta — o usuário informa origem, destino e datas, a busca roda assincronamente e devolve um Pacote Ideal (voo + hospedagem + carro) ranqueado por custo real e avaliação, com filtros, explicação da escolha e links de reserva.

**Architecture:** Hexagonal, já estabelecida no repo. O domínio ganha a peça que faltava (`PackageAssemblerDomainService`) para montar pacotes a partir de ofertas; a aplicação ganha uma busca assíncrona com estado em memória; a infraestrutura ganha providers de demonstração atrás das portas que já existem. Trocar demo por fonte real será trocar o bean em `BeanConfig`.

**Tech Stack:** Java 21, Spring Boot 3.5.5, Maven, JUnit 5 + AssertJ, HTML/CSS/JS estático (sem framework de front).

## Global Constraints

- Pacote raiz: `com.smarttravel.analyzer`. Testes espelham o pacote em `src/test/java/`.
- `domain/` NÃO importa nada de Spring. Beans de domínio são registrados à mão em `infrastructure/configuration/BeanConfig`.
- Portas ficam em `domain/repository/` com sufixo `Port`. Adaptadores em `infrastructure/adapter/` com sufixo `Adapter` ou nome próprio do provider.
- Modelo de domínio é `record` imutável, validado no construtor compacto, lançando `DomainException`. Coleções copiadas na entrada.
- Dinheiro é sempre `Money`. Nunca `double` para preço. Moeda do projeto inteiro: **BRL**.
- Nenhum teste toca a rede.
- TDD: escrever o teste, rodar e ver falhar, implementar o mínimo, rodar e ver passar, commitar.
- Comando de teste: `mvn test`. Um teste só: `mvn test -Dtest=NomeDaClasse#nomeDoMetodo`.
- Não há Maven Wrapper — usar o `mvn` do sistema.
- Nomes de domínio em inglês (seguindo o código existente); texto voltado ao usuário em português.

---

## File Structure

**Domínio (novo):**
- `domain/model/packagebundle/PackagePart.java` — par oferta + preço normalizado
- `domain/model/search/SearchStatus.java` — BUSCANDO | PRONTO | PARCIAL | ERRO
- `domain/model/search/SourceHealth.java` — BUSCANDO | OK | DEGRADADO
- `domain/model/search/SourceStatus.java` — estado de uma fonte
- `domain/model/search/TravelOffers.java` — as três listas de ofertas
- `domain/model/search/PackageFilter.java` — critérios de filtro
- `domain/service/PackageAssemblerDomainService.java` — monta candidatos
- `domain/service/PackageFilterDomainService.java` — aplica filtros
- `domain/service/PackageExplanationDomainService.java` — gera o "por quê"
- `domain/link/DeepLinkBuilder.java` — porta de link, função pura

**Domínio (modificado):**
- `domain/model/shared/Money.java` — ganha `BRL`, `brl()`, `subtract()`
- `domain/model/packagebundle/TravelPackage.java` — passa a carregar as ofertas
- `domain/service/PackageBundlerDomainService.java` — ajuste ao novo construtor
- `domain/repository/*Port.java` — ganham `default boolean isDemo()`

**Aplicação (novo):**
- `application/search/SearchSession.java` — estado mutável de uma busca
- `application/search/SearchSessionStore.java` — mapa concorrente com TTL
- `application/usecase/StartSearchUseCase.java` — dispara a busca
- `application/usecase/GetSearchResultUseCase.java` — lê resultado + filtra
- `application/service/SearchRunner.java` — orquestra providers, trata falha
- `application/service/DateFlexibilityService.java` — datas vizinhas
- `application/dto/` — DTOs de request e response

**Infraestrutura (novo):**
- `infrastructure/adapter/demo/DemoFlightProvider.java`
- `infrastructure/adapter/demo/DemoLodgingProvider.java`
- `infrastructure/adapter/demo/DemoCarRentalProvider.java`
- `infrastructure/adapter/link/BookingDeepLinkBuilder.java`
- `infrastructure/adapter/link/GoogleFlightsDeepLinkBuilder.java`

**Apresentação:**
- `presentation/rest/SearchController.java` — novo
- `src/main/resources/static/` — index.html, app.js, styles.css reescritos

---

### Task 1: Fazer a suíte de testes realmente rodar

Hoje `mvn test` termina em `BUILD SUCCESS` com `Tests run: 0`. O `pom.xml` não fixa a versão do `maven-surefire-plugin`, então o Maven usa a 2.12.4 do super-POM, que não enxerga JUnit 5. Os três testes existentes nunca rodaram. Sem isso não existe TDD.

**Files:**
- Modify: `pom.xml`

**Interfaces:**
- Consumes: nada
- Produces: uma suíte de testes que executa. Todas as tarefas seguintes dependem disso.

- [ ] **Step 1: Confirmar o problema**

Run: `mvn test`
Expected: `BUILD SUCCESS` com `Tests run: 0, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 2: Fixar a versão do surefire**

Em `pom.xml`, dentro de `<build><plugins>`, adicionar antes do `spring-boot-maven-plugin`:

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-surefire-plugin</artifactId>
  <version>3.5.2</version>
</plugin>
```

- [ ] **Step 3: Confirmar que os testes existentes rodam e passam**

Run: `mvn test`
Expected: `Tests run: 3, Failures: 0, Errors: 0` — os três de `TravelDomainServicesTest`.

Se algum falhar, PARE e reporte. Um teste que nunca rodou pode estar errado, e consertá-lo é decisão do usuário, não sua.

- [ ] **Step 4: Commit**

```bash
git add pom.xml
git commit -m "build: pin surefire 3.5.2 so JUnit 5 tests actually run"
```

---

### Task 2: Moeda BRL e as operações que faltam em Money

`Money.add` lança quando as moedas diferem, e o código de hoje cria tudo em USD. Para calcular custo oculto (custo real menos preço anunciado) também falta `subtract`.

**Files:**
- Modify: `src/main/java/com/smarttravel/analyzer/domain/model/shared/Money.java`
- Modify: `src/main/java/com/smarttravel/analyzer/application/usecase/GetPriceTrendHistoryUseCase.java:23`
- Modify: `src/main/java/com/smarttravel/analyzer/infrastructure/adapter/external/InMemoryPriceHistoryStoreAdapter.java`
- Test: `src/test/java/com/smarttravel/analyzer/domain/model/shared/MoneyTest.java`

**Interfaces:**
- Consumes: nada
- Produces:
  - `Money.BRL` — constante `java.util.Currency`
  - `Money.brl(String amount)` → `Money`
  - `Money.subtract(Money other)` → `Money` (lança `DomainException` se moedas diferem; resultado negativo é impedido pelo construtor)

- [ ] **Step 1: Escrever o teste falhando**

Criar `src/test/java/com/smarttravel/analyzer/domain/model/shared/MoneyTest.java`:

```java
package com.smarttravel.analyzer.domain.model.shared;

import com.smarttravel.analyzer.domain.exception.DomainException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test void brlFactoryBuildsBrazilianCurrencyWithTwoDecimalPlaces() {
        var value = Money.brl("1234.5");
        assertThat(value.currency().getCurrencyCode()).isEqualTo("BRL");
        assertThat(value.amount()).isEqualByComparingTo("1234.50");
    }

    @Test void subtractReturnsTheDifferenceBetweenTwoAmounts() {
        assertThat(Money.brl("1850.00").subtract(Money.brl("1576.00")).amount())
            .isEqualByComparingTo("274.00");
    }

    @Test void subtractRejectsDifferentCurrencies() {
        assertThatThrownBy(() -> Money.brl("100.00").subtract(Money.of("100.00", "USD")))
            .isInstanceOf(DomainException.class)
            .hasMessageContaining("Currency mismatch");
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn test -Dtest=MoneyTest`
Expected: erro de compilação — `cannot find symbol: method brl(String)` e `method subtract(Money)`.

- [ ] **Step 3: Implementar**

Em `Money.java`, adicionar dentro do record, junto dos métodos existentes:

```java
    public static final java.util.Currency BRL = java.util.Currency.getInstance("BRL");

    public static Money brl(String amount) { return new Money(new BigDecimal(amount), BRL); }

    public Money subtract(Money other) { requireSameCurrency(other); return new Money(amount.subtract(other.amount), currency); }
```

- [ ] **Step 4: Rodar e ver passar**

Run: `mvn test -Dtest=MoneyTest`
Expected: PASS, 3 testes.

- [ ] **Step 5: Trocar USD por BRL no que já existe**

Em `GetPriceTrendHistoryUseCase.java`, no método `trend`, trocar:

```java
        var rating = trend.classify(new Money(currentPrice, Currency.getInstance("USD")));
```

por:

```java
        var rating = trend.classify(new Money(currentPrice, Money.BRL));
```

Remover o import `java.util.Currency` se ficar sem uso.

Em `InMemoryPriceHistoryStoreAdapter.java`, trocar a linha `var usd = Currency.getInstance("USD");` por `var brl = Money.BRL;` e substituir as quatro ocorrências de `usd` por `brl`. Remover o import `java.util.Currency` se ficar sem uso, mantendo os demais.

- [ ] **Step 6: Rodar a suíte inteira**

Run: `mvn test`
Expected: PASS, 6 testes (3 antigos + 3 novos).

- [ ] **Step 7: Commit**

```bash
git add pom.xml src/main/java src/test/java
git commit -m "feat: switch the money flow to BRL and add Money.subtract"
```

---

### Task 3: Bairro na hospedagem

Para focar a busca num ponto do destino, a oferta precisa dizer onde fica. Hoje `LodgingOffer` só tem `distanceToAttractionsKm`, que não diz de onde. O bairro entra como campo da oferta — com fonte real ele vem junto do anúncio, e nada precisa ser cadastrado por cidade.

**Files:**
- Modify: `src/main/java/com/smarttravel/analyzer/domain/model/lodging/LodgingOffer.java`
- Test: `src/test/java/com/smarttravel/analyzer/domain/model/lodging/LodgingOfferTest.java`

**Interfaces:**
- Consumes: `Money.brl` (Task 2)
- Produces:
  - `LodgingOffer(String id, String name, String neighborhood, Money nightlyRate, int nights, Money serviceFees, Money cityTaxes, Money resortFees, HotelRating rating, Set<Amenity> amenities, double distanceToAttractionsKm)` — `neighborhood` é o **terceiro** parâmetro, logo depois de `name`
  - Bairro em branco ou nulo vira `"Não informado"`, nunca `null` — assim nenhum consumidor precisa de checagem de nulo

- [ ] **Step 1: Escrever o teste falhando**

Criar `src/test/java/com/smarttravel/analyzer/domain/model/lodging/LodgingOfferTest.java`:

```java
package com.smarttravel.analyzer.domain.model.lodging;

import com.smarttravel.analyzer.domain.model.shared.Money;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LodgingOfferTest {

    private static LodgingOffer offer(String neighborhood) {
        return new LodgingOffer("lo-1", "Pousada Maré Alta", neighborhood, Money.brl("200.00"), 3,
            Money.brl("30.00"), Money.brl("20.00"), Money.brl("0.00"), new HotelRating(8.5, 500),
            Set.of(Amenity.BREAKFAST_INCLUDED), 1.0);
    }

    @Test void keepsTheNeighborhoodItWasGiven() {
        assertThat(offer("Boa Viagem").neighborhood()).isEqualTo("Boa Viagem");
    }

    @Test void aMissingNeighborhoodBecomesAReadableLabelInsteadOfNull() {
        assertThat(offer(null).neighborhood()).isEqualTo("Não informado");
        assertThat(offer("   ").neighborhood()).isEqualTo("Não informado");
    }

    @Test void normalizationStillAddsEveryFeeOnTopOfTheNightlyRate() {
        assertThat(offer("Boa Viagem").normalize().totalPrice().amount()).isEqualByComparingTo("650.00");
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn test -Dtest=LodgingOfferTest`
Expected: erro de compilação — o construtor de `LodgingOffer` tem 10 parâmetros, não 11.

- [ ] **Step 3: Implementar**

Substituir a declaração e o construtor compacto em `LodgingOffer.java`:

```java
public record LodgingOffer(String id, String name, String neighborhood, Money nightlyRate, int nights,
                           Money serviceFees, Money cityTaxes, Money resortFees, HotelRating rating,
                           Set<Amenity> amenities, double distanceToAttractionsKm) {

    public static final String UNKNOWN_NEIGHBORHOOD = "Não informado";

    public LodgingOffer {
        amenities = Set.copyOf(amenities);
        neighborhood = (neighborhood == null || neighborhood.isBlank()) ? UNKNOWN_NEIGHBORHOOD : neighborhood.trim();
    }
```

O restante do record (`normalize()` e `has()`) não muda.

- [ ] **Step 4: Rodar e ver passar**

Run: `mvn test -Dtest=LodgingOfferTest`
Expected: PASS, 3 testes.

- [ ] **Step 5: Rodar a suíte inteira**

Run: `mvn test`
Expected: PASS. Se algum arquivo de produção ainda construir `LodgingOffer` com 10 argumentos, o compilador aponta — acrescente o bairro no terceiro lugar.

- [ ] **Step 6: Commit**

```bash
git add src/main/java src/test/java
git commit -m "feat: record which neighborhood a lodging offer sits in"
```

---

### Task 4: TravelPackage passa a carregar as ofertas que o compõem

Hoje `TravelPackage` guarda id, preço total e três notas. Sem referência às ofertas, a tela não tem como mostrar qual hotel nem justificar a escolha, e não dá para calcular custo oculto.

**Files:**
- Create: `src/main/java/com/smarttravel/analyzer/domain/model/packagebundle/PackagePart.java`
- Modify: `src/main/java/com/smarttravel/analyzer/domain/model/packagebundle/TravelPackage.java`
- Modify: `src/main/java/com/smarttravel/analyzer/domain/service/PackageBundlerDomainService.java:16`
- Test: `src/test/java/com/smarttravel/analyzer/domain/model/packagebundle/TravelPackageTest.java`

**Interfaces:**
- Consumes: `Money.brl`, `Money.subtract` (Task 2)
- Produces:
  - `PackagePart<T>(T offer, NormalizedPrice price)`
  - `TravelPackage(String id, Money totalPrice, Score valueScore, Score qualityScore, Score convenienceScore, PackageBundleType recommendationType, PackagePart<FlightOffer> flight, PackagePart<LodgingOffer> lodging, PackagePart<CarRentalOffer> car)` — `car` pode ser `null`
  - `TravelPackage.hasCar()` → `boolean`
  - `TravelPackage.advertisedPrice()` → `Money` — soma dos `rawPrice`
  - `TravelPackage.hiddenCosts()` → `Money` — `totalPrice - advertisedPrice`
  - `TravelPackage.costAdjustments()` → `List<String>` — todos os ajustes, sem repetição, na ordem voo, hospedagem, carro
  - `TravelPackage.withRecommendationType(PackageBundleType)` → `TravelPackage`

- [ ] **Step 1: Escrever o teste falhando**

Criar `src/test/java/com/smarttravel/analyzer/domain/model/packagebundle/TravelPackageTest.java`:

```java
package com.smarttravel.analyzer.domain.model.packagebundle;

import com.smarttravel.analyzer.domain.model.carrental.*;
import com.smarttravel.analyzer.domain.model.flight.*;
import com.smarttravel.analyzer.domain.model.lodging.*;
import com.smarttravel.analyzer.domain.model.shared.*;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TravelPackageTest {

    private static PackagePart<FlightOffer> flightPart() {
        var offer = new FlightOffer("fl-1", new Airline("G3", "GOL"), Money.brl("800.00"), Money.brl("90.00"),
            Money.brl("60.00"), List.of(new FlightLeg(new Location("CWB", "Curitiba", "BR"),
            new Location("REC", "Recife", "BR"), null, false, false)), .9);
        return new PackagePart<>(offer, offer.normalize(true));
    }

    private static PackagePart<LodgingOffer> lodgingPart() {
        var offer = new LodgingOffer("lo-1", "Pousada Boa Vista", "Boa Viagem", Money.brl("200.00"), 3, Money.brl("30.00"),
            Money.brl("20.00"), Money.brl("0.00"), new HotelRating(8.9, 2000),
            Set.of(Amenity.BREAKFAST_INCLUDED), 1.0);
        return new PackagePart<>(offer, offer.normalize());
    }

    private static TravelPackage packageWithoutCar() {
        var flight = flightPart();
        var lodging = lodgingPart();
        var total = flight.price().totalPrice().add(lodging.price().totalPrice());
        return new TravelPackage("fl-1|lo-1", total, new Score(90), new Score(85), new Score(80),
            null, flight, lodging, null);
    }

    @Test void advertisedPriceSumsTheRawPricesOfEveryPart() {
        assertThat(packageWithoutCar().advertisedPrice().amount()).isEqualByComparingTo("1400.00");
    }

    @Test void hiddenCostsAreTheDifferenceBetweenRealAndAdvertisedPrice() {
        assertThat(packageWithoutCar().hiddenCosts().amount()).isEqualByComparingTo("200.00");
    }

    @Test void costAdjustmentsListEveryAddedFeeWithoutRepeating() {
        assertThat(packageWithoutCar().costAdjustments())
            .containsExactly("mandatory_airport_taxes", "checked_baggage",
                             "service_fees", "city_taxes", "resort_fees");
    }

    @Test void packageWithoutCarReportsItHasNoCar() {
        assertThat(packageWithoutCar().hasCar()).isFalse();
    }

    @Test void withRecommendationTypeKeepsEveryOtherField() {
        var tagged = packageWithoutCar().withRecommendationType(PackageBundleType.BEST_VALUE_OVERALL);
        assertThat(tagged.recommendationType()).isEqualTo(PackageBundleType.BEST_VALUE_OVERALL);
        assertThat(tagged.id()).isEqualTo("fl-1|lo-1");
        assertThat(tagged.lodging().offer().name()).isEqualTo("Pousada Boa Vista");
    }
}
```

Confira as contas antes de rodar: voo anunciado 800 + hospedagem anunciada 200×3=600 = **1400**. Voo real 800+90+60=950; hospedagem real 600+30+20+0=650; total **1600**. Custo oculto 1600−1400=**200**.

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn test -Dtest=TravelPackageTest`
Expected: erro de compilação — `PackagePart` não existe e o construtor de `TravelPackage` tem 6 parâmetros.

- [ ] **Step 3: Criar PackagePart**

Criar `src/main/java/com/smarttravel/analyzer/domain/model/packagebundle/PackagePart.java`:

```java
package com.smarttravel.analyzer.domain.model.packagebundle;

import com.smarttravel.analyzer.domain.exception.DomainException;
import com.smarttravel.analyzer.domain.model.shared.NormalizedPrice;

public record PackagePart<T>(T offer, NormalizedPrice price) {
    public PackagePart {
        if (offer == null || price == null) throw new DomainException("Package part requires an offer and its normalized price");
    }
}
```

- [ ] **Step 4: Reescrever TravelPackage**

Substituir o conteúdo de `TravelPackage.java` por:

```java
package com.smarttravel.analyzer.domain.model.packagebundle;

import com.smarttravel.analyzer.domain.model.carrental.CarRentalOffer;
import com.smarttravel.analyzer.domain.model.flight.FlightOffer;
import com.smarttravel.analyzer.domain.model.lodging.LodgingOffer;
import com.smarttravel.analyzer.domain.model.shared.*;
import java.util.ArrayList;
import java.util.List;

public record TravelPackage(String id, Money totalPrice, Score valueScore, Score qualityScore,
                            Score convenienceScore, PackageBundleType recommendationType,
                            PackagePart<FlightOffer> flight, PackagePart<LodgingOffer> lodging,
                            PackagePart<CarRentalOffer> car) implements Comparable<TravelPackage> {

    @Override public int compareTo(TravelPackage other) { return Double.compare(other.valueScore.value(), valueScore.value()); }

    public boolean meetsSmartBudgetFloor() { return qualityScore.value() >= 70 && convenienceScore.value() >= 60; }

    public boolean hasCar() { return car != null; }

    public Money advertisedPrice() {
        Money sum = flight.price().rawPrice().add(lodging.price().rawPrice());
        return hasCar() ? sum.add(car.price().rawPrice()) : sum;
    }

    public Money hiddenCosts() { return totalPrice.subtract(advertisedPrice()); }

    public List<String> costAdjustments() {
        var all = new ArrayList<String>();
        addMissing(all, flight.price().includedCostAdjustments());
        addMissing(all, lodging.price().includedCostAdjustments());
        if (hasCar()) addMissing(all, car.price().includedCostAdjustments());
        return List.copyOf(all);
    }

    public TravelPackage withRecommendationType(PackageBundleType type) {
        return new TravelPackage(id, totalPrice, valueScore, qualityScore, convenienceScore, type, flight, lodging, car);
    }

    private static void addMissing(List<String> target, List<String> source) {
        source.stream().filter(item -> !target.contains(item)).forEach(target::add);
    }
}
```

- [ ] **Step 5: Ajustar PackageBundlerDomainService ao novo construtor**

Em `PackageBundlerDomainService.java`, substituir o método privado `tag` por uma chamada ao novo método, e usar `withRecommendationType` no lugar da reconstrução manual:

```java
    private TravelPackage tag(TravelPackage p, PackageBundleType type) { return p.withRecommendationType(type); }
```

- [ ] **Step 6: Rodar a suíte inteira**

Run: `mvn test`
Expected: PASS. `TravelPackageTest` com 5 testes, e os anteriores continuam verdes.

- [ ] **Step 7: Commit**

```bash
git add src/main/java src/test/java
git commit -m "feat: carry the underlying offers inside TravelPackage"
```

---

### Task 5: Montar pacotes a partir das ofertas

Esta é a peça que falta no meio do domínio. `SearchBestValuePackagesUseCase` devolve três pacotes escritos à mão porque ninguém combina ofertas em candidatos.

**Files:**
- Create: `src/main/java/com/smarttravel/analyzer/domain/model/search/TravelOffers.java`
- Create: `src/main/java/com/smarttravel/analyzer/domain/service/PackageAssemblerDomainService.java`
- Test: `src/test/java/com/smarttravel/analyzer/domain/service/PackageAssemblerDomainServiceTest.java`

**Interfaces:**
- Consumes: `TravelPackage`, `PackagePart` (Task 3); `CostNormalizerDomainService`, `ValueScoringDomainService`, `PriceTrendDomainService` (já existem)
- Produces:
  - `TravelOffers(List<FlightOffer> flights, List<LodgingOffer> lodgings, List<CarRentalOffer> cars)`
  - `PackageAssemblerDomainService(CostNormalizerDomainService, ValueScoringDomainService, PriceTrendDomainService)`
  - `PackageAssemblerDomainService.assemble(SearchCriteria criteria, TravelOffers offers)` → `List<TravelPackage>` (sem `recommendationType`, que é atribuído depois pelo `PackageBundlerDomainService`)
  - `PackageAssemblerDomainService.MAX_PER_DIMENSION` = `5`

**Como o preço vira nota sem histórico coletado:** o `ValueScoringDomainService.priceScore` precisa de um `RoutePriceTrend`. Como não há coletor rodando, a tendência é calculada sobre os próprios candidatos desta busca — o pacote mais barato que a média do conjunto pontua acima de 70, o mais caro abaixo. É honesto (compara o que está na tela) e reaproveita `PriceTrendDomainService`. Quando o coletor existir, troca-se a origem da tendência sem mexer no resto.

- [ ] **Step 1: Escrever o teste falhando**

Criar `src/test/java/com/smarttravel/analyzer/domain/service/PackageAssemblerDomainServiceTest.java`:

```java
package com.smarttravel.analyzer.domain.service;

import com.smarttravel.analyzer.domain.model.carrental.*;
import com.smarttravel.analyzer.domain.model.flight.*;
import com.smarttravel.analyzer.domain.model.lodging.*;
import com.smarttravel.analyzer.domain.model.packagebundle.TravelPackage;
import com.smarttravel.analyzer.domain.model.search.TravelOffers;
import com.smarttravel.analyzer.domain.model.shared.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PackageAssemblerDomainServiceTest {

    private final PackageAssemblerDomainService assembler = new PackageAssemblerDomainService(
        new CostNormalizerDomainService(), new ValueScoringDomainService(), new PriceTrendDomainService());

    private static SearchCriteria criteria(boolean carRequired) {
        return new SearchCriteria("CWB", "REC", LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 13), 2, true, carRequired);
    }

    private static FlightOffer flight(String id, String fare) {
        return new FlightOffer(id, new Airline("G3", "GOL"), Money.brl(fare), Money.brl("90.00"), Money.brl("60.00"),
            List.of(new FlightLeg(new Location("CWB", "Curitiba", "BR"), new Location("REC", "Recife", "BR"), null, false, false)), .9);
    }

    private static LodgingOffer lodging(String id, String nightly) {
        return new LodgingOffer(id, "Hotel " + id, "Boa Viagem", Money.brl(nightly), 3, Money.brl("30.00"), Money.brl("20.00"),
            Money.brl("0.00"), new HotelRating(8.5, 500), Set.of(Amenity.BREAKFAST_INCLUDED), 1.0);
    }

    private static CarRentalOffer car(String id, String daily) {
        return new CarRentalOffer(id, "Movida", CarCategory.COMPACT, Money.brl(daily), 3,
            InsuranceCoverageType.BASIC_CDW_TP, Money.brl("25.00"), Money.brl("40.00"), true, PickupMode.IN_TERMINAL, .9);
    }

    @Test void buildsOnePackageForEveryFlightAndLodgingCombinationWhenNoCarIsRequired() {
        var offers = new TravelOffers(List.of(flight("f1", "800.00"), flight("f2", "900.00")),
                                      List.of(lodging("l1", "200.00"), lodging("l2", "250.00")), List.of());
        assertThat(assembler.assemble(criteria(false), offers)).hasSize(4);
    }

    @Test void packagesWithoutCarCarryNoCarPart() {
        var offers = new TravelOffers(List.of(flight("f1", "800.00")), List.of(lodging("l1", "200.00")), List.of(car("c1", "90.00")));
        assertThat(assembler.assemble(criteria(false), offers)).allSatisfy(p -> assertThat(p.hasCar()).isFalse());
    }

    @Test void includesTheCarInEveryCombinationWhenTheUserRequiresOne() {
        var offers = new TravelOffers(List.of(flight("f1", "800.00")), List.of(lodging("l1", "200.00")),
                                      List.of(car("c1", "90.00"), car("c2", "110.00")));
        var packages = assembler.assemble(criteria(true), offers);
        assertThat(packages).hasSize(2).allSatisfy(p -> assertThat(p.hasCar()).isTrue());
    }

    @Test void totalPriceIsTheSumOfNormalizedCostsNotAdvertisedPrices() {
        var offers = new TravelOffers(List.of(flight("f1", "800.00")), List.of(lodging("l1", "200.00")), List.of());
        var only = assembler.assemble(criteria(false), offers).getFirst();
        assertThat(only.totalPrice().amount()).isEqualByComparingTo("1600.00");
        assertThat(only.advertisedPrice().amount()).isEqualByComparingTo("1400.00");
    }

    @Test void truncatesEachDimensionToTheFiveCheapestOptions() {
        var flights = List.of(flight("f1", "800.00"), flight("f2", "810.00"), flight("f3", "820.00"),
                              flight("f4", "830.00"), flight("f5", "840.00"), flight("f6", "850.00"));
        var lodgings = List.of(lodging("l1", "200.00"), lodging("l2", "210.00"), lodging("l3", "220.00"),
                               lodging("l4", "230.00"), lodging("l5", "240.00"), lodging("l6", "250.00"));
        var packages = assembler.assemble(criteria(false), new TravelOffers(flights, lodgings, List.of()));
        assertThat(packages).hasSize(25);
        assertThat(packages).noneSatisfy(p -> assertThat(p.id()).contains("f6"));
    }

    @Test void theCheapestPackageScoresHigherOnPriceThanTheMostExpensiveOne() {
        var offers = new TravelOffers(List.of(flight("f1", "800.00"), flight("f2", "2000.00")),
                                      List.of(lodging("l1", "200.00")), List.of());
        var packages = assembler.assemble(criteria(false), offers);
        var cheapest = packages.stream().min(java.util.Comparator.comparing(TravelPackage::totalPrice)).orElseThrow();
        var priciest = packages.stream().max(java.util.Comparator.comparing(TravelPackage::totalPrice)).orElseThrow();
        assertThat(cheapest.valueScore().value()).isGreaterThan(priciest.valueScore().value());
    }

    @Test void returnsEmptyWhenAnEssentialDimensionHasNoOffers() {
        assertThat(assembler.assemble(criteria(false), new TravelOffers(List.of(), List.of(lodging("l1", "200.00")), List.of()))).isEmpty();
    }

    @Test void returnsEmptyWhenTheUserRequiresACarAndThereIsNone() {
        var offers = new TravelOffers(List.of(flight("f1", "800.00")), List.of(lodging("l1", "200.00")), List.of());
        assertThat(assembler.assemble(criteria(true), offers)).isEmpty();
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn test -Dtest=PackageAssemblerDomainServiceTest`
Expected: erro de compilação — `TravelOffers` e `PackageAssemblerDomainService` não existem.

- [ ] **Step 3: Criar TravelOffers**

Criar `src/main/java/com/smarttravel/analyzer/domain/model/search/TravelOffers.java`:

```java
package com.smarttravel.analyzer.domain.model.search;

import com.smarttravel.analyzer.domain.model.carrental.CarRentalOffer;
import com.smarttravel.analyzer.domain.model.flight.FlightOffer;
import com.smarttravel.analyzer.domain.model.lodging.LodgingOffer;
import java.util.List;

public record TravelOffers(List<FlightOffer> flights, List<LodgingOffer> lodgings, List<CarRentalOffer> cars) {
    public TravelOffers {
        flights = List.copyOf(flights);
        lodgings = List.copyOf(lodgings);
        cars = List.copyOf(cars);
    }
    public static TravelOffers empty() { return new TravelOffers(List.of(), List.of(), List.of()); }
}
```

- [ ] **Step 4: Implementar o assembler**

Criar `src/main/java/com/smarttravel/analyzer/domain/service/PackageAssemblerDomainService.java`:

```java
package com.smarttravel.analyzer.domain.service;

import com.smarttravel.analyzer.domain.model.carrental.CarRentalOffer;
import com.smarttravel.analyzer.domain.model.flight.FlightOffer;
import com.smarttravel.analyzer.domain.model.history.PriceHistoryPoint;
import com.smarttravel.analyzer.domain.model.history.RoutePriceTrend;
import com.smarttravel.analyzer.domain.model.lodging.LodgingOffer;
import com.smarttravel.analyzer.domain.model.packagebundle.PackagePart;
import com.smarttravel.analyzer.domain.model.packagebundle.TravelPackage;
import com.smarttravel.analyzer.domain.model.search.TravelOffers;
import com.smarttravel.analyzer.domain.model.shared.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PackageAssemblerDomainService {

    public static final int MAX_PER_DIMENSION = 5;
    private static final double DESTINATION_MEAN_RATING = 8.0;
    private static final int RATING_CONFIDENCE_WEIGHT = 100;

    private final CostNormalizerDomainService normalizer;
    private final ValueScoringDomainService scoring;
    private final PriceTrendDomainService trends;

    public PackageAssemblerDomainService(CostNormalizerDomainService normalizer, ValueScoringDomainService scoring, PriceTrendDomainService trends) {
        this.normalizer = normalizer;
        this.scoring = scoring;
        this.trends = trends;
    }

    public List<TravelPackage> assemble(SearchCriteria criteria, TravelOffers offers) {
        var flights = cheapest(offers.flights().stream()
            .map(offer -> new PackagePart<>(offer, normalizer.normalizeFlight(offer, criteria.checkedBagRequested()))).toList());
        var lodgings = cheapest(offers.lodgings().stream()
            .map(offer -> new PackagePart<>(offer, normalizer.normalizeLodging(offer))).toList());
        var cars = criteria.carRequired()
            ? cheapest(offers.cars().stream().map(offer -> new PackagePart<>(offer, normalizer.normalizeCarRental(offer))).toList())
            : List.<PackagePart<CarRentalOffer>>of();

        if (flights.isEmpty() || lodgings.isEmpty()) return List.of();
        if (criteria.carRequired() && cars.isEmpty()) return List.of();

        var combinations = combine(flights, lodgings, cars);
        if (combinations.isEmpty()) return List.of();

        var trend = trendOver(criteria, combinations);
        return combinations.stream().map(combination -> score(combination, trend)).toList();
    }

    private static <T> List<PackagePart<T>> cheapest(List<PackagePart<T>> parts) {
        return parts.stream()
            .sorted(Comparator.comparing(part -> part.price().totalPrice()))
            .limit(MAX_PER_DIMENSION)
            .toList();
    }

    private static List<Combination> combine(List<PackagePart<FlightOffer>> flights,
                                             List<PackagePart<LodgingOffer>> lodgings,
                                             List<PackagePart<CarRentalOffer>> cars) {
        var combinations = new ArrayList<Combination>();
        for (var flight : flights) {
            for (var lodging : lodgings) {
                if (cars.isEmpty()) {
                    combinations.add(new Combination(flight, lodging, null));
                } else {
                    for (var car : cars) combinations.add(new Combination(flight, lodging, car));
                }
            }
        }
        return combinations;
    }

    private RoutePriceTrend trendOver(SearchCriteria criteria, List<Combination> combinations) {
        var key = criteria.origin() + "-" + criteria.destination();
        var points = combinations.stream()
            .map(combination -> new PriceHistoryPoint(key, criteria.departureDate(), combination.total()))
            .toList();
        return trends.calculateTrend(key, points);
    }

    private TravelPackage score(Combination combination, RoutePriceTrend trend) {
        Money total = combination.total();
        Score price = scoring.priceScore(total, trend);
        Score quality = scoring.calculateBayesianRating(combination.lodging().offer().rating(), DESTINATION_MEAN_RATING, RATING_CONFIDENCE_WEIGHT);
        Score convenience = combination.convenience(scoring);
        return new TravelPackage(combination.id(), total, scoring.calculate(price, quality, convenience),
            quality, convenience, null, combination.flight(), combination.lodging(), combination.car());
    }

    private record Combination(PackagePart<FlightOffer> flight, PackagePart<LodgingOffer> lodging, PackagePart<CarRentalOffer> car) {

        Money total() {
            Money sum = flight.price().totalPrice().add(lodging.price().totalPrice());
            return car == null ? sum : sum.add(car.price().totalPrice());
        }

        String id() {
            var base = flight.offer().id() + "|" + lodging.offer().id();
            return car == null ? base : base + "|" + car.offer().id();
        }

        Score convenience(ValueScoringDomainService scoring) {
            double flightScore = scoring.flightConvenience(flight.offer()).value();
            double lodgingScore = scoring.lodgingConvenience(lodging.offer()).value();
            if (car == null) return new Score((flightScore + lodgingScore) / 2);
            return new Score((flightScore + lodgingScore + scoring.carConvenience(car.offer()).value()) / 3);
        }
    }
}
```

- [ ] **Step 5: Rodar e ver passar**

Run: `mvn test -Dtest=PackageAssemblerDomainServiceTest`
Expected: PASS, 8 testes.

- [ ] **Step 6: Registrar o bean**

Em `BeanConfig.java`, adicionar:

```java
    @Bean PackageAssemblerDomainService packageAssemblerDomainService(CostNormalizerDomainService normalizer, ValueScoringDomainService scoring, PriceTrendDomainService trends) {
        return new PackageAssemblerDomainService(normalizer, scoring, trends);
    }
```

- [ ] **Step 7: Rodar a suíte e commitar**

Run: `mvn test`
Expected: PASS.

```bash
git add src/main/java src/test/java
git commit -m "feat: assemble travel packages from flight, lodging and car offers"
```

---

### Task 6: Filtros no servidor

Os filtros são regra de negócio. Se ficarem no JavaScript, viram duas implementações que divergem.

**Files:**
- Create: `src/main/java/com/smarttravel/analyzer/domain/model/search/PackageFilter.java`
- Create: `src/main/java/com/smarttravel/analyzer/domain/model/search/NeighborhoodSummary.java`
- Create: `src/main/java/com/smarttravel/analyzer/domain/service/PackageFilterDomainService.java`
- Test: `src/test/java/com/smarttravel/analyzer/domain/service/PackageFilterDomainServiceTest.java`

**Interfaces:**
- Consumes: `TravelPackage` (Task 3)
- Produces:
  - `PackageFilter(BigDecimal maxPrice, Double minRating, boolean directFlightOnly, boolean breakfastIncluded, boolean freeCancellation, String neighborhood)` — campos de objeto aceitam `null` (sem filtro)
  - `PackageFilter.none()` → `PackageFilter` com tudo desligado
  - `NeighborhoodSummary(String neighborhood, int packages, Money cheapest)`
  - `PackageFilterDomainService.apply(List<TravelPackage>, PackageFilter)` → `List<TravelPackage>`
  - `PackageFilterDomainService.summarise(List<TravelPackage>)` → `List<NeighborhoodSummary>`, ordenado do bairro mais barato para o mais caro

- [ ] **Step 1: Escrever o teste falhando**

Criar `src/test/java/com/smarttravel/analyzer/domain/service/PackageFilterDomainServiceTest.java`:

```java
package com.smarttravel.analyzer.domain.service;

import com.smarttravel.analyzer.domain.model.flight.*;
import com.smarttravel.analyzer.domain.model.lodging.*;
import com.smarttravel.analyzer.domain.model.packagebundle.*;
import com.smarttravel.analyzer.domain.model.search.NeighborhoodSummary;
import com.smarttravel.analyzer.domain.model.search.PackageFilter;
import com.smarttravel.analyzer.domain.model.shared.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PackageFilterDomainServiceTest {

    private final PackageFilterDomainService filters = new PackageFilterDomainService();

    private static TravelPackage build(String id, String total, double rating, int legs, Set<Amenity> amenities, String neighborhood) {
        var leg = new FlightLeg(new Location("CWB", "Curitiba", "BR"), new Location("REC", "Recife", "BR"),
            Duration.ofHours(2), false, false);
        var flightOffer = new FlightOffer(id + "-f", new Airline("G3", "GOL"), Money.brl("800.00"), Money.brl("0.00"),
            Money.brl("0.00"), legs == 1 ? List.of(leg) : List.of(leg, leg), .9);
        var lodgingOffer = new LodgingOffer(id + "-l", "Hotel " + id, neighborhood, Money.brl("100.00"), 1, Money.brl("0.00"),
            Money.brl("0.00"), Money.brl("0.00"), new HotelRating(rating, 500), amenities, 1.0);
        return new TravelPackage(id, Money.brl(total), new Score(80), new Score(80), new Score(80), null,
            new PackagePart<>(flightOffer, flightOffer.normalize(false)),
            new PackagePart<>(lodgingOffer, lodgingOffer.normalize()), null);
    }

    private static final TravelPackage CHEAP_DIRECT = build("cheap", "900.00", 9.0, 1, Set.of(Amenity.BREAKFAST_INCLUDED), "Boa Viagem");
    private static final TravelPackage PRICEY_STOPOVER = build("pricey", "2500.00", 7.0, 2, Set.of(Amenity.FREE_FLEXIBLE_CANCELLATION), "Centro");

    private static final List<TravelPackage> ALL = List.of(CHEAP_DIRECT, PRICEY_STOPOVER);

    @Test void noFilterKeepsEveryPackage() {
        assertThat(filters.apply(ALL, PackageFilter.none())).containsExactlyElementsOf(ALL);
    }

    @Test void maxPriceDropsPackagesAboveTheCeiling() {
        var filter = new PackageFilter(new BigDecimal("1000.00"), null, false, false, false, null);
        assertThat(filters.apply(ALL, filter)).containsExactly(CHEAP_DIRECT);
    }

    @Test void minRatingDropsPackagesBelowTheFloor() {
        var filter = new PackageFilter(null, 8.5, false, false, false, null);
        assertThat(filters.apply(ALL, filter)).containsExactly(CHEAP_DIRECT);
    }

    @Test void directFlightOnlyDropsPackagesWithConnections() {
        var filter = new PackageFilter(null, null, true, false, false, null);
        assertThat(filters.apply(ALL, filter)).containsExactly(CHEAP_DIRECT);
    }

    @Test void breakfastFilterKeepsOnlyLodgingWithBreakfast() {
        var filter = new PackageFilter(null, null, false, true, false, null);
        assertThat(filters.apply(ALL, filter)).containsExactly(CHEAP_DIRECT);
    }

    @Test void freeCancellationFilterKeepsOnlyFlexibleLodging() {
        var filter = new PackageFilter(null, null, false, false, true, null);
        assertThat(filters.apply(ALL, filter)).containsExactly(PRICEY_STOPOVER);
    }

    @Test void combinedFiltersMayLeaveNothing() {
        var filter = new PackageFilter(new BigDecimal("1000.00"), null, false, false, true, null);
        assertThat(filters.apply(ALL, filter)).isEmpty();
    }

    @Test void neighborhoodFilterKeepsOnlyPackagesInThatNeighborhood() {
        var filter = new PackageFilter(null, null, false, false, false, "Boa Viagem");
        assertThat(filters.apply(ALL, filter)).containsExactly(CHEAP_DIRECT);
    }

    @Test void neighborhoodFilterIgnoresAccentsAndCase() {
        var filter = new PackageFilter(null, null, false, false, false, "boa viagem");
        assertThat(filters.apply(ALL, filter)).containsExactly(CHEAP_DIRECT);
    }

    @Test void summariseGroupsPackagesByNeighborhoodWithCountAndCheapestPrice() {
        var summaries = filters.summarise(ALL);
        assertThat(summaries).hasSize(2);
        assertThat(summaries.getFirst().neighborhood()).isEqualTo("Boa Viagem");
        assertThat(summaries.getFirst().packages()).isEqualTo(1);
        assertThat(summaries.getFirst().cheapest().amount()).isEqualByComparingTo("900.00");
    }

    @Test void summariesComeSortedByCheapestPriceSoTheBestAreaIsFirst() {
        assertThat(filters.summarise(ALL))
            .extracting(NeighborhoodSummary::neighborhood)
            .containsExactly("Boa Viagem", "Centro");
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn test -Dtest=PackageFilterDomainServiceTest`
Expected: erro de compilação — `PackageFilter` e `PackageFilterDomainService` não existem.

- [ ] **Step 3: Criar NeighborhoodSummary e PackageFilter**

Criar `src/main/java/com/smarttravel/analyzer/domain/model/search/NeighborhoodSummary.java`:

```java
package com.smarttravel.analyzer.domain.model.search;

import com.smarttravel.analyzer.domain.model.shared.Money;

public record NeighborhoodSummary(String neighborhood, int packages, Money cheapest) {}
```

Criar `src/main/java/com/smarttravel/analyzer/domain/model/search/PackageFilter.java`:

```java
package com.smarttravel.analyzer.domain.model.search;

import java.math.BigDecimal;

public record PackageFilter(BigDecimal maxPrice, Double minRating, boolean directFlightOnly,
                            boolean breakfastIncluded, boolean freeCancellation, String neighborhood) {
    public static PackageFilter none() { return new PackageFilter(null, null, false, false, false, null); }
}
```

- [ ] **Step 4: Implementar o serviço**

Criar `src/main/java/com/smarttravel/analyzer/domain/service/PackageFilterDomainService.java`:

```java
package com.smarttravel.analyzer.domain.service;

import com.smarttravel.analyzer.domain.model.lodging.Amenity;
import com.smarttravel.analyzer.domain.model.packagebundle.TravelPackage;
import com.smarttravel.analyzer.domain.model.search.NeighborhoodSummary;
import com.smarttravel.analyzer.domain.model.search.PackageFilter;
import com.smarttravel.analyzer.domain.model.shared.Money;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PackageFilterDomainService {

    public List<TravelPackage> apply(List<TravelPackage> packages, PackageFilter filter) {
        return packages.stream().filter(candidate -> matches(candidate, filter)).toList();
    }

    /** Bairros presentes nos pacotes, do mais barato para o mais caro. Nada é cadastrado por cidade. */
    public List<NeighborhoodSummary> summarise(List<TravelPackage> packages) {
        Map<String, List<TravelPackage>> grouped = new LinkedHashMap<>();
        for (var candidate : packages) {
            grouped.computeIfAbsent(candidate.lodging().offer().neighborhood(), key -> new java.util.ArrayList<>()).add(candidate);
        }
        return grouped.entrySet().stream()
            .map(entry -> new NeighborhoodSummary(entry.getKey(), entry.getValue().size(), cheapest(entry.getValue())))
            .sorted(Comparator.comparing(NeighborhoodSummary::cheapest))
            .toList();
    }

    private static Money cheapest(List<TravelPackage> packages) {
        return packages.stream().map(TravelPackage::totalPrice).min(Comparator.naturalOrder()).orElseThrow();
    }

    /** Compara sem acento e sem caixa: "boa viagem" acha "Boa Viagem". */
    static String fold(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "").trim().toLowerCase(java.util.Locale.ROOT);
    }

    private boolean matches(TravelPackage candidate, PackageFilter filter) {
        if (filter.maxPrice() != null && candidate.totalPrice().amount().compareTo(filter.maxPrice()) > 0) return false;
        if (filter.minRating() != null && candidate.lodging().offer().rating().average() < filter.minRating()) return false;
        if (filter.directFlightOnly() && candidate.flight().offer().stops() > 0) return false;
        if (filter.breakfastIncluded() && !candidate.lodging().offer().has(Amenity.BREAKFAST_INCLUDED)) return false;
        if (filter.neighborhood() != null && !filter.neighborhood().isBlank()
            && !fold(candidate.lodging().offer().neighborhood()).equals(fold(filter.neighborhood()))) return false;
        return !filter.freeCancellation() || candidate.lodging().offer().has(Amenity.FREE_FLEXIBLE_CANCELLATION);
    }
}
```

- [ ] **Step 5: Rodar e ver passar**

Run: `mvn test -Dtest=PackageFilterDomainServiceTest`
Expected: PASS, 11 testes.

- [ ] **Step 6: Registrar o bean e commitar**

Em `BeanConfig.java`: `@Bean PackageFilterDomainService packageFilterDomainService() { return new PackageFilterDomainService(); }`

Run: `mvn test`
Expected: PASS.

```bash
git add src/main/java src/test/java
git commit -m "feat: filter assembled packages on the server side"
```

---

### Task 7: "Por que este pacote?"

O domínio já calcula tudo o que justifica a escolha e joga fora. Esta é a diferença entre o app e qualquer comparador de preço.

**Files:**
- Create: `src/main/java/com/smarttravel/analyzer/domain/service/PackageExplanationDomainService.java`
- Test: `src/test/java/com/smarttravel/analyzer/domain/service/PackageExplanationDomainServiceTest.java`

**Interfaces:**
- Consumes: `TravelPackage` (Task 3)
- Produces:
  - `PackageExplanationDomainService.explain(TravelPackage chosen, List<TravelPackage> allCandidates)` → `String` em português
  - `PackageExplanationDomainService.translateAdjustment(String code)` → `String` em português

Códigos de ajuste que existem hoje no domínio, e sua tradução:

| código | texto |
|---|---|
| `mandatory_airport_taxes` | taxa de embarque |
| `checked_baggage` | bagagem despachada |
| `service_fees` | taxa de serviço |
| `city_taxes` | taxa municipal |
| `resort_fees` | taxa de resort |
| `airport_pickup_fee` | retirada no aeroporto |
| `zero_deductible_full_coverage` | seguro sem franquia |

- [ ] **Step 1: Escrever o teste falhando**

Criar `src/test/java/com/smarttravel/analyzer/domain/service/PackageExplanationDomainServiceTest.java`:

```java
package com.smarttravel.analyzer.domain.service;

import com.smarttravel.analyzer.domain.model.flight.*;
import com.smarttravel.analyzer.domain.model.lodging.*;
import com.smarttravel.analyzer.domain.model.packagebundle.*;
import com.smarttravel.analyzer.domain.model.shared.*;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PackageExplanationDomainServiceTest {

    private final PackageExplanationDomainService explanations = new PackageExplanationDomainService();

    private static TravelPackage build(String id, String total, double rating, int reviews, double quality) {
        var flightOffer = new FlightOffer(id + "-f", new Airline("G3", "GOL"), Money.brl("800.00"), Money.brl("90.00"),
            Money.brl("0.00"), List.of(new FlightLeg(new Location("CWB", "Curitiba", "BR"),
            new Location("REC", "Recife", "BR"), null, false, false)), .9);
        var lodgingOffer = new LodgingOffer(id + "-l", "Hotel " + id, "Centro", Money.brl("100.00"), 1, Money.brl("30.00"),
            Money.brl("0.00"), Money.brl("0.00"), new HotelRating(rating, reviews), Set.of(Amenity.BREAKFAST_INCLUDED), 1.0);
        return new TravelPackage(id, Money.brl(total), new Score(85), new Score(quality), new Score(80), null,
            new PackagePart<>(flightOffer, flightOffer.normalize(false)),
            new PackagePart<>(lodgingOffer, lodgingOffer.normalize()), null);
    }

    @Test void translatesEveryCostAdjustmentCodeToPortuguese() {
        assertThat(explanations.translateAdjustment("mandatory_airport_taxes")).isEqualTo("taxa de embarque");
        assertThat(explanations.translateAdjustment("zero_deductible_full_coverage")).isEqualTo("seguro sem franquia");
    }

    @Test void unknownAdjustmentCodesFallBackToTheRawCode() {
        assertThat(explanations.translateAdjustment("mystery_fee")).isEqualTo("mystery_fee");
    }

    @Test void explanationMentionsTheHiddenCostTheUserWouldNotHaveSeen() {
        var chosen = build("a", "1020.00", 8.9, 2000, 90);
        assertThat(explanations.explain(chosen, List.of(chosen)))
            .contains("R$ 120,00")
            .contains("taxa de embarque")
            .contains("taxa de serviço");
    }

    @Test void explanationCreditsReviewVolumeWhenTheChosenHotelIsNotTheTopRated() {
        var chosen = build("chosen", "1020.00", 8.9, 2000, 90);
        var flashy = build("flashy", "1020.00", 10.0, 3, 60);
        assertThat(explanations.explain(chosen, List.of(chosen, flashy)))
            .contains("2.000 avaliações")
            .contains("3 avaliações");
    }

    @Test void explanationSaysItIsTheCheapestWhenItActuallyIs() {
        var chosen = build("chosen", "1020.00", 8.9, 2000, 90);
        var pricier = build("pricier", "2500.00", 8.9, 2000, 90);
        assertThat(explanations.explain(chosen, List.of(chosen, pricier))).contains("mais barato");
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn test -Dtest=PackageExplanationDomainServiceTest`
Expected: erro de compilação — `PackageExplanationDomainService` não existe.

- [ ] **Step 3: Implementar**

Criar `src/main/java/com/smarttravel/analyzer/domain/service/PackageExplanationDomainService.java`:

```java
package com.smarttravel.analyzer.domain.service;

import com.smarttravel.analyzer.domain.model.packagebundle.TravelPackage;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PackageExplanationDomainService {

    private static final Locale BR = Locale.forLanguageTag("pt-BR");

    private static final Map<String, String> ADJUSTMENTS = Map.of(
        "mandatory_airport_taxes", "taxa de embarque",
        "checked_baggage", "bagagem despachada",
        "service_fees", "taxa de serviço",
        "city_taxes", "taxa municipal",
        "resort_fees", "taxa de resort",
        "airport_pickup_fee", "retirada no aeroporto",
        "zero_deductible_full_coverage", "seguro sem franquia");

    public String translateAdjustment(String code) { return ADJUSTMENTS.getOrDefault(code, code); }

    public String explain(TravelPackage chosen, List<TravelPackage> candidates) {
        var reasons = new java.util.ArrayList<String>();

        var hidden = chosen.hiddenCosts().amount();
        if (hidden.compareTo(BigDecimal.ZERO) > 0) {
            var fees = chosen.costAdjustments().stream().map(this::translateAdjustment).toList();
            reasons.add("O preço anunciado esconde " + money(hidden) + " em " + String.join(", ", fees) + ".");
        }

        boolean cheapest = candidates.stream().noneMatch(other -> other.totalPrice().compareTo(chosen.totalPrice()) < 0);
        if (cheapest) reasons.add("É o mais barato depois de somar tudo.");

        var topRated = candidates.stream()
            .max(Comparator.comparingDouble(candidate -> candidate.lodging().offer().rating().average()))
            .orElse(chosen);
        var chosenRating = chosen.lodging().offer().rating();
        var topRating = topRated.lodging().offer().rating();
        if (topRating.average() > chosenRating.average() && chosenRating.reviewCount() > topRating.reviewCount()) {
            reasons.add("A nota " + number(chosenRating.average()) + " com " + count(chosenRating.reviewCount())
                + " avaliações é mais confiável que a nota " + number(topRating.average())
                + " com " + count(topRating.reviewCount()) + " avaliações.");
        }

        return String.join(" ", reasons);
    }

    private static String money(BigDecimal amount) { return NumberFormat.getCurrencyInstance(BR).format(amount); }
    private static String number(double value) { return NumberFormat.getNumberInstance(BR).format(value); }
    private static String count(int value) { return NumberFormat.getIntegerInstance(BR).format(value); }
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `mvn test -Dtest=PackageExplanationDomainServiceTest`
Expected: PASS, 5 testes.

Se a asserção de `R$ 120,00` falhar por causa do espaço entre `R$` e o número (a JDK usa espaço não separável, ` `), ajuste o teste para `.contains("120,00")` em vez de mudar a implementação — o formato do JDK está correto.

- [ ] **Step 5: Registrar o bean e commitar**

Em `BeanConfig.java`: `@Bean PackageExplanationDomainService packageExplanationDomainService() { return new PackageExplanationDomainService(); }`

Run: `mvn test`
Expected: PASS.

```bash
git add src/main/java src/test/java
git commit -m "feat: explain in plain Portuguese why a package won"
```

---

### Task 8: Links de reserva

Função pura: monta a URL de busca do parceiro já preenchida. Sem rede, sem TTL, sem persistência.

**Files:**
- Create: `src/main/java/com/smarttravel/analyzer/domain/link/DeepLinkBuilder.java`
- Create: `src/main/java/com/smarttravel/analyzer/infrastructure/adapter/link/BookingDeepLinkBuilder.java`
- Create: `src/main/java/com/smarttravel/analyzer/infrastructure/adapter/link/GoogleFlightsDeepLinkBuilder.java`
- Test: `src/test/java/com/smarttravel/analyzer/infrastructure/adapter/link/DeepLinkBuilderTest.java`

**Interfaces:**
- Consumes: `SearchCriteria` (já existe)
- Produces:
  - `DeepLinkBuilder` — interface com `String partnerName()` e `URI searchUrl(SearchCriteria criteria)`
  - `BookingDeepLinkBuilder`, `GoogleFlightsDeepLinkBuilder` — `@Component`

- [ ] **Step 1: Escrever o teste falhando**

Criar `src/test/java/com/smarttravel/analyzer/infrastructure/adapter/link/DeepLinkBuilderTest.java`:

```java
package com.smarttravel.analyzer.infrastructure.adapter.link;

import com.smarttravel.analyzer.domain.model.shared.SearchCriteria;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeepLinkBuilderTest {

    private static SearchCriteria criteria(String destination) {
        return new SearchCriteria("CWB", destination, LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 13), 2, true, false);
    }

    @Test void bookingUrlCarriesDestinationDatesAndTravelers() {
        var url = new BookingDeepLinkBuilder().searchUrl(criteria("REC")).toString();
        assertThat(url).startsWith("https://www.booking.com/searchresults.html?")
            .contains("ss=REC")
            .contains("checkin=2026-11-10")
            .contains("checkout=2026-11-13")
            .contains("group_adults=2");
    }

    @Test void bookingUrlEncodesAccentsAndSpaces() {
        assertThat(new BookingDeepLinkBuilder().searchUrl(criteria("São Paulo")).toString())
            .contains("ss=S%C3%A3o+Paulo");
    }

    @Test void bookingUrlEncodesAmpersandSoItCannotInjectAnotherParameter() {
        assertThat(new BookingDeepLinkBuilder().searchUrl(criteria("Rio&Bahia")).toString())
            .contains("ss=Rio%26Bahia")
            .doesNotContain("ss=Rio&Bahia");
    }

    @Test void googleFlightsUrlCarriesBothAirportsAndBothDates() {
        var url = new GoogleFlightsDeepLinkBuilder().searchUrl(criteria("REC")).toString();
        assertThat(url).startsWith("https://www.google.com/travel/flights?q=")
            .contains("CWB").contains("REC").contains("2026-11-10").contains("2026-11-13");
    }

    @Test void everyBuilderNamesItsPartner() {
        assertThat(new BookingDeepLinkBuilder().partnerName()).isEqualTo("Booking.com");
        assertThat(new GoogleFlightsDeepLinkBuilder().partnerName()).isEqualTo("Google Flights");
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn test -Dtest=DeepLinkBuilderTest`
Expected: erro de compilação — as classes não existem.

- [ ] **Step 3: Criar a porta**

Criar `src/main/java/com/smarttravel/analyzer/domain/link/DeepLinkBuilder.java`:

```java
package com.smarttravel.analyzer.domain.link;

import com.smarttravel.analyzer.domain.model.shared.SearchCriteria;
import java.net.URI;

public interface DeepLinkBuilder {
    String partnerName();
    URI searchUrl(SearchCriteria criteria);
}
```

- [ ] **Step 4: Implementar os dois builders**

Criar `src/main/java/com/smarttravel/analyzer/infrastructure/adapter/link/BookingDeepLinkBuilder.java`:

```java
package com.smarttravel.analyzer.infrastructure.adapter.link;

import com.smarttravel.analyzer.domain.link.DeepLinkBuilder;
import com.smarttravel.analyzer.domain.model.shared.SearchCriteria;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Component
public class BookingDeepLinkBuilder implements DeepLinkBuilder {

    @Override public String partnerName() { return "Booking.com"; }

    @Override public URI searchUrl(SearchCriteria criteria) {
        return URI.create("https://www.booking.com/searchresults.html"
            + "?ss=" + encode(criteria.destination())
            + "&checkin=" + criteria.departureDate()
            + "&checkout=" + criteria.returnDate()
            + "&group_adults=" + criteria.travelers());
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
```

Criar `src/main/java/com/smarttravel/analyzer/infrastructure/adapter/link/GoogleFlightsDeepLinkBuilder.java`:

```java
package com.smarttravel.analyzer.infrastructure.adapter.link;

import com.smarttravel.analyzer.domain.link.DeepLinkBuilder;
import com.smarttravel.analyzer.domain.model.shared.SearchCriteria;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Component
public class GoogleFlightsDeepLinkBuilder implements DeepLinkBuilder {

    @Override public String partnerName() { return "Google Flights"; }

    @Override public URI searchUrl(SearchCriteria criteria) {
        var query = "Flights from " + criteria.origin() + " to " + criteria.destination()
            + " on " + criteria.departureDate() + " through " + criteria.returnDate();
        return URI.create("https://www.google.com/travel/flights?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8));
    }
}
```

- [ ] **Step 5: Rodar e ver passar**

Run: `mvn test -Dtest=DeepLinkBuilderTest`
Expected: PASS, 5 testes.

- [ ] **Step 6: Commit**

```bash
git add src/main/java src/test/java
git commit -m "feat: build partner booking links as a pure function"
```

---

### Task 9: Providers de demonstração atrás das portas existentes

As portas existem e não têm nenhuma implementação. Enquanto não há fonte real, os demos preenchem o buraco — e ficam marcados como demo para a tela poder avisar.

**Files:**
- Modify: `src/main/java/com/smarttravel/analyzer/domain/repository/FlightProviderPort.java`
- Modify: `src/main/java/com/smarttravel/analyzer/domain/repository/LodgingProviderPort.java`
- Modify: `src/main/java/com/smarttravel/analyzer/domain/repository/CarRentalProviderPort.java`
- Create: `src/main/java/com/smarttravel/analyzer/infrastructure/adapter/demo/DemoFlightProvider.java`
- Create: `src/main/java/com/smarttravel/analyzer/infrastructure/adapter/demo/DemoLodgingProvider.java`
- Create: `src/main/java/com/smarttravel/analyzer/infrastructure/adapter/demo/DemoCarRentalProvider.java`
- Test: `src/test/java/com/smarttravel/analyzer/infrastructure/adapter/demo/DemoProvidersTest.java`

**Interfaces:**
- Consumes: `SearchCriteria`, `Money.brl` (Task 2)
- Produces:
  - `FlightProviderPort.isDemo()`, `LodgingProviderPort.isDemo()`, `CarRentalProviderPort.isDemo()` — `default` retornando `false`
  - `DemoFlightProvider`, `DemoLodgingProvider`, `DemoCarRentalProvider` — `@Component`, `isDemo()` retorna `true`, cada um devolve 6 ofertas determinísticas

**Sobre o contract test:** o spec pede um contract test abstrato por porta, com os demos estendendo a mesma bateria. Aqui existe uma implementação por porta, então a classe base abstrata não teria segunda implementação para provar nada — ela nasce quando o primeiro provider real chegar, e aí as asserções de `DemoProvidersTest` que valem para qualquer fonte (moeda BRL, noites iguais à duração da viagem, origem pedida) sobem para a base. Não crie a hierarquia agora.

**Determinismo:** a semente é `criteria.hashCode()` (record → hashCode de todos os campos). A mesma busca devolve sempre o mesmo resultado, o que torna o teste estável e a tela previsível ao refazer a busca.

- [ ] **Step 1: Escrever o teste falhando**

Criar `src/test/java/com/smarttravel/analyzer/infrastructure/adapter/demo/DemoProvidersTest.java`:

```java
package com.smarttravel.analyzer.infrastructure.adapter.demo;

import com.smarttravel.analyzer.domain.model.shared.*;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DemoProvidersTest {

    private static final SearchCriteria CRITERIA =
        new SearchCriteria("CWB", "REC", LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 13), 2, true, true);

    @Test void everyDemoProviderDeclaresItselfAsDemo() {
        assertThat(new DemoFlightProvider().isDemo()).isTrue();
        assertThat(new DemoLodgingProvider().isDemo()).isTrue();
        assertThat(new DemoCarRentalProvider().isDemo()).isTrue();
    }

    @Test void flightProviderReturnsSeveralOffersAllInBrl() {
        var offers = new DemoFlightProvider().searchFlights(CRITERIA);
        assertThat(offers).hasSize(6);
        assertThat(offers).allSatisfy(offer -> assertThat(offer.rawFare().currency()).isEqualTo(Money.BRL));
    }

    @Test void flightOffersDepartFromTheRequestedOrigin() {
        assertThat(new DemoFlightProvider().searchFlights(CRITERIA))
            .allSatisfy(offer -> assertThat(offer.legs().getFirst().origin().code()).isEqualTo("CWB"));
    }

    @Test void lodgingNightsMatchTheTripLength() {
        assertThat(new DemoLodgingProvider().searchLodging(CRITERIA))
            .allSatisfy(offer -> assertThat(offer.nights()).isEqualTo(3));
    }

    @Test void carRentalDaysMatchTheTripLength() {
        assertThat(new DemoCarRentalProvider().searchCars(CRITERIA))
            .allSatisfy(offer -> assertThat(offer.rentalDays()).isEqualTo(3));
    }

    @Test void theSameSearchAlwaysReturnsTheSameOffers() {
        var first = new DemoLodgingProvider().searchLodging(CRITERIA);
        var second = new DemoLodgingProvider().searchLodging(CRITERIA);
        assertThat(first).isEqualTo(second);
    }

    @Test void differentSearchesReturnDifferentOffers() {
        var other = new SearchCriteria("CWB", "SSA", LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 13), 2, true, true);
        assertThat(new DemoLodgingProvider().searchLodging(CRITERIA))
            .isNotEqualTo(new DemoLodgingProvider().searchLodging(other));
    }

    @Test void lodgingOffersSpreadAcrossSeveralNeighborhoodsWithMoreThanOnePerArea() {
        var neighborhoods = new DemoLodgingProvider().searchLodging(CRITERIA).stream()
            .map(offer -> offer.neighborhood()).toList();
        assertThat(neighborhoods).doesNotContainNull();
        assertThat(java.util.Set.copyOf(neighborhoods)).hasSizeGreaterThan(2).hasSizeLessThan(neighborhoods.size());
    }

    @Test void atLeastOneFlightIsDirectAndAtLeastOneHasAConnection() {
        var offers = new DemoFlightProvider().searchFlights(CRITERIA);
        assertThat(offers).anySatisfy(offer -> assertThat(offer.stops()).isZero());
        assertThat(offers).anySatisfy(offer -> assertThat(offer.stops()).isPositive());
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn test -Dtest=DemoProvidersTest`
Expected: erro de compilação — as classes não existem.

- [ ] **Step 3: Adicionar isDemo às três portas**

Em cada uma das três portas, acrescentar o método default. Exemplo para `LodgingProviderPort.java`:

```java
public interface LodgingProviderPort {
    List<LodgingOffer> searchLodging(SearchCriteria criteria);
    default boolean isDemo() { return false; }
}
```

Fazer o mesmo em `FlightProviderPort` (método `searchFlights`) e `CarRentalProviderPort` (método `searchCars`).

- [ ] **Step 4: Implementar DemoFlightProvider**

Criar `src/main/java/com/smarttravel/analyzer/infrastructure/adapter/demo/DemoFlightProvider.java`:

```java
package com.smarttravel.analyzer.infrastructure.adapter.demo;

import com.smarttravel.analyzer.domain.model.flight.*;
import com.smarttravel.analyzer.domain.model.shared.*;
import com.smarttravel.analyzer.domain.repository.FlightProviderPort;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.springframework.stereotype.Component;

@Component
public class DemoFlightProvider implements FlightProviderPort {

    private static final List<Airline> AIRLINES = List.of(
        new Airline("G3", "GOL"), new Airline("AD", "Azul"), new Airline("LA", "LATAM"));

    @Override public boolean isDemo() { return true; }

    @Override public List<FlightOffer> searchFlights(SearchCriteria criteria) {
        var random = new Random(criteria.hashCode());
        var offers = new ArrayList<FlightOffer>();
        for (int index = 0; index < 6; index++) {
            var airline = AIRLINES.get(index % AIRLINES.size());
            boolean direct = index % 2 == 0;
            var fare = money(420 + random.nextInt(680));
            offers.add(new FlightOffer("demo-fl-" + index, airline, fare, money(60 + random.nextInt(60)),
                money(70 + random.nextInt(50)), legs(criteria, direct), 0.75 + random.nextDouble() * 0.24));
        }
        return List.copyOf(offers);
    }

    private static List<FlightLeg> legs(SearchCriteria criteria, boolean direct) {
        var origin = new Location(criteria.origin(), criteria.origin(), "BR");
        var destination = new Location(criteria.destination(), criteria.destination(), "BR");
        if (direct) return List.of(new FlightLeg(origin, destination, null, false, false));
        var hub = new Location("GRU", "São Paulo", "BR");
        return List.of(new FlightLeg(origin, hub, Duration.ofMinutes(110), false, false),
                       new FlightLeg(hub, destination, null, false, false));
    }

    private static Money money(int amount) { return new Money(BigDecimal.valueOf(amount), Money.BRL); }
}
```

- [ ] **Step 5: Implementar DemoLodgingProvider**

Criar `src/main/java/com/smarttravel/analyzer/infrastructure/adapter/demo/DemoLodgingProvider.java`:

```java
package com.smarttravel.analyzer.infrastructure.adapter.demo;

import com.smarttravel.analyzer.domain.model.lodging.*;
import com.smarttravel.analyzer.domain.model.shared.*;
import com.smarttravel.analyzer.domain.repository.LodgingProviderPort;
import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class DemoLodgingProvider implements LodgingProviderPort {

    private static final List<String> NAMES = List.of("Pousada Maré Alta", "Hotel Centro Histórico",
        "Apartamento Beira-Mar", "Hostel do Porto", "Casa de Temporada Jardim", "Apart-Hotel Executivo");

    private static final List<String> NEIGHBORHOODS = List.of("Boa Viagem", "Centro",
        "Boa Viagem", "Recife Antigo", "Espinheiro", "Centro");

    @Override public boolean isDemo() { return true; }

    @Override public List<LodgingOffer> searchLodging(SearchCriteria criteria) {
        var random = new Random(criteria.hashCode());
        int nights = nights(criteria);
        var offers = new ArrayList<LodgingOffer>();
        for (int index = 0; index < NAMES.size(); index++) {
            var amenities = EnumSet.noneOf(Amenity.class);
            if (index % 2 == 0) amenities.add(Amenity.BREAKFAST_INCLUDED);
            if (index % 3 != 0) amenities.add(Amenity.FREE_FLEXIBLE_CANCELLATION);
            if (index % 3 == 0) amenities.add(Amenity.WALKABLE_ATTRACTIONS);
            offers.add(new LodgingOffer("demo-lo-" + index, NAMES.get(index), NEIGHBORHOODS.get(index), money(110 + random.nextInt(320)), nights,
                money(random.nextInt(60)), money(random.nextInt(35)), money(index % 4 == 0 ? 45 : 0),
                new HotelRating(round(7.2 + random.nextDouble() * 2.6), 40 + random.nextInt(2400)),
                Set.copyOf(amenities), round(random.nextDouble() * 4)));
        }
        return List.copyOf(offers);
    }

    static int nights(SearchCriteria criteria) {
        return Math.max(1, (int) ChronoUnit.DAYS.between(criteria.departureDate(), criteria.returnDate()));
    }

    private static double round(double value) { return Math.round(value * 10) / 10.0; }

    private static Money money(int amount) { return new Money(BigDecimal.valueOf(amount), Money.BRL); }
}
```

- [ ] **Step 6: Implementar DemoCarRentalProvider**

Criar `src/main/java/com/smarttravel/analyzer/infrastructure/adapter/demo/DemoCarRentalProvider.java`:

```java
package com.smarttravel.analyzer.infrastructure.adapter.demo;

import com.smarttravel.analyzer.domain.model.carrental.*;
import com.smarttravel.analyzer.domain.model.shared.*;
import com.smarttravel.analyzer.domain.repository.CarRentalProviderPort;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.springframework.stereotype.Component;

@Component
public class DemoCarRentalProvider implements CarRentalProviderPort {

    private static final List<String> SUPPLIERS = List.of("Movida", "Localiza", "Unidas", "Rentcars");
    private static final List<CarCategory> CATEGORIES =
        List.of(CarCategory.ECONOMY, CarCategory.COMPACT, CarCategory.SUV, CarCategory.PREMIUM);

    @Override public boolean isDemo() { return true; }

    @Override public List<CarRentalOffer> searchCars(SearchCriteria criteria) {
        var random = new Random(criteria.hashCode());
        int days = DemoLodgingProvider.nights(criteria);
        var offers = new ArrayList<CarRentalOffer>();
        for (int index = 0; index < 4; index++) {
            var coverage = index % 2 == 0 ? InsuranceCoverageType.BASIC_CDW_TP : InsuranceCoverageType.ZERO_DEDUCTIBLE_FULL_COVERAGE;
            offers.add(new CarRentalOffer("demo-ca-" + index, SUPPLIERS.get(index), CATEGORIES.get(index),
                money(70 + random.nextInt(190)), days, coverage, money(22 + random.nextInt(25)),
                money(index % 2 == 0 ? 55 : 0), true,
                index % 2 == 0 ? PickupMode.IN_TERMINAL : PickupMode.SHUTTLE, 0.7 + random.nextDouble() * 0.29));
        }
        return List.copyOf(offers);
    }

    private static Money money(int amount) { return new Money(BigDecimal.valueOf(amount), Money.BRL); }
}
```

- [ ] **Step 7: Rodar e ver passar**

Run: `mvn test -Dtest=DemoProvidersTest`
Expected: PASS, 9 testes.

- [ ] **Step 8: Rodar a suíte e commitar**

Run: `mvn test`
Expected: PASS.

```bash
git add src/main/java src/test/java
git commit -m "feat: add demo providers behind the existing offer ports"
```

---

### Task 10: Busca assíncrona com estado por fonte

Busca demorada não pode ser um POST pendurado até o navegador desistir. E fonte que falha não pode derrubar a busca inteira.

**Files:**
- Create: `src/main/java/com/smarttravel/analyzer/domain/model/search/SearchStatus.java`
- Create: `src/main/java/com/smarttravel/analyzer/domain/model/search/SourceHealth.java`
- Create: `src/main/java/com/smarttravel/analyzer/domain/model/search/SourceStatus.java`
- Create: `src/main/java/com/smarttravel/analyzer/application/search/SearchSession.java`
- Create: `src/main/java/com/smarttravel/analyzer/application/search/SearchSessionStore.java`
- Create: `src/main/java/com/smarttravel/analyzer/application/service/SearchRunner.java`
- Test: `src/test/java/com/smarttravel/analyzer/application/service/SearchRunnerTest.java`

**Interfaces:**
- Consumes: `PackageAssemblerDomainService` (Task 4), `PackageBundlerDomainService`, os três `*Port` (Task 8)
- Produces:
  - `SearchStatus` — `BUSCANDO, PRONTO, PARCIAL, ERRO`
  - `SourceHealth` — `BUSCANDO, OK, DEGRADADO`
  - `SourceStatus(String source, SourceHealth health, int offers, String message)`
  - `SearchSession` — `id()`, `criteria()`, `status()`, `sources()`, `packages()`, `demo()`, `createdAt()`
  - `SearchSessionStore.create(SearchCriteria)` → `SearchSession`; `.find(String id)` → `Optional<SearchSession>`; `.purgeExpired()`
  - `SearchRunner.run(SearchSession)` → `void` (preenche a sessão; nunca lança)

**Regra de status:** `ERRO` se nenhum pacote pôde ser montado; `PARCIAL` se alguma fonte ficou `DEGRADADO` mas há pacotes; `PRONTO` caso contrário.

- [ ] **Step 1: Escrever o teste falhando**

Criar `src/test/java/com/smarttravel/analyzer/application/service/SearchRunnerTest.java`:

```java
package com.smarttravel.analyzer.application.service;

import com.smarttravel.analyzer.application.search.SearchSession;
import com.smarttravel.analyzer.domain.model.carrental.CarRentalOffer;
import com.smarttravel.analyzer.domain.model.flight.FlightOffer;
import com.smarttravel.analyzer.domain.model.lodging.LodgingOffer;
import com.smarttravel.analyzer.domain.model.search.*;
import com.smarttravel.analyzer.domain.model.shared.SearchCriteria;
import com.smarttravel.analyzer.domain.repository.*;
import com.smarttravel.analyzer.domain.service.*;
import com.smarttravel.analyzer.infrastructure.adapter.demo.*;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchRunnerTest {

    private static final SearchCriteria CRITERIA =
        new SearchCriteria("CWB", "REC", LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 13), 2, true, false);

    private static final SearchCriteria CRITERIA_WITH_CAR =
        new SearchCriteria("CWB", "REC", LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 13), 2, true, true);

    private static SearchRunner runner(FlightProviderPort flights, LodgingProviderPort lodgings, CarRentalProviderPort cars) {
        var scoring = new ValueScoringDomainService();
        var assembler = new PackageAssemblerDomainService(new CostNormalizerDomainService(), scoring, new PriceTrendDomainService());
        return new SearchRunner(flights, lodgings, cars, assembler, new PackageBundlerDomainService());
    }

    private static SearchRunner healthyRunner() {
        return runner(new DemoFlightProvider(), new DemoLodgingProvider(), new DemoCarRentalProvider());
    }

    @Test void aHealthySearchEndsReadyWithThreeRecommendations() {
        var session = new SearchSession("s1", CRITERIA);
        healthyRunner().run(session);
        assertThat(session.status()).isEqualTo(SearchStatus.PRONTO);
        assertThat(session.packages()).hasSize(3);
    }

    @Test void aHealthySearchMarksEverySourceAsOk() {
        var session = new SearchSession("s1", CRITERIA);
        healthyRunner().run(session);
        assertThat(session.sources()).isNotEmpty()
            .allSatisfy(source -> assertThat(source.health()).isEqualTo(SourceHealth.OK));
    }

    @Test void aSearchBuiltOnlyFromDemoProvidersIsFlaggedAsDemo() {
        var session = new SearchSession("s1", CRITERIA);
        healthyRunner().run(session);
        assertThat(session.demo()).isTrue();
    }

    @Test void aFailingCarSourceDegradesTheSourceWithoutBreakingTheSearch() {
        CarRentalProviderPort broken = criteria -> { throw new IllegalStateException("locadora fora do ar"); };
        var session = new SearchSession("s1", CRITERIA_WITH_CAR);
        runner(new DemoFlightProvider(), new DemoLodgingProvider(), broken).run(session);
        assertThat(session.status()).isEqualTo(SearchStatus.PARCIAL);
        assertThat(session.sources()).anySatisfy(source ->
            assertThat(source.health()).isEqualTo(SourceHealth.DEGRADADO));
    }

    @Test void aCarSourceIsNotEvenConsultedWhenTheUserDoesNotWantACar() {
        CarRentalProviderPort broken = criteria -> { throw new IllegalStateException("nunca deveria ser chamado"); };
        var session = new SearchSession("s1", CRITERIA);
        runner(new DemoFlightProvider(), new DemoLodgingProvider(), broken).run(session);
        assertThat(session.status()).isEqualTo(SearchStatus.PRONTO);
        assertThat(session.sources()).extracting(SourceStatus::source).doesNotContain("Carros");
    }

    @Test void aFailingEssentialSourceEndsInErrorInsteadOfThrowing() {
        LodgingProviderPort broken = criteria -> { throw new IllegalStateException("hotelaria fora do ar"); };
        var session = new SearchSession("s1", CRITERIA);
        runner(new DemoFlightProvider(), broken, new DemoCarRentalProvider()).run(session);
        assertThat(session.status()).isEqualTo(SearchStatus.ERRO);
        assertThat(session.packages()).isEmpty();
    }

    @Test void aSourceThatReturnsNothingIsDegradedNotSilentlyIgnored() {
        FlightProviderPort empty = criteria -> List.of();
        var session = new SearchSession("s1", CRITERIA);
        runner(empty, new DemoLodgingProvider(), new DemoCarRentalProvider()).run(session);
        assertThat(session.sources()).anySatisfy(source ->
            assertThat(source.health()).isEqualTo(SourceHealth.DEGRADADO));
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn test -Dtest=SearchRunnerTest`
Expected: erro de compilação — `SearchSession`, `SearchRunner` e os enums não existem.

- [ ] **Step 3: Criar os tipos de estado**

`src/main/java/com/smarttravel/analyzer/domain/model/search/SearchStatus.java`:

```java
package com.smarttravel.analyzer.domain.model.search;
public enum SearchStatus { BUSCANDO, PRONTO, PARCIAL, ERRO }
```

`src/main/java/com/smarttravel/analyzer/domain/model/search/SourceHealth.java`:

```java
package com.smarttravel.analyzer.domain.model.search;
public enum SourceHealth { BUSCANDO, OK, DEGRADADO }
```

`src/main/java/com/smarttravel/analyzer/domain/model/search/SourceStatus.java`:

```java
package com.smarttravel.analyzer.domain.model.search;

public record SourceStatus(String source, SourceHealth health, int offers, String message) {
    public static SourceStatus ok(String source, int offers) { return new SourceStatus(source, SourceHealth.OK, offers, null); }
    public static SourceStatus degraded(String source, String message) { return new SourceStatus(source, SourceHealth.DEGRADADO, 0, message); }
}
```

- [ ] **Step 4: Criar SearchSession**

Criar `src/main/java/com/smarttravel/analyzer/application/search/SearchSession.java`:

```java
package com.smarttravel.analyzer.application.search;

import com.smarttravel.analyzer.domain.model.packagebundle.TravelPackage;
import com.smarttravel.analyzer.domain.model.search.*;
import com.smarttravel.analyzer.domain.model.shared.SearchCriteria;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class SearchSession {

    private final String id;
    private final SearchCriteria criteria;
    private final Instant createdAt = Instant.now();
    private final List<SourceStatus> sources = new CopyOnWriteArrayList<>();
    private volatile SearchStatus status = SearchStatus.BUSCANDO;
    private volatile List<TravelPackage> packages = List.of();
    private volatile boolean demo;
    private volatile List<DateOption> dateOptions = List.of();

    public SearchSession(String id, SearchCriteria criteria) { this.id = id; this.criteria = criteria; }

    public String id() { return id; }
    public SearchCriteria criteria() { return criteria; }
    public Instant createdAt() { return createdAt; }
    public SearchStatus status() { return status; }
    public List<SourceStatus> sources() { return List.copyOf(sources); }
    public List<TravelPackage> packages() { return packages; }
    public boolean demo() { return demo; }
    public List<DateOption> dateOptions() { return dateOptions; }

    public void addSource(SourceStatus source) { sources.add(source); }
    public void status(SearchStatus value) { this.status = value; }
    public void packages(List<TravelPackage> value) { this.packages = List.copyOf(value); }
    public void demo(boolean value) { this.demo = value; }
    public void dateOptions(List<DateOption> value) { this.dateOptions = List.copyOf(value); }
}
```

`DateOption` é criado na Task 11; para esta tarefa compilar, criar já o record em `src/main/java/com/smarttravel/analyzer/application/search/DateOption.java`:

```java
package com.smarttravel.analyzer.application.search;

import com.smarttravel.analyzer.domain.model.shared.Money;
import java.time.LocalDate;

public record DateOption(LocalDate departureDate, LocalDate returnDate, Money total, Money difference) {}
```

- [ ] **Step 5: Criar SearchSessionStore**

Criar `src/main/java/com/smarttravel/analyzer/application/search/SearchSessionStore.java`:

```java
package com.smarttravel.analyzer.application.search;

import com.smarttravel.analyzer.domain.model.shared.SearchCriteria;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class SearchSessionStore {

    static final Duration TTL = Duration.ofMinutes(30);

    private final ConcurrentHashMap<String, SearchSession> sessions = new ConcurrentHashMap<>();

    public SearchSession create(SearchCriteria criteria) {
        purgeExpired();
        var session = new SearchSession(UUID.randomUUID().toString(), criteria);
        sessions.put(session.id(), session);
        return session;
    }

    public Optional<SearchSession> find(String id) { return Optional.ofNullable(sessions.get(id)); }

    public void purgeExpired() {
        var cutoff = Instant.now().minus(TTL);
        sessions.values().removeIf(session -> session.createdAt().isBefore(cutoff));
    }
}
```

- [ ] **Step 6: Implementar SearchRunner**

Criar `src/main/java/com/smarttravel/analyzer/application/service/SearchRunner.java`:

```java
package com.smarttravel.analyzer.application.service;

import com.smarttravel.analyzer.application.search.SearchSession;
import com.smarttravel.analyzer.domain.model.search.*;
import com.smarttravel.analyzer.domain.model.shared.SearchCriteria;
import com.smarttravel.analyzer.domain.repository.*;
import com.smarttravel.analyzer.domain.service.PackageAssemblerDomainService;
import com.smarttravel.analyzer.domain.service.PackageBundlerDomainService;
import java.util.List;
import java.util.function.Function;
import org.springframework.stereotype.Service;

@Service
public class SearchRunner {

    private final FlightProviderPort flights;
    private final LodgingProviderPort lodgings;
    private final CarRentalProviderPort cars;
    private final PackageAssemblerDomainService assembler;
    private final PackageBundlerDomainService bundler;

    public SearchRunner(FlightProviderPort flights, LodgingProviderPort lodgings, CarRentalProviderPort cars,
                        PackageAssemblerDomainService assembler, PackageBundlerDomainService bundler) {
        this.flights = flights;
        this.lodgings = lodgings;
        this.cars = cars;
        this.assembler = assembler;
        this.bundler = bundler;
    }

    public void run(SearchSession session) {
        var criteria = session.criteria();
        session.demo(flights.isDemo() && lodgings.isDemo() && cars.isDemo());

        var flightOffers = collect(session, "Voos", criteria, flights::searchFlights);
        var lodgingOffers = collect(session, "Hospedagem", criteria, lodgings::searchLodging);
        var carOffers = criteria.carRequired()
            ? collect(session, "Carros", criteria, cars::searchCars)
            : List.<com.smarttravel.analyzer.domain.model.carrental.CarRentalOffer>of();

        var candidates = assembler.assemble(criteria, new TravelOffers(flightOffers, lodgingOffers, carOffers));
        if (candidates.isEmpty()) {
            session.packages(List.of());
            session.status(SearchStatus.ERRO);
            return;
        }

        session.packages(bundler.topRecommendations(candidates));
        boolean degraded = session.sources().stream().anyMatch(source -> source.health() == SourceHealth.DEGRADADO);
        session.status(degraded ? SearchStatus.PARCIAL : SearchStatus.PRONTO);
    }

    private <T> List<T> collect(SearchSession session, String name, SearchCriteria criteria, Function<SearchCriteria, List<T>> call) {
        try {
            var offers = call.apply(criteria);
            if (offers.isEmpty()) {
                session.addSource(SourceStatus.degraded(name, "nenhuma oferta encontrada"));
            } else {
                session.addSource(SourceStatus.ok(name, offers.size()));
            }
            return offers;
        } catch (RuntimeException failure) {
            session.addSource(SourceStatus.degraded(name, failure.getMessage()));
            return List.of();
        }
    }
}
```

- [ ] **Step 7: Rodar e ver passar**

Run: `mvn test -Dtest=SearchRunnerTest`
Expected: PASS, 7 testes.

- [ ] **Step 8: Rodar a suíte e commitar**

Run: `mvn test`
Expected: PASS.

```bash
git add src/main/java src/test/java
git commit -m "feat: run searches asynchronously with per-source health"
```

---

### Task 11: API REST da busca

**Files:**
- Create: `src/main/java/com/smarttravel/analyzer/application/dto/SearchStartedResponse.java`
- Create: `src/main/java/com/smarttravel/analyzer/application/dto/SearchResultResponse.java`
- Create: `src/main/java/com/smarttravel/analyzer/application/dto/SourceStatusDTO.java`
- Create: `src/main/java/com/smarttravel/analyzer/application/dto/PackageDTO.java`
- Create: `src/main/java/com/smarttravel/analyzer/application/dto/BookingLinkDTO.java`
- Create: `src/main/java/com/smarttravel/analyzer/application/dto/DateOptionDTO.java`
- Create: `src/main/java/com/smarttravel/analyzer/application/dto/NeighborhoodDTO.java`
- Create: `src/main/java/com/smarttravel/analyzer/application/usecase/StartSearchUseCase.java`
- Create: `src/main/java/com/smarttravel/analyzer/application/usecase/GetSearchResultUseCase.java`
- Create: `src/main/java/com/smarttravel/analyzer/presentation/rest/SearchController.java`
- Modify: `src/main/java/com/smarttravel/analyzer/application/dto/SearchCriteriaRequest.java`
- Modify: `src/main/java/com/smarttravel/analyzer/application/mapper/DomainToDtoMapper.java`
- Modify: `src/main/java/com/smarttravel/analyzer/presentation/rest/TravelAnalysisController.java`
- Delete: `src/main/java/com/smarttravel/analyzer/application/usecase/SearchBestValuePackagesUseCase.java`
- Delete: `src/main/java/com/smarttravel/analyzer/application/dto/PackageResponseRecord.java`
- Test: `src/test/java/com/smarttravel/analyzer/presentation/rest/SearchControllerTest.java`

**Interfaces:**
- Consumes: tudo das tarefas 4 a 9
- Produces:
  - `POST /api/search` body `SearchCriteriaRequest` → `202` `SearchStartedResponse(String searchId)`
  - `GET /api/search/{id}` query opcional `maxPrice`, `minRating`, `directFlightOnly`, `breakfastIncluded`, `freeCancellation`, `neighborhood` → `200` `SearchResultResponse`; `404` se o id não existe
  - `neighborhoods` na resposta é calculado sobre os pacotes **antes** de aplicar os filtros, para as opções de bairro não sumirem conforme o usuário filtra
  - `SearchCriteriaRequest(String origin, String destination, LocalDate departureDate, LocalDate returnDate, int travelers, boolean checkedBagRequested, boolean carRequired, boolean flexibleDates)`

O `SearchBestValuePackagesUseCase` sai: ele existia só para devolver os três pacotes escritos à mão. O endpoint `GET /api/travel-analysis/trends/{marketKey}` continua como está.

- [ ] **Step 1: Escrever o teste falhando**

Criar `src/test/java/com/smarttravel/analyzer/presentation/rest/SearchControllerTest.java`:

```java
package com.smarttravel.analyzer.presentation.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class SearchControllerTest {

    @Autowired WebApplicationContext context;

    private MockMvc mockMvc() { return MockMvcBuilders.webAppContextSetup(context).build(); }

    private static final String BODY = """
        {"origin":"CWB","destination":"REC","departureDate":"2030-11-10","returnDate":"2030-11-13",
         "travelers":2,"checkedBagRequested":true,"carRequired":false,"flexibleDates":false}""";

    private String startSearch() throws Exception {
        var response = mockMvc().perform(post("/api/search").contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.searchId").isNotEmpty())
            .andReturn().getResponse().getContentAsString();
        return response.replaceAll(".*\"searchId\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }

    @Test void startingASearchReturnsAnIdImmediately() throws Exception {
        org.assertj.core.api.Assertions.assertThat(startSearch()).isNotBlank();
    }

    @Test void theResultCarriesThreeRecommendationsAndTheDemoFlag() throws Exception {
        var id = startSearch();
        mockMvc().perform(get("/api/search/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.demo").value(true))
            .andExpect(jsonPath("$.packages.length()").value(3))
            .andExpect(jsonPath("$.packages[0].realCost").isNotEmpty())
            .andExpect(jsonPath("$.packages[0].advertisedPrice").isNotEmpty())
            .andExpect(jsonPath("$.packages[0].why").isNotEmpty())
            .andExpect(jsonPath("$.packages[0].lodgingName").isNotEmpty())
            .andExpect(jsonPath("$.packages[0].neighborhood").isNotEmpty())
            .andExpect(jsonPath("$.packages[0].links.length()").value(2));
    }

    @Test void theResultListsTheNeighborhoodsFoundWithCountAndCheapestPrice() throws Exception {
        var id = startSearch();
        mockMvc().perform(get("/api/search/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.neighborhoods.length()").value(org.hamcrest.Matchers.greaterThan(1)))
            .andExpect(jsonPath("$.neighborhoods[0].neighborhood").isNotEmpty())
            .andExpect(jsonPath("$.neighborhoods[0].packages").isNumber())
            .andExpect(jsonPath("$.neighborhoods[0].cheapest").isNotEmpty());
    }

    @Test void filteringByNeighborhoodKeepsTheNeighborhoodOptionsIntact() throws Exception {
        var id = startSearch();
        mockMvc().perform(get("/api/search/{id}", id).param("neighborhood", "bairro-que-nao-existe"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.packages.length()").value(0))
            .andExpect(jsonPath("$.neighborhoods.length()").value(org.hamcrest.Matchers.greaterThan(1)));
    }

    @Test void everySourceIsReportedWithItsHealth() throws Exception {
        var id = startSearch();
        mockMvc().perform(get("/api/search/{id}", id))
            .andExpect(jsonPath("$.sources.length()").value(2))
            .andExpect(jsonPath("$.sources[0].health").value("OK"));
    }

    @Test void anImpossibleFilterReturnsAnEmptyPackageListNotAnError() throws Exception {
        var id = startSearch();
        mockMvc().perform(get("/api/search/{id}", id).param("maxPrice", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.packages.length()").value(0));
    }

    @Test void anUnknownSearchIdReturnsNotFound() throws Exception {
        mockMvc().perform(get("/api/search/{id}", "does-not-exist")).andExpect(status().isNotFound());
    }

    @Test void aRequestWithoutOriginIsRejected() throws Exception {
        mockMvc().perform(post("/api/search").contentType(MediaType.APPLICATION_JSON)
                .content(BODY.replace("\"origin\":\"CWB\"", "\"origin\":\"\"")))
            .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn test -Dtest=SearchControllerTest`
Expected: FAIL — não existe rota `/api/search`.

- [ ] **Step 3: Criar os DTOs**

`SearchStartedResponse.java`:

```java
package com.smarttravel.analyzer.application.dto;
public record SearchStartedResponse(String searchId) {}
```

`SourceStatusDTO.java`:

```java
package com.smarttravel.analyzer.application.dto;

import com.smarttravel.analyzer.domain.model.search.SourceHealth;

public record SourceStatusDTO(String source, SourceHealth health, int offers, String message) {}
```

`BookingLinkDTO.java`:

```java
package com.smarttravel.analyzer.application.dto;
public record BookingLinkDTO(String partner, String url) {}
```

`NeighborhoodDTO.java`:

```java
package com.smarttravel.analyzer.application.dto;

import java.math.BigDecimal;

public record NeighborhoodDTO(String neighborhood, int packages, BigDecimal cheapest) {}
```

`DateOptionDTO.java`:

```java
package com.smarttravel.analyzer.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DateOptionDTO(LocalDate departureDate, LocalDate returnDate, BigDecimal total, BigDecimal difference) {}
```

`PackageDTO.java`:

```java
package com.smarttravel.analyzer.application.dto;

import com.smarttravel.analyzer.domain.model.packagebundle.PackageBundleType;
import java.math.BigDecimal;
import java.util.List;

public record PackageDTO(String id, PackageBundleType recommendationType, BigDecimal advertisedPrice,
                         BigDecimal realCost, BigDecimal hiddenCosts, String currency,
                         double valueScore, double qualityScore, double convenienceScore,
                         List<String> included, String why,
                         String airline, int stops,
                         String lodgingName, String neighborhood, double lodgingRating, int lodgingReviews,
                         String carSupplier, String carCategory,
                         List<BookingLinkDTO> links) {}
```

`SearchResultResponse.java`:

```java
package com.smarttravel.analyzer.application.dto;

import com.smarttravel.analyzer.domain.model.search.SearchStatus;
import java.util.List;

public record SearchResultResponse(String searchId, SearchStatus status, boolean demo,
                                   List<SourceStatusDTO> sources, List<PackageDTO> packages,
                                   List<NeighborhoodDTO> neighborhoods, List<DateOptionDTO> dateOptions) {}
```

- [ ] **Step 4: Acrescentar flexibleDates ao request**

Substituir `SearchCriteriaRequest.java` por:

```java
package com.smarttravel.analyzer.application.dto;

import com.smarttravel.analyzer.domain.model.shared.SearchCriteria;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record SearchCriteriaRequest(@NotBlank String origin, @NotBlank String destination,
                                    @Future LocalDate departureDate, @Future LocalDate returnDate,
                                    @Min(1) int travelers, boolean checkedBagRequested,
                                    boolean carRequired, boolean flexibleDates) {

    public SearchCriteria toDomain() {
        return new SearchCriteria(origin, destination, departureDate, returnDate, travelers, checkedBagRequested, carRequired);
    }
}
```

- [ ] **Step 5: Reescrever o mapper**

Substituir `DomainToDtoMapper.java` por:

```java
package com.smarttravel.analyzer.application.mapper;

import com.smarttravel.analyzer.application.dto.*;
import com.smarttravel.analyzer.domain.link.DeepLinkBuilder;
import com.smarttravel.analyzer.domain.model.packagebundle.TravelPackage;
import com.smarttravel.analyzer.domain.model.search.SourceStatus;
import com.smarttravel.analyzer.domain.model.shared.SearchCriteria;
import com.smarttravel.analyzer.domain.service.PackageExplanationDomainService;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DomainToDtoMapper {

    private final PackageExplanationDomainService explanations;
    private final List<DeepLinkBuilder> linkBuilders;

    public DomainToDtoMapper(PackageExplanationDomainService explanations, List<DeepLinkBuilder> linkBuilders) {
        this.explanations = explanations;
        this.linkBuilders = List.copyOf(linkBuilders);
    }

    public PackageDTO toDto(TravelPackage travelPackage, List<TravelPackage> candidates, SearchCriteria criteria) {
        var lodging = travelPackage.lodging().offer();
        return new PackageDTO(
            travelPackage.id(),
            travelPackage.recommendationType(),
            travelPackage.advertisedPrice().amount(),
            travelPackage.totalPrice().amount(),
            travelPackage.hiddenCosts().amount(),
            travelPackage.totalPrice().currency().getCurrencyCode(),
            travelPackage.valueScore().value(),
            travelPackage.qualityScore().value(),
            travelPackage.convenienceScore().value(),
            travelPackage.costAdjustments().stream().map(explanations::translateAdjustment).toList(),
            explanations.explain(travelPackage, candidates),
            travelPackage.flight().offer().airline().name(),
            travelPackage.flight().offer().stops(),
            lodging.name(),
            lodging.neighborhood(),
            lodging.rating().average(),
            lodging.rating().reviewCount(),
            travelPackage.hasCar() ? travelPackage.car().offer().supplier() : null,
            travelPackage.hasCar() ? travelPackage.car().offer().category().name() : null,
            linkBuilders.stream().map(builder -> new BookingLinkDTO(builder.partnerName(), builder.searchUrl(criteria).toString())).toList());
    }

    public SourceStatusDTO toDto(SourceStatus source) {
        return new SourceStatusDTO(source.source(), source.health(), source.offers(), source.message());
    }
}
```

- [ ] **Step 6: Criar os use cases**

`StartSearchUseCase.java`:

```java
package com.smarttravel.analyzer.application.usecase;

import com.smarttravel.analyzer.application.dto.SearchCriteriaRequest;
import com.smarttravel.analyzer.application.search.*;
import com.smarttravel.analyzer.application.service.SearchRunner;
import java.util.concurrent.Executor;
import org.springframework.stereotype.Service;

@Service
public class StartSearchUseCase {

    private final SearchSessionStore store;
    private final SearchRunner runner;
    private final Executor executor;

    public StartSearchUseCase(SearchSessionStore store, SearchRunner runner, Executor executor) {
        this.store = store;
        this.runner = runner;
        this.executor = executor;
    }

    public String start(SearchCriteriaRequest request) {
        var session = store.create(request.toDomain());
        executor.execute(() -> runner.run(session));
        return session.id();
    }
}
```

`GetSearchResultUseCase.java`:

```java
package com.smarttravel.analyzer.application.usecase;

import com.smarttravel.analyzer.application.dto.*;
import com.smarttravel.analyzer.application.mapper.DomainToDtoMapper;
import com.smarttravel.analyzer.application.search.SearchSessionStore;
import com.smarttravel.analyzer.domain.model.search.PackageFilter;
import com.smarttravel.analyzer.domain.service.PackageFilterDomainService;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class GetSearchResultUseCase {

    private final SearchSessionStore store;
    private final PackageFilterDomainService filters;
    private final DomainToDtoMapper mapper;

    public GetSearchResultUseCase(SearchSessionStore store, PackageFilterDomainService filters, DomainToDtoMapper mapper) {
        this.store = store;
        this.filters = filters;
        this.mapper = mapper;
    }

    public Optional<SearchResultResponse> result(String searchId, PackageFilter filter) {
        return store.find(searchId).map(session -> {
            var visible = filters.apply(session.packages(), filter);
            var packages = visible.stream().map(item -> mapper.toDto(item, session.packages(), session.criteria())).toList();
            var neighborhoods = filters.summarise(session.packages()).stream()
                .map(summary -> new NeighborhoodDTO(summary.neighborhood(), summary.packages(), summary.cheapest().amount()))
                .toList();
            var dateOptions = session.dateOptions().stream()
                .map(option -> new DateOptionDTO(option.departureDate(), option.returnDate(),
                    option.total().amount(), option.difference().amount()))
                .toList();
            return new SearchResultResponse(session.id(), session.status(), session.demo(),
                session.sources().stream().map(mapper::toDto).toList(), packages, neighborhoods, List.copyOf(dateOptions));
        });
    }
}
```

- [ ] **Step 7: Criar o controller e o Executor**

`SearchController.java`:

```java
package com.smarttravel.analyzer.presentation.rest;

import com.smarttravel.analyzer.application.dto.*;
import com.smarttravel.analyzer.application.usecase.*;
import com.smarttravel.analyzer.domain.model.search.PackageFilter;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final StartSearchUseCase startSearch;
    private final GetSearchResultUseCase getResult;

    public SearchController(StartSearchUseCase startSearch, GetSearchResultUseCase getResult) {
        this.startSearch = startSearch;
        this.getResult = getResult;
    }

    @PostMapping
    public ResponseEntity<SearchStartedResponse> start(@Valid @RequestBody SearchCriteriaRequest request) {
        return ResponseEntity.accepted().body(new SearchStartedResponse(startSearch.start(request)));
    }

    @GetMapping("/{searchId}")
    public ResponseEntity<SearchResultResponse> result(
            @PathVariable String searchId,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Double minRating,
            @RequestParam(defaultValue = "false") boolean directFlightOnly,
            @RequestParam(defaultValue = "false") boolean breakfastIncluded,
            @RequestParam(defaultValue = "false") boolean freeCancellation,
            @RequestParam(required = false) String neighborhood) {
        var filter = new PackageFilter(maxPrice, minRating, directFlightOnly, breakfastIncluded, freeCancellation, neighborhood);
        return getResult.result(searchId, filter).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }
}
```

Em `BeanConfig.java`, adicionar o executor. Ele é `SimpleAsyncTaskExecutor` com concorrência limitada — suficiente para uso pessoal e sem thread pool para configurar:

```java
    @Bean java.util.concurrent.Executor searchExecutor() {
        var executor = new org.springframework.core.task.SimpleAsyncTaskExecutor("busca-");
        executor.setConcurrencyLimit(4);
        return executor;
    }
```

- [ ] **Step 8: Remover o use case antigo**

```bash
git rm src/main/java/com/smarttravel/analyzer/application/usecase/SearchBestValuePackagesUseCase.java
git rm src/main/java/com/smarttravel/analyzer/application/dto/PackageResponseRecord.java
```

Em `TravelAnalysisController.java`, remover o campo `searchPackages`, o parâmetro do construtor, o método `search` e os imports que ficarem sem uso. O controller fica só com o endpoint de tendência.

- [ ] **Step 9: Rodar e ver passar**

Run: `mvn test -Dtest=SearchControllerTest`
Expected: PASS, 8 testes.

Se `theResultCarriesThreeRecommendations` falhar com `packages.length()` igual a 0, a busca ainda estava rodando quando o GET chegou. Nesse caso o teste deve esperar: adicione um laço de espera antes do GET, com no máximo 50 tentativas de 100ms, parando quando `status` deixar de ser `BUSCANDO`. Não troque o executor por síncrono — o comportamento assíncrono é o que está sendo testado.

- [ ] **Step 10: Rodar a suíte e commitar**

Run: `mvn test`
Expected: PASS.

```bash
git add -A
git commit -m "feat: expose the asynchronous search over REST"
```

---

### Task 12: Datas flexíveis ±3 dias

**Files:**
- Create: `src/main/java/com/smarttravel/analyzer/application/service/DateFlexibilityService.java`
- Modify: `src/main/java/com/smarttravel/analyzer/application/service/SearchRunner.java`
- Modify: `src/main/java/com/smarttravel/analyzer/application/usecase/StartSearchUseCase.java`
- Test: `src/test/java/com/smarttravel/analyzer/application/service/DateFlexibilityServiceTest.java`

**Interfaces:**
- Consumes: `DateOption` (Task 9), `PackageAssemblerDomainService` (Task 4), os três `*Port`
- Produces:
  - `DateFlexibilityService.neighbouringDates(SearchCriteria criteria)` → `List<DateOption>` — 6 opções (−3 a +3, sem o dia 0), ordenadas por data, cada uma com o custo do melhor pacote daquele dia e a diferença em relação à data original
  - `SearchRunner.run(SearchSession session, boolean flexibleDates)` — sobrecarga; `run(session)` continua chamando com `false`

A duração da viagem é preservada: desloca-se ida e volta pelo mesmo número de dias. Datas no passado são descartadas.

- [ ] **Step 1: Escrever o teste falhando**

Criar `src/test/java/com/smarttravel/analyzer/application/service/DateFlexibilityServiceTest.java`:

```java
package com.smarttravel.analyzer.application.service;

import com.smarttravel.analyzer.domain.model.shared.SearchCriteria;
import com.smarttravel.analyzer.domain.service.*;
import com.smarttravel.analyzer.infrastructure.adapter.demo.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DateFlexibilityServiceTest {

    private static final LocalDate DEPARTURE = LocalDate.now().plusDays(60);
    private static final SearchCriteria CRITERIA =
        new SearchCriteria("CWB", "REC", DEPARTURE, DEPARTURE.plusDays(3), 2, true, false);

    private DateFlexibilityService service() {
        var assembler = new PackageAssemblerDomainService(new CostNormalizerDomainService(),
            new ValueScoringDomainService(), new PriceTrendDomainService());
        return new DateFlexibilityService(new DemoFlightProvider(), new DemoLodgingProvider(),
            new DemoCarRentalProvider(), assembler);
    }

    @Test void returnsSixNeighbouringDatesWithoutTheOriginalOne() {
        var options = service().neighbouringDates(CRITERIA);
        assertThat(options).hasSize(6);
        assertThat(options).noneSatisfy(option -> assertThat(option.departureDate()).isEqualTo(DEPARTURE));
    }

    @Test void everyOptionKeepsTheOriginalTripLength() {
        assertThat(service().neighbouringDates(CRITERIA)).allSatisfy(option ->
            assertThat(ChronoUnit.DAYS.between(option.departureDate(), option.returnDate())).isEqualTo(3));
    }

    @Test void optionsComeSortedByDepartureDate() {
        var options = service().neighbouringDates(CRITERIA);
        assertThat(options).isSortedAccordingTo(java.util.Comparator.comparing(option -> option.departureDate()));
    }

    @Test void theDifferenceIsNeverNegativeBecauseMoneyCannotBeNegative() {
        assertThat(service().neighbouringDates(CRITERIA)).allSatisfy(option ->
            assertThat(option.difference().amount().signum()).isNotNegative());
    }

    @Test void datesInThePastAreDiscarded() {
        var tomorrow = LocalDate.now().plusDays(1);
        var criteria = new SearchCriteria("CWB", "REC", tomorrow, tomorrow.plusDays(3), 2, true, false);
        assertThat(service().neighbouringDates(criteria)).allSatisfy(option ->
            assertThat(option.departureDate()).isAfterOrEqualTo(LocalDate.now()));
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn test -Dtest=DateFlexibilityServiceTest`
Expected: erro de compilação — `DateFlexibilityService` não existe.

- [ ] **Step 3: Implementar**

Criar `src/main/java/com/smarttravel/analyzer/application/service/DateFlexibilityService.java`:

```java
package com.smarttravel.analyzer.application.service;

import com.smarttravel.analyzer.application.search.DateOption;
import com.smarttravel.analyzer.domain.model.packagebundle.TravelPackage;
import com.smarttravel.analyzer.domain.model.search.TravelOffers;
import com.smarttravel.analyzer.domain.model.shared.*;
import com.smarttravel.analyzer.domain.repository.*;
import com.smarttravel.analyzer.domain.service.PackageAssemblerDomainService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class DateFlexibilityService {

    private static final int WINDOW = 3;

    private final FlightProviderPort flights;
    private final LodgingProviderPort lodgings;
    private final CarRentalProviderPort cars;
    private final PackageAssemblerDomainService assembler;

    public DateFlexibilityService(FlightProviderPort flights, LodgingProviderPort lodgings,
                                  CarRentalProviderPort cars, PackageAssemblerDomainService assembler) {
        this.flights = flights;
        this.lodgings = lodgings;
        this.cars = cars;
        this.assembler = assembler;
    }

    public List<DateOption> neighbouringDates(SearchCriteria criteria) {
        var reference = cheapestTotal(criteria);
        var options = new ArrayList<DateOption>();
        for (int offset = -WINDOW; offset <= WINDOW; offset++) {
            if (offset == 0) continue;
            var departure = criteria.departureDate().plusDays(offset);
            if (departure.isBefore(LocalDate.now())) continue;
            var shifted = new SearchCriteria(criteria.origin(), criteria.destination(), departure,
                criteria.returnDate().plusDays(offset), criteria.travelers(),
                criteria.checkedBagRequested(), criteria.carRequired());
            cheapestTotal(shifted).ifPresent(total ->
                options.add(new DateOption(shifted.departureDate(), shifted.returnDate(), total, difference(reference, total))));
        }
        options.sort(Comparator.comparing(DateOption::departureDate));
        return List.copyOf(options);
    }

    private Optional<Money> cheapestTotal(SearchCriteria criteria) {
        try {
            var offers = new TravelOffers(flights.searchFlights(criteria), lodgings.searchLodging(criteria),
                criteria.carRequired() ? cars.searchCars(criteria) : List.of());
            return assembler.assemble(criteria, offers).stream()
                .map(TravelPackage::totalPrice)
                .min(Comparator.naturalOrder());
        } catch (RuntimeException failure) {
            return Optional.empty();
        }
    }

    private static Money difference(Optional<Money> reference, Money total) {
        if (reference.isEmpty()) return new Money(BigDecimal.ZERO, total.currency());
        var base = reference.get();
        return base.compareTo(total) >= 0 ? base.subtract(total) : new Money(BigDecimal.ZERO, total.currency());
    }
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `mvn test -Dtest=DateFlexibilityServiceTest`
Expected: PASS, 5 testes.

- [ ] **Step 5: Ligar ao SearchRunner**

Em `SearchRunner.java`, injetar `DateFlexibilityService` no construtor (adicionando o parâmetro ao final) e acrescentar a sobrecarga:

```java
    public void run(SearchSession session) { run(session, false); }

    public void run(SearchSession session, boolean flexibleDates) {
        runSearch(session);
        if (flexibleDates && session.status() != SearchStatus.ERRO) {
            session.dateOptions(dateFlexibility.neighbouringDates(session.criteria()));
        }
    }
```

Renomear o corpo atual de `run(SearchSession)` para `private void runSearch(SearchSession session)`.

Atualizar `SearchRunnerTest` para passar o novo argumento no construtor:

```java
        var dateFlexibility = new DateFlexibilityService(flights, lodgings, cars, assembler);
        return new SearchRunner(flights, lodgings, cars, assembler, new PackageBundlerDomainService(), dateFlexibility);
```

Em `StartSearchUseCase.start`, trocar a chamada por `runner.run(session, request.flexibleDates())`.

- [ ] **Step 6: Rodar a suíte e commitar**

Run: `mvn test`
Expected: PASS.

```bash
git add -A
git commit -m "feat: compare neighbouring departure dates within a 3-day window"
```

---

### Task 13: Tela em português, com busca, progresso, filtros e reserva

A tela atual está em inglês, tem três pacotes de exemplo no JavaScript e chama um endpoint que deixou de existir. É reescrita inteira.

**Files:**
- Rewrite: `src/main/resources/static/index.html`
- Rewrite: `src/main/resources/static/app.js`
- Modify: `src/main/resources/static/styles.css`

**Interfaces:**
- Consumes: `POST /api/search` e `GET /api/search/{id}` (Task 10)
- Produces: a tela funcionando de ponta a ponta

**Requisitos da tela:**

1. Formulário: origem, destino, data de ida, data de volta, viajantes, e três checkboxes — bagagem despachada, preciso de carro, datas flexíveis.
2. Ao enviar: `POST /api/search`, guardar o `searchId`, e fazer polling do `GET` a cada 1s, no máximo 60 tentativas.
3. Durante a busca: mostrar cada fonte com seu estado. Nada de spinner mudo.
4. `demo: true` na resposta ⇒ faixa fixa e visível: "Dados de demonstração — os preços não são reais."
5. Resultado: o pacote `BEST_VALUE_OVERALL` em destaque com o rótulo **Pacote Ideal**; abaixo, `SMART_BUDGET` ("Econômico Inteligente") e `MAX_COMFORT` ("Máximo Conforto").
6. Cada card mostra: custo real em destaque, preço anunciado riscado, custo oculto, o que está incluído, o texto de `why`, hotel com nota e número de avaliações, companhia aérea, paradas, e os botões de reserva a partir de `links`.
7. Filtros: preço máximo, nota mínima, só voo direto, café da manhã, cancelamento grátis. Ao mudar qualquer um, refazer o `GET` com os query params — sem refazer a busca.
7b. Bairros: uma fileira de chips com o nome do bairro, quantas opções tem e o preço a partir de. Clicar filtra por aquele bairro; "Todos os bairros" limpa. As opções vêm de `neighborhoods`, que o servidor calcula antes dos filtros — então elas não somem conforme o usuário filtra.
8. `status: "ERRO"` ⇒ mensagem explicando que nenhuma combinação foi encontrada, listando as fontes degradadas.
9. `status: "PARCIAL"` ⇒ aviso de que faltou fonte, com o resultado exibido mesmo assim.
10. `dateOptions` não vazio ⇒ tabela de datas vizinhas com a economia de cada uma.
11. Formatação de moeda: `Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })`.
12. Todo texto visível em português.

- [ ] **Step 1: Reescrever o HTML**

Substituir `index.html` inteiro. Estrutura mínima obrigatória (os ids são usados pelo `app.js`):

```html
<!doctype html>
<html lang="pt-BR">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>FindaTrip — o pacote que vale a pena</title>
  <link rel="stylesheet" href="/styles.css" />
</head>
<body>
  <main class="shell">
    <div id="demo-banner" class="demo-banner" hidden>
      Dados de demonstração — os preços não são reais.
    </div>

    <h1>Para onde e quando?</h1>

    <form id="search-form" class="search-card">
      <label>Origem <input name="origin" value="CWB" maxlength="3" required /></label>
      <label>Destino <input name="destination" value="REC" maxlength="3" required /></label>
      <label>Ida <input name="departureDate" type="date" required /></label>
      <label>Volta <input name="returnDate" type="date" required /></label>
      <label>Viajantes <input name="travelers" type="number" min="1" value="2" required /></label>
      <label class="check"><input name="checkedBagRequested" type="checkbox" checked /> Bagagem despachada</label>
      <label class="check"><input name="carRequired" type="checkbox" /> Preciso de carro</label>
      <label class="check"><input name="flexibleDates" type="checkbox" /> Datas flexíveis (±3 dias)</label>
      <button type="submit">Buscar</button>
    </form>

    <section id="progress" class="progress" hidden>
      <h2>Buscando…</h2>
      <ul id="sources"></ul>
    </section>

    <section id="neighborhoods" class="neighborhoods" hidden>
      <h2>Em que parte de <span id="destination-label"></span>?</h2>
      <div id="neighborhood-chips" class="chips"></div>
    </section>

    <section id="filters" class="filters" hidden>
      <label>Preço máximo <input id="f-maxPrice" type="number" min="0" step="50" /></label>
      <label>Nota mínima <input id="f-minRating" type="number" min="0" max="10" step="0.5" /></label>
      <label class="check"><input id="f-directFlightOnly" type="checkbox" /> Só voo direto</label>
      <label class="check"><input id="f-breakfastIncluded" type="checkbox" /> Café da manhã</label>
      <label class="check"><input id="f-freeCancellation" type="checkbox" /> Cancelamento grátis</label>
    </section>

    <p id="message" class="message" hidden></p>
    <section id="packages" class="packages"></section>
    <section id="date-options" class="date-options" hidden>
      <h2>Mudando a data</h2>
      <table id="date-table"></table>
    </section>
  </main>
  <script src="/app.js"></script>
</body>
</html>
```

- [ ] **Step 2: Reescrever o JavaScript**

Substituir `app.js` inteiro:

```javascript
const form = document.querySelector('#search-form');
const progress = document.querySelector('#progress');
const sourcesList = document.querySelector('#sources');
const filters = document.querySelector('#filters');
const message = document.querySelector('#message');
const packagesEl = document.querySelector('#packages');
const dateOptionsEl = document.querySelector('#date-options');
const neighborhoodsEl = document.querySelector('#neighborhoods');
const chipsEl = document.querySelector('#neighborhood-chips');
const destinationLabel = document.querySelector('#destination-label');
const dateTable = document.querySelector('#date-table');
const demoBanner = document.querySelector('#demo-banner');

const LABELS = {
  BEST_VALUE_OVERALL: 'Pacote Ideal',
  SMART_BUDGET: 'Econômico Inteligente',
  MAX_COMFORT: 'Máximo Conforto'
};

const brl = (value) => new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value);
const inDays = (days) => new Date(Date.now() + days * 86400000).toISOString().slice(0, 10);

document.querySelector('input[name="departureDate"]').value = inDays(45);
document.querySelector('input[name="returnDate"]').value = inDays(48);

let searchId = null;
let neighborhood = null;

form.addEventListener('submit', async (event) => {
  event.preventDefault();
  const data = new FormData(form);
  const body = {
    origin: data.get('origin'),
    destination: data.get('destination'),
    departureDate: data.get('departureDate'),
    returnDate: data.get('returnDate'),
    travelers: Number(data.get('travelers')),
    checkedBagRequested: data.get('checkedBagRequested') === 'on',
    carRequired: data.get('carRequired') === 'on',
    flexibleDates: data.get('flexibleDates') === 'on'
  };

  reset();
  destinationLabel.textContent = body.destination;
  progress.hidden = false;

  const started = await fetch('/api/search', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  });

  if (!started.ok) {
    show('Não foi possível iniciar a busca. Confira os campos e tente de novo.');
    progress.hidden = true;
    return;
  }

  searchId = (await started.json()).searchId;
  poll();
});

filters.addEventListener('change', () => { if (searchId) load(); });

function reset() {
  packagesEl.innerHTML = '';
  sourcesList.innerHTML = '';
  chipsEl.innerHTML = '';
  neighborhood = null;
  dateOptionsEl.hidden = true;
  neighborhoodsEl.hidden = true;
  filters.hidden = true;
  message.hidden = true;
}

function show(text) {
  message.textContent = text;
  message.hidden = false;
}

async function poll(attempt = 0) {
  const result = await load();
  if (result && result.status === 'BUSCANDO' && attempt < 60) {
    setTimeout(() => poll(attempt + 1), 1000);
  }
}

function filterQuery() {
  const params = new URLSearchParams();
  const maxPrice = document.querySelector('#f-maxPrice').value;
  const minRating = document.querySelector('#f-minRating').value;
  if (maxPrice) params.set('maxPrice', maxPrice);
  if (minRating) params.set('minRating', minRating);
  if (document.querySelector('#f-directFlightOnly').checked) params.set('directFlightOnly', 'true');
  if (document.querySelector('#f-breakfastIncluded').checked) params.set('breakfastIncluded', 'true');
  if (document.querySelector('#f-freeCancellation').checked) params.set('freeCancellation', 'true');
  if (neighborhood) params.set('neighborhood', neighborhood);
  return params.toString() ? `?${params}` : '';
}

async function load() {
  const response = await fetch(`/api/search/${searchId}${filterQuery()}`);
  if (!response.ok) {
    show('Essa busca expirou. Faça uma nova.');
    return null;
  }
  const result = await response.json();
  render(result);
  return result;
}

function render(result) {
  demoBanner.hidden = !result.demo;
  renderSources(result.sources);

  if (result.status === 'BUSCANDO') return;
  progress.hidden = true;

  if (result.status === 'ERRO') {
    const broken = result.sources.filter((s) => s.health === 'DEGRADADO').map((s) => s.source);
    show(broken.length
      ? `Nenhuma combinação encontrada. Fontes com problema: ${broken.join(', ')}.`
      : 'Nenhuma combinação encontrada para essas datas.');
    return;
  }

  filters.hidden = false;
  renderNeighborhoods(result.neighborhoods);

  if (result.status === 'PARCIAL') {
    const broken = result.sources.filter((s) => s.health === 'DEGRADADO').map((s) => s.source);
    show(`Resultado parcial — sem dados de: ${broken.join(', ')}.`);
  } else if (result.packages.length === 0) {
    show(neighborhood
      ? `Nenhum pacote em ${neighborhood} com esses filtros. Toque em "Todos os bairros" ou afrouxe algum filtro.`
      : 'Nenhum pacote passa nos filtros. Afrouxe algum deles.');
  }

  renderPackages(result.packages);
  renderDateOptions(result.dateOptions);
}

function renderSources(sources) {
  sourcesList.innerHTML = sources.map((source) => `
    <li class="source ${source.health.toLowerCase()}">
      <b>${source.source}</b>
      <span>${source.health === 'OK' ? `${source.offers} ofertas` : source.message || 'sem resposta'}</span>
    </li>`).join('');
}

function renderPackages(packages) {
  packagesEl.innerHTML = packages.map((item, index) => `
    <article class="package ${index === 0 ? 'featured' : ''}">
      <header>
        <span class="pill">${LABELS[item.recommendationType] || item.recommendationType}</span>
        <div class="price">
          <strong>${brl(item.realCost)}</strong>
          ${Number(item.hiddenCosts) > 0 ? `<s>${brl(item.advertisedPrice)}</s>` : ''}
        </div>
      </header>
      ${Number(item.hiddenCosts) > 0 ? `<p class="hidden-costs">${brl(item.hiddenCosts)} em custos que o anúncio não mostra</p>` : ''}
      <dl class="details">
        <div><dt>Hospedagem</dt><dd>${item.lodgingName} — ${item.neighborhood} — nota ${item.lodgingRating} (${item.lodgingReviews} avaliações)</dd></div>
        <div><dt>Voo</dt><dd>${item.airline} — ${item.stops === 0 ? 'direto' : `${item.stops} parada(s)`}</dd></div>
        ${item.carSupplier ? `<div><dt>Carro</dt><dd>${item.carSupplier} — ${item.carCategory}</dd></div>` : ''}
        <div><dt>Incluído</dt><dd>${item.included.join(', ') || '—'}</dd></div>
      </dl>
      ${item.why ? `<p class="why"><b>Por que este pacote:</b> ${item.why}</p>` : ''}
      <footer>
        ${item.links.map((link) => `<a class="book" href="${link.url}" target="_blank" rel="noopener">Reservar no ${link.partner}</a>`).join('')}
      </footer>
    </article>`).join('');
}

function renderNeighborhoods(areas) {
  if (!areas || areas.length === 0) { neighborhoodsEl.hidden = true; return; }
  neighborhoodsEl.hidden = false;
  const all = `<button class="chip ${neighborhood ? '' : 'active'}" data-area="">Todos os bairros</button>`;
  chipsEl.innerHTML = all + areas.map((area) => `
    <button class="chip ${neighborhood === area.neighborhood ? 'active' : ''}" data-area="${area.neighborhood}">
      ${area.neighborhood}
      <small>${area.packages} ${area.packages === 1 ? 'opção' : 'opções'} · a partir de ${brl(area.cheapest)}</small>
    </button>`).join('');
}

chipsEl.addEventListener('click', (event) => {
  const chip = event.target.closest('.chip');
  if (!chip || !searchId) return;
  neighborhood = chip.dataset.area || null;
  load();
});

function renderDateOptions(options) {
  if (!options || options.length === 0) { dateOptionsEl.hidden = true; return; }
  dateOptionsEl.hidden = false;
  dateTable.innerHTML = `
    <tr><th>Ida</th><th>Volta</th><th>Custo</th><th>Economia</th></tr>
    ${options.map((option) => `
      <tr>
        <td>${option.departureDate}</td>
        <td>${option.returnDate}</td>
        <td>${brl(option.total)}</td>
        <td>${Number(option.difference) > 0 ? `−${brl(option.difference)}` : '—'}</td>
      </tr>`).join('')}`;
}
```

- [ ] **Step 3: Ajustar o CSS**

Em `styles.css`, manter a paleta existente e acrescentar as classes novas: `.demo-banner` (faixa de aviso, fundo âmbar, texto escuro, sempre visível no topo), `.progress`, `.source.ok` / `.source.degradado` (verde e âmbar), `.filters` (linha de campos), `.package` (card), `.package.featured` (borda e sombra mais fortes que os demais), `.price s` (preço anunciado riscado, cor apagada), `.hidden-costs`, `.why`, `.book` (botão), `.chips` (fileira que quebra linha), `.chip` e `.chip.active` (o ativo com fundo cheio e contraste invertido; `<small>` em bloco, menor e apagado), `.date-options table` (larguras e alinhamento à direita nos valores).

Remover as regras que sobraram sem uso do layout antigo (`.hero`, `.value-card`, `.score-ring`, `.mini-map`, `.topbar`, `.nav-links`).

- [ ] **Step 4: Verificar na mão**

```bash
mvn spring-boot:run
```

Abrir `http://localhost:8080` e conferir, um a um:

1. A faixa de dados de demonstração aparece.
2. Buscar CWB → REC mostra as fontes com contagem de ofertas durante a busca.
3. Chegam três cards, com o Pacote Ideal em destaque.
4. O custo real é maior que o preço anunciado riscado, e o texto de custo oculto bate com a diferença.
5. "Por que este pacote" traz frase em português.
6. Marcar "Só voo direto" reduz ou mantém a lista, sem recarregar a página.
6b. Os chips de bairro aparecem com contagem e preço; clicar em um filtra, e "Todos os bairros" volta ao conjunto completo.
7. Preço máximo igual a 1 esvazia a lista e mostra a mensagem de filtro.
8. Os botões de reserva abrem o Booking e o Google Flights com destino e datas preenchidos.
9. Marcar "Datas flexíveis" e buscar de novo traz a tabela de datas vizinhas.

Encerrar com Ctrl+C.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static
git commit -m "feat: rebuild the front end around the asynchronous search"
```

---

### Task 13.5: Filtrar os candidatos antes de escolher os perfis (correção)

Defeito encontrado ao verificar a Task 13 rodando de verdade. `session.packages()` guardava só as
três recomendações finais, e tanto os filtros quanto o resumo por bairro trabalhavam sobre elas.

Duas consequências: os chips de bairro mostravam no máximo 3 bairros, quase sempre 1 — a
funcionalidade nascia inútil; e marcar "só voo direto" devolvia o que sobrasse dos 3 já escolhidos
em vez dos 3 melhores pacotes com voo direto.

Um terceiro defeito apareceu junto: `topRecommendations` devolvia sempre 3 entradas, e o mesmo
pacote costuma vencer em mais de um perfil — então a tela mostrava o mesmo card repetido sob
rótulos diferentes.

**O que mudou:**
- `SearchSession` guarda `candidates()` (todos os pacotes montados) além de `packages()`.
- `GetSearchResultUseCase` filtra os candidatos e só então chama o bundler; o resumo por bairro
  sai dos candidatos, para as opções não sumirem conforme o usuário filtra.
- `PackageBundlerDomainService` devolve até três recomendações **distintas**: cada perfil leva o
  melhor pacote ainda não escolhido. Com um só candidato, devolve um card, não três iguais.
- As asserções de "exatamente 3 pacotes" em `SearchControllerTest` e `SearchRunnerTest` estavam
  erradas: aceitavam a repetição como se fosse resultado. Viraram "de 1 a 3, sem id repetido".

Coberto por `PackageBundlerDomainServiceTest` (6 testes) e três testes novos em
`SearchControllerTest`. Verificado com a aplicação no ar: 4 bairros com contagem real
(10, 5, 5, 5 opções), filtro por "centro" em minúsculo e sem acento achando "Centro", e três
pacotes distintos com e sem filtro.

---

### Task 14: Atualizar o CLAUDE.md e fechar

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: Rodar a suíte inteira uma última vez**

Run: `mvn test`
Expected: PASS. Anotar o número total de testes.

- [ ] **Step 2: Atualizar o CLAUDE.md**

Remover da seção "Pendências conhecidas" o item do surefire (resolvido na Task 1). Acrescentar em "Ordem de trabalho" que a etapa de produto com dados de demonstração está concluída, e que a próxima é conectar a primeira fonte real substituindo os beans `Demo*Provider` em `BeanConfig`.

Acrescentar uma seção curta explicando que `PackageAssemblerDomainService` calcula a nota de preço sobre os próprios candidatos da busca, e que isso muda quando o coletor de histórico existir.

- [ ] **Step 3: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: record the finished demo-data milestone"
```

---

## Notas para quem for implementar

- **Não invente fonte real.** Nenhuma tarefa aqui acessa a rede. Se você se pegar escrevendo um `HttpClient`, parou de seguir o plano.
- **Não remova a marca de demonstração.** A faixa amarela na tela é requisito, não enfeite. Um buscador que mostra preço inventado sem avisar é pior que não existir.
- **Se um teste antigo quebrar, pare e reporte.** Os três testes de `TravelDomainServicesTest` nunca rodaram antes da Task 1; se algum falhar, é descoberta, não obstáculo a contornar.
