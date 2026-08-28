# CLAUDE.md — FindaTrip

Ferramenta **pessoal** de comparação de viagem: acha o melhor custo-benefício real
de voo + hospedagem + carro, e mostra a tendência de preço da rota ao longo do tempo.
Uso pessoal, volume baixo, busca sempre iniciada por ação do usuário.

Spec vigente: `../spec-v5-fontes-links-imagens.md` (conteúdo é a **v6 — Sem Travelpayouts**).
A v6 substitui a v5 inteira.

---

## Stack

Java 21 · Spring Boot 3.5.5 · Maven · JUnit 5 + AssertJ (via `spring-boot-starter-test`).
Front atual: HTML/CSS/JS estático em `src/main/resources/static/`.

```bash
mvn test              # bateria completa
mvn spring-boot:run
```

Não há Maven Wrapper no repositório — use o `mvn` do sistema.

> **Atenção:** hoje `mvn test` termina com `Tests run: 0`. O `pom.xml` não fixa a versão do
> `maven-surefire-plugin`, então o Maven usa a 2.12.4 do super-POM, que não enxerga JUnit 5 —
> `TravelDomainServicesTest` nunca roda. Corrigir isso é pré-requisito do TDD (ver Pendências).

---

## Arquitetura

Hexagonal, quatro camadas, dependência sempre apontando para dentro:

```
presentation/   → controller REST + handler de exceção
application/    → usecase, dto, mapper
domain/         → model, service, repository (PORTAS), exception
infrastructure/ → adapter (implementações das portas), configuration
```

Regras que valem para todo código novo:

- **`domain/` não importa nada de Spring.** Nem anotação, nem `@Component`. Beans de
  domínio são registrados à mão em `infrastructure/configuration/BeanConfig`.
- **Portas ficam em `domain/repository/`**, sufixo `Port` (`FlightProviderPort`,
  `LodgingProviderPort`, `CarRentalProviderPort`, `PriceHistoryStorePort`).
  Adaptadores ficam em `infrastructure/adapter/` com sufixo `Adapter`.
- **Modelo de domínio é `record` imutável**, com validação no construtor compacto
  lançando `DomainException` (ver `Money`). Coleções são copiadas na entrada
  (`Set.copyOf`), nunca guardadas por referência.
- **Dinheiro é sempre `Money`** — `BigDecimal` com escala 2, `HALF_UP`, moeda explícita,
  operação entre moedas diferentes falha. Nunca `double` para preço.
- Nada de setter, nada de entidade anêmica com lógica no service: se o cálculo é do
  objeto, ele mora no objeto (`LodgingOffer.normalize()`,
  `CarRentalOffer.normalizeWithFullInsurance()`).

---

## Como trabalhar aqui

**TDD é obrigatório.** Teste primeiro, mostre falhando, só então implemente.
Nenhuma implementação entra sem teste que a justifique.

- **Teste de contrato por porta.** Cada família de adaptador estende o contract test da
  sua porta (`StayOfferProviderContractTest` etc.) e herda a bateria inteira. É o LSP
  fazendo trabalho útil: um scraper novo não pode se comportar diferente dos outros.
- **Nenhum teste bate na rede.** Scraper se testa contra HTML de fixture em disco.
- Pare para revisão ao terminar um módulo. Não emende o próximo por conta própria.

---

## Fontes

Sem API de afiliado. Fontes são scrapers próprios, configurados por YAML em `scrapers/`.
Duffel pode entrar como fonte opcional de voo, atrás da mesma porta dos scrapers.
Seletor CSS **NUNCA** fica em código Java — sempre no YAML do site.
Todo scraper degrada com elegância: falha vira resultado parcial marcado, nunca exceção
que sobe.

Detalhes que decorrem disso:

- **Seletores em `scrapers/{site}.yml`**, com `url_busca`, `tipo` (`spa|ssr`),
  `seletores`, `rate_limit_ms` e `_meta.verificado_em`. Config com `verificado_em`
  acima de 90 dias falha o build — mesma regra de validade das regras tarifárias.
- **Cache de HTML em disco** em `.cache/{site}/{hash}.html`. Reprocessar não pode
  bater no site. Sem isso o bloqueio vem do próprio ciclo de desenvolvimento.
- **Degradação, não lista vazia.** Scraper que devolve 0 onde a média histórica é > 10
  está com layout quebrado: marca a fonte como `DEGRADADO` e o resultado da busca
  carrega quais fontes degradaram. A falha silenciosa é a pior.
- **Busca via porta `PageFetcher`**, com `JsoupFetcher` (ssr) e `PlaywrightFetcher` (spa).
- **Conduta:** 1 request a cada 3–5s por site, `robots.txt` respeitado, coletor 2× ao dia
  em horário de baixa.

### Links de reserva

Não existe mais link com TTL nem regra de resolução no clique. `DeepLinkBuilder` é
**função pura, sem rede**: monta a URL de busca do site a partir de `SearchCriteria`, e a
URL direta a partir da oferta (o `link` que o scraper já capturou). Testável por asserção
de string.

### Imagens

`record ImageRef(URI origem, ImageSource fonte, String atribuicao)`. Padrão é a URL que o
scraper capturou, servida por proxy próprio (`/api/img?ref=...`) que baixa e cacheia em
disco — resolve referer e evita rebater no CDN alheio a cada render. Google Places Photos
só quando o scraper não trouxer imagem; ali a atribuição é obrigatória e a foto não pode
ser armazenada. Fonte que exige atribuição nunca produz `ImageRef` com `atribuicao` vazia.

---

## Ordem de trabalho

| Etapa | Entrega | Estado |
|---|---|---|
| 0 | 30 min medindo o Duffel: CWB→GRU e CWB→REC, quantas das três cias aparecem | manual, fora do código |
| 1 | `adapter/scraper` com Booking + coletor de preços rodando | **próximo** |
| 2 | `domain/cost` — TrueCostEngine | |
| 3 | Mais scrapers: Google Flights, um site de cia aérea | |
| 4 | `domain/stay` — dedup entre fontes | |
| 5 | `domain/trip` — Pareto + diversificação | |
| 6 | API REST + front React | |
| 7 | Airbnb, proxy de imagem, alerta no Telegram | |

**A etapa 1 tem relógio correndo.** Sem API de afiliado, os dois gráficos dependem 100%
da coleta própria: o coletor precisa começar a gravar série agora, ou não há gráfico
nenhum por 60 dias.

---

## Pendências conhecidas

- `pom.xml` ainda não tem SnakeYAML, Jsoup nem Playwright — entram com a etapa 1.
- Não há `mvnw` versionado; considere adicionar o wrapper para fixar a versão do Maven.
- **`maven-surefire-plugin` sem versão fixada no `pom.xml`**: a 2.12.4 do super-POM ignora
  JUnit 5 e a suíte inteira passa em branco (`Tests run: 0`). Fixar em 3.5.x antes de
  escrever qualquer teste novo.
- `InMemoryPriceHistoryStoreAdapter` devolve dado fixo de exemplo; é stub, não fonte.
- Nomenclatura: a spec fala em `StayOffer`/`StayOfferProvider`, o código tem
  `LodgingOffer`/`LodgingProviderPort`. **Mantenha os nomes do código** e trate os da
  spec como sinônimos, ou renomeie tudo de uma vez num commit isolado — não misture.
