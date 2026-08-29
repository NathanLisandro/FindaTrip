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

Suíte atual: **81 testes**, todos verdes.

---

## Estado do projeto

O buscador funciona de ponta a ponta. A API é assíncrona, em dois passos:

- `POST /api/search` — recebe origem, destino, datas e preferências, cria a sessão de busca e
  devolve o `searchId` na hora. A busca roda em background.
- `GET /api/search/{id}` — devolve o estado (`BUSCANDO`, `PRONTO`, `PARCIAL`, `ERRO`), a saúde de
  cada fonte, até três pacotes recomendados distintos, o resumo por bairro e a explicação da
  escolha. Aceita os filtros como query params — filtrar é uma nova leitura, não uma nova busca.

O front (`src/main/resources/static/`) está inteiro em português e consome essa API com polling.

**Os dados são de demonstração.** `DemoFlightProvider`, `DemoLodgingProvider` e
`DemoCarRentalProvider` geram ofertas plausíveis sem tocar na rede, e a tela diz isso ao usuário
numa faixa que **não é enfeite**: um buscador que mostra preço inventado sem avisar é pior que não
existir. Não remova a marca antes de existir fonte real.

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

## Nota de preço sem histórico

`PackageAssemblerDomainService` precisa de um `RoutePriceTrend` para transformar preço em nota, e
não existe histórico coletado — o coletor da etapa 1 ainda não roda. Então a tendência é calculada
**sobre os próprios candidatos daquela busca**: monta um `PriceHistoryPoint` por combinação e
manda para o `PriceTrendDomainService`. O pacote mais barato que a média do conjunto pontua acima
de 70, o mais caro abaixo.

Isso é honesto — compara o que está na tela, não um número inventado — mas é uma nota **relativa
à busca**, não ao histórico da rota. "Bom preço" aqui significa "bom entre os que achamos hoje".

Quando o coletor existir, troca-se só a origem da tendência (`trendOver`) por uma consulta ao
`PriceHistoryStorePort`. O resto do assembler não muda.

---

## Candidatos ≠ recomendações

Regra de desenho fácil de quebrar sem perceber, e o motivo da Task 13.5 do plano:

**Filtros e resumo por bairro trabalham sobre `session.candidates()` — todos os pacotes montados —
NUNCA sobre `session.packages()`, que são só as três recomendações finais.**

Se alguém filtrar sobre `packages()`, duas coisas quebram de uma vez:

- os chips de bairro degeneram: 3 pacotes rendem no máximo 3 bairros, quase sempre 1, e a
  funcionalidade nasce inútil;
- os filtros passam a **aparar** o resultado em vez de **reescolher**: marcar "só voo direto"
  devolve o que sobrou dos 3 já escolhidos, não os 3 melhores pacotes com voo direto.

A ordem correta em `GetSearchResultUseCase` é: filtrar os candidatos → chamar o
`PackageBundlerDomainService` sobre o que sobrou → resumir os bairros a partir dos candidatos (não
dos filtrados, senão as opções somem conforme o usuário clica).

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
| P | **Produto inteiro com dados de demonstração**: montagem de pacote, filtros no servidor, explicação da escolha, busca assíncrona, API e front em português | **concluída** |
| 1 | Primeira fonte real: `adapter/scraper` com Booking + coletor de preços rodando | **próximo** |
| 2 | `domain/cost` — TrueCostEngine | |
| 3 | Mais scrapers: Google Flights, um site de cia aérea | |
| 4 | `domain/stay` — dedup entre fontes | |
| 5 | `domain/trip` — Pareto + diversificação | |
| 6 | API REST + front React | parcial: API e front estático prontos |
| 7 | Airbnb, proxy de imagem, alerta no Telegram | |

A etapa P entregou o esqueleto todo funcionando; o que falta é preço de verdade dentro dele.

**Conectar a primeira fonte real é trocar quem implementa a porta.** Os providers de demonstração
são `@Component` em `infrastructure/adapter/demo/`, descobertos por component scan. "Substituir"
significa fazer o adaptador real implementar a mesma porta (`FlightProviderPort`,
`LodgingProviderPort`, `CarRentalProviderPort`) e tirar o `Demo*` do caminho — removendo o
`@Component` dele, marcando o real como `@Primary`, ou registrando ambos à mão em `BeanConfig`.
Nada acima da porta muda: `SearchRunner`, assembler, filtros e front não sabem a diferença. É
exatamente para isso que a porta existe — se conectar a fonte real exigir mexer em domínio ou
aplicação, algo foi desenhado errado.

**A etapa 1 tem relógio correndo.** Sem API de afiliado, os dois gráficos dependem 100%
da coleta própria: o coletor precisa começar a gravar série agora, ou não há gráfico
nenhum por 60 dias. Enquanto ele não existir, a nota de preço continua relativa à busca
(ver acima).

---

## Pendências conhecidas

- `pom.xml` ainda não tem SnakeYAML, Jsoup nem Playwright — entram com a etapa 1.
- Não há `mvnw` versionado; considere adicionar o wrapper para fixar a versão do Maven.
- `InMemoryPriceHistoryStoreAdapter` devolve dado fixo de exemplo; é stub, não fonte.
- As ofertas são geradas pelos `Demo*Provider`. Nenhum preço no app é real hoje.
- `SearchSessionStore` é um mapa em memória com TTL de 30 min: reiniciar o app perde as buscas em
  andamento. Aceitável para uso pessoal, não para mais de uma instância.
- Nomenclatura: a spec fala em `StayOffer`/`StayOfferProvider`, o código tem
  `LodgingOffer`/`LodgingProviderPort`. **Mantenha os nomes do código** e trate os da
  spec como sinônimos, ou renomeie tudo de uma vez num commit isolado — não misture.
