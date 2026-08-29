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

    @Override
    public synchronized String fetchAfterVisiting(String entryUrl, String url, String waitForSelector, Duration timeout) {
        try {
            var context = novoContexto();
            try (context) {
                var page = context.newPage();
                // A pagina de entrada pode ate falhar; o que importa e o contexto de navegacao
                // que ela estabelece antes do salto pelo lado do cliente.
                try {
                    page.navigate(entryUrl, new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(timeout.toMillis()));
                } catch (PlaywrightException entradaFalhou) {
                    // segue mesmo assim
                }
                page.waitForTimeout(6000);
                page.evaluate("destino => { window.location.href = destino; }", url);

                // Esperar o RESULTADO aparecer, nao um tempo fixo. A Decolar leva de 15 a 40
                // segundos conforme a hora do dia; com espera fixa a fonte parecia bloqueada
                // quando estava apenas lenta, e devolvia zero em silencio.
                if (waitForSelector != null && !waitForSelector.isBlank()) {
                    try {
                        page.waitForSelector(waitForSelector,
                            new Page.WaitForSelectorOptions().setTimeout(timeout.toMillis()));
                        // ja apareceu o primeiro; da tempo de pintar o resto da lista
                        page.waitForTimeout(6000);
                    } catch (PlaywrightException naoApareceu) {
                        // Ultimo recurso: talvez o seletor tenha mudado, mas a pagina tenha algo.
                        page.waitForTimeout(12000);
                    }
                } else {
                    page.waitForTimeout(20000);
                }
                page.mouse().wheel(0, 2000);
                page.waitForTimeout(5000);
                return page.content();
            }
        } catch (PlaywrightException falha) {
            throw new PageFetchException("Nao foi possivel carregar " + url + " via " + entryUrl, falha);
        }
    }

    private BrowserContext novoContexto() {
        return browser().newContext(new Browser.NewContextOptions()
            .setLocale("pt-BR").setTimezoneId("America/Sao_Paulo")
            .setViewportSize(1440, 900).setUserAgent(USER_AGENT));
    }

    private Browser browser() {
        if (browser == null) {
            // Rodamos no Chrome do sistema (setChannel abaixo), entao nao ha por que baixar
            // os navegadores proprios do Playwright: sao 1,4 GB que nunca seriam usados.
            playwright = Playwright.create(new Playwright.CreateOptions()
                .setEnv(java.util.Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
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
