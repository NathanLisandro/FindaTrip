# Design — Buscador de Pacote Ideal

**Data:** 2026-08-28
**Estado do projeto:** domínio pronto e testado; nenhuma fonte de dado conectada.

## Objetivo

O usuário informa origem, destino, data de ida e data de volta. A busca roda (pode
demorar) e devolve um **Pacote Ideal** — voo + hospedagem + carro — escolhido por custo
real e por avaliação dos usuários, com filtros aplicáveis ao resultado.

Ao final desta rodada o produto funciona de ponta a ponta com **dados de demonstração**,
claramente rotulados como tal na tela. Fontes reais entram depois, atrás das mesmas
portas, sem alterar domínio, aplicação ou front.

## O que já existe e não muda

- `CostNormalizerDomainService` — custo real: taxa de aeroporto, bagagem, seguro total.
- `ValueScoringDomainService` — rating bayesiano, conveniência de voo/hotel/carro,
  score ponderado (preço .45, qualidade .35, conveniência .20).
- `PackageBundlerDomainService` — escolhe os três perfis entre candidatos prontos.
- `Money`, `Score`, `NormalizedPrice` e os modelos de oferta.
- Portas `FlightProviderPort`, `LodgingProviderPort`, `CarRentalProviderPort`.

## Lacunas a preencher

### 1. Montagem de pacotes (domínio)

Não existe quem combine ofertas em candidatos — por isso
`SearchBestValuePackagesUseCase` devolve três pacotes escritos à mão, ignorando o que o
usuário digitou.

**`PackageAssemblerDomainService`**: recebe as três listas de ofertas e o `SearchCriteria`,
combina, normaliza cada parte, calcula os três scores e devolve os candidatos.

**`TravelPackage` passa a carregar as ofertas que o compõem** (voo, hospedagem, carro) e
os `NormalizedPrice` de cada parte. Hoje guarda só id, preço total e notas — sem isso a
tela não tem como mostrar qual hotel nem justificar a escolha.

Combinação: produto cartesiano limitado. Para não explodir, cada dimensão entra ordenada
por custo normalizado crescente e truncada nas N mais baratas (N=5), teto de 125
candidatos. O ranking por qualidade acontece depois, sobre os candidatos montados.

### 2. Busca assíncrona

Busca demorada não pode ser um POST pendurado.

```
POST /api/search      → { searchId }         (retorna imediatamente)
GET  /api/search/{id} → { status, progresso, fontes[], pacotes[] }
```

`status`: `BUSCANDO` | `PRONTO` | `PARCIAL` | `ERRO`.
`fontes[]`: uma linha por provider com `OK | DEGRADADO | BUSCANDO`.

Fonte que falha não derruba a busca: o resultado sai `PARCIAL` com a fonte marcada.
Nenhuma exceção de provider sobe para o controller.

Estado em memória, com expiração por TTL. Sem banco nesta rodada.

### 3. Providers de demonstração

`DemoFlightProvider`, `DemoLodgingProvider`, `DemoCarRentalProvider` implementam as portas
existentes e geram ofertas variadas e coerentes a partir de origem, destino, datas e
número de viajantes — determinísticas por semente derivada do critério, para a mesma
busca dar o mesmo resultado.

Cada provider declara `isDemo()`. A resposta da API carrega essa marca e a tela exibe um
aviso permanente de dado simulado. Trocar por fonte real é trocar o bean em `BeanConfig`.

### 4. Front

Formulário: origem, destino, ida, volta, viajantes, bagagem despachada, preciso de carro.

Resultado: **Pacote Ideal** em destaque (hotel com nome e nota, voo, carro, custo total
real, o que está incluído), e abaixo Econômico Inteligente e Máximo Conforto.

Durante a busca: progresso por fonte, não um spinner mudo.

