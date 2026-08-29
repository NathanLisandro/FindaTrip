# Design — Fontes reais, React e Docker

**Data:** 2026-08-29
**Decisão do usuário:** sem API paga e sem API de afiliado. Scrapers próprios, vários sites.
**Teste de aceitação:** a tela mostra preço real de **MGF → FLN, 7 a 14/11**.

## Sondagem que fundamenta este desenho

Feita em 2026-08-29, uma requisição por site. Dumps em `docs/superpowers/probes/`.

Sem navegador (curl), **nenhum** dos sites devolve preço: Booking responde 202 com desafio de
robô, Decolar 403, Hoteis.com 429 na primeira requisição, LATAM nem conecta, Smiles e Google
devolvem casca de SPA.

Com Chrome real (Playwright apontando para o `/usr/bin/google-chrome` do sistema):

| Fonte | Resultado |
|---|---|
| Google Voos | 7 resultados MGF→FLN com horário, companhia, escala e preço (a partir de R$ 639) |
| Booking | 77 preços para Florianópolis 7–14/11 |
| Google Hotels | 43 preços, diárias de R$ 117 a R$ 266 |
| Airbnb | 60 preços, R$ 284 a R$ 3.116 |
| LATAM direto | carrega, mas para em "a busca está demorando mais que o normal" |
| Smiles | deep link não leva à busca; exige formulário e provavelmente login |
| Decolar | 403 mesmo com Chrome — bloqueio duro |
| Hoteis.com | 429 com captcha — bloqueio duro |

**Consequência que define a arquitetura:** Google Voos e Google Hotels são agregadores. Eles
devolvem o preço de LATAM, GOL, Azul, Booking, Hoteis.com, Expedia e Decolar lado a lado. Cobrir
"vários sites" passa por eles, em vez de brigar com o anti-bot de cada um. Decolar e Hoteis.com
entram por essa via, não como scraper próprio.

## Fontes desta rodada

1. **GoogleFlightsScraper** → `FlightProviderPort`
2. **BookingScraper** → `LodgingProviderPort`
3. **GoogleHotelsScraper** → `LodgingProviderPort` (traz o preço de vários sites por hotel)
4. **AirbnbScraper** → `LodgingProviderPort`

**Google Hotels ficou de fora, e o motivo apareceu só ao ler a fixture:** a URL de busca
(`/travel/search?q=hoteis+{cidade}`) **não carrega as datas**. O HTML capturado traz
"1 noite, com tributos e taxas" e "28 – 29 de set." — preços de outro período. Levar as datas exige
montar o parâmetro `ts=`, que é protobuf serializado, o mesmo problema do `tfs` do Google Voos que a
v6 §3 já apontava. Mostrar preço da data errada é pior que não ter a fonte, então ela espera esse
trabalho.

Fora desta rodada, com motivo: Decolar e Hoteis.com (bloqueio duro — entram via Google Hotels,
quando ele entrar),
LATAM e Smiles diretos (exigem fluxo de formulário e login), site próprio do hotel (é um segundo
salto a partir do nome, depois das quatro acima).

Carros ficam sem fonte real nesta rodada; a porta continua com o provider de demonstração, e a
tela precisa dizer isso.

## Arquitetura de scraping

**Playwright para Java**, apontando para o Chrome do sistema (`setChannel("chrome")`) — sem baixar
navegador, sem imagem inchada.

- Uma porta `PageFetcherPort` em `domain/repository/`, com `PlaywrightPageFetcher` na
  infraestrutura. O domínio nunca vê navegador.
- Um **navegador só**, reaproveitado, com um `BrowserContext` novo por busca (locale pt-BR,
  fuso America/Sao_Paulo, viewport 1440×900, UA de Chrome real).
- Seletores em **YAML** por site, em `scrapers/`, como a v6 exige: layout muda, edita YAML, não
  recompila. Com `_meta.verificado_em`, e teste que falha o build acima de 90 dias.
- Cache de HTML em disco em `.cache/{site}/{hash}.html`, com TTL. Sem isso o bloqueio vem do
  próprio ciclo de desenvolvimento.
- Rate limit por site e timeout de 45s (a sondagem mostrou que essas páginas levam 9–14s para
  pintar o resultado).
- **Falha vira `DEGRADADO`, nunca exceção** — o que a arquitetura já faz hoje.

**Testes sem rede.** Os dumps da sondagem viram fixture em `src/test/resources/fixtures/`. O
parser é testado contra HTML salvo; só um teste de integração marcado `@Tag("rede")`, fora do
`mvn test` padrão, toca a internet.

## Front em React

Componentes, build com Vite, saída para `src/main/resources/static/`. Motivo declarado pelo
usuário: componentizar para gastar menos tokens em mudanças futuras.

Componentes: `SearchForm`, `SourceProgress`, `DemoBanner`, `NeighborhoodChips`, `FilterBar`,
`PackageCard`, `DateOptions`. Estado da busca num hook `useSearch` (POST + polling).

O aviso de dado simulado passa a ser **por fonte**, não global: com voos e hotéis reais e carro
ainda de demonstração, dizer "tudo é demonstração" seria mentira, e esconder seria pior.

## Docker

`Dockerfile` multi-stage: build Maven + build Vite → runtime JRE 21 com Chrome instalado.
`docker-compose.yml` com uma porta publicada e volume para `.cache`. A imagem precisa do Chrome e
das bibliotecas que ele exige — é o que a torna grande, e não tem como fugir disso com scraping.

## O que pode dar errado, dito antes

- **Bloqueio.** As quatro fontes funcionaram hoje, de um IP residencial. Podem bloquear amanhã,
  e volume aumenta o risco. Por isso rate limit e degradação por fonte.
- **Layout.** Seletor quebra. Por isso YAML e fixture.
- **Termos de uso.** Contraria os termos desses sites. O usuário decidiu com essa informação.