Filtros aplicados sobre o resultado já obtido, sem refazer a busca: preço máximo, nota
mínima, só voo direto, café da manhã incluso, cancelamento grátis e bairro. A filtragem roda **no
servidor**, num `PackageFilterDomainService` testável, exposto como
`GET /api/search/{id}?notaMinima=8&precoMax=2000` sobre o resultado em memória — o front
não reimplementa regra de negócio.

## Itens aprovados nesta rodada

### Moeda BRL

Todo o fluxo passa a usar BRL. `Money.add` lança em moedas diferentes, então USD
misturado com uma fonte brasileira quebraria a montagem. Correção pequena agora, bug
garantido depois.

### "Por que este pacote?"

Cada card mostra:

- preço anunciado (`NormalizedPrice.rawPrice`) versus custo real (`totalPrice`);
- o que foi somado (`includedCostAdjustments`, traduzido para português);
- a razão da escolha, derivada dos scores — inclusive a comparação bayesiana
  ("nota 8.9 com 2.000 avaliações vale mais que 10 com 3").

Dado que o domínio já produz e hoje descarta.

### Botão Reservar com link real

`DeepLinkBuilder`: função pura, sem rede, que monta a URL de busca do parceiro já
preenchida com destino, datas e viajantes. Testado por asserção de string, com casos de
`&`, espaço e acento. Vale mesmo com dado de demonstração — o link é real.

### Bairros do destino

`LodgingOffer` ganha um campo `neighborhood`. Depois da busca, a tela mostra os bairros que
realmente apareceram nas ofertas — nome, quantas opções e o preço a partir de — e clicar
num deles filtra o resultado.

A lista de bairros é derivada das próprias ofertas, não de um catálogo por cidade: nada
precisa ser cadastrado, funciona para qualquer destino, e com fonte real o bairro vem junto
do anúncio. Bairro ausente vira `"Não informado"`, nunca `null`.

O resumo por bairro é calculado **antes** dos filtros, para as opções não sumirem conforme
o usuário filtra. A comparação ignora acento e caixa.

### Flexibilidade de datas ±3 dias

Busca os dias vizinhos e mostra quanto se economiza mudando a data
("saindo na quinta em vez da sexta: −R$ 400").

Multiplica a busca por até 7. Fica atrás de um flag no critério, desligado por padrão:
com provider de demonstração é barato, com scraper real é caro. A estrutura fica pronta;
a decisão de ligar é por busca.

O resultado das datas vizinhas é um **resumo comparativo** — data e custo total do melhor
pacote de cada dia — não a lista completa de pacotes de cada data. Clicar numa data
vizinha dispara uma busca nova com aquelas datas.

## Fora de escopo, deliberadamente

- **Scrapers e fontes reais.** É a rodada seguinte, e é o trabalho difícil.
- **Gráfico de tendência de preço.** Depende de coletor rodando por semanas;
  `InMemoryPriceHistoryStoreAdapter` devolve quatro pontos fixos de exemplo.
- **Ônibus.** Sem fonte viável no Brasil, conforme a spec v6.
- **Persistência.** Busca vive em memória e expira.

## Testes

- TDD: teste primeiro, falhando, depois implementação.
- `PackageAssemblerDomainService`: combinação, truncamento, normalização, scores.
- Contract test por porta de provider; os três demos estendem a mesma bateria.
- Busca assíncrona: fonte que falha produz `PARCIAL`, nunca exceção.
- `DeepLinkBuilder`: encoding.
- Filtros: cada filtro isolado e combinados, incluindo bairro sem acento e em caixa baixa.
- Resumo por bairro: agrupamento, contagem e preço a partir de.
- Nenhum teste toca a rede.

## Correção de infraestrutura (pré-requisito)

`mvn test` hoje termina em `BUILD SUCCESS` com `Tests run: 0`: o `pom.xml` não fixa a
versão do `maven-surefire-plugin` e o Maven usa a 2.12.4 do super-POM, que ignora JUnit 5.
Os três testes existentes nunca rodaram. Fixar em 3.5.x é a primeira tarefa — sem isso não
há TDD.
