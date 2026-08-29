package com.smarttravel.analyzer.presentation.rest;

import com.smarttravel.analyzer.infrastructure.adapter.image.ImageProxyPolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/**
 * Serve as fotos dos anuncios e os logos das companhias pelo backend.
 * Hotlink direto no CDN de Booking e Airbnb e bloqueado por referer; buscar aqui resolve
 * isso e evita bater no CDN deles a cada render do navegador.
 */
@RestController
@RequestMapping("/api/img")
public class ImageController {

    private static final int MAX_REDIRECIONAMENTOS = 3;

    /**
     * NEVER de proposito: o cliente nao segue redirecionamento sozinho.
     * Seguir as cegas deixaria um host permitido apontar para a rede interna;
     * aqui cada salto e revalidado contra a allowlist antes de ser buscado.
     */
    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(6))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();

    @GetMapping
    public ResponseEntity<byte[]> proxy(@RequestParam String ref) {
        var alvo = ref;
        try {
            for (int salto = 0; salto <= MAX_REDIRECIONAMENTOS; salto++) {
                if (!ImageProxyPolicy.allows(alvo)) return ResponseEntity.badRequest().build();
                var resposta = buscar(alvo);
                if (ehRedirecionamento(resposta.statusCode())) {
                    var destino = resposta.headers().firstValue("location").orElse(null);
                    if (destino == null) return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
                    alvo = URI.create(alvo).resolve(destino).toString();
                    continue;
                }
                if (resposta.statusCode() != 200) return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
                var tipo = resposta.headers().firstValue("content-type").orElse("image/jpeg");
                if (!tipo.startsWith("image/")) return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
                return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(tipo))
                    .cacheControl(CacheControl.maxAge(Duration.ofHours(6)).cachePublic())
                    .body(resposta.body());
            }
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        } catch (java.io.IOException falha) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        } catch (InterruptedException interrompida) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    private static boolean ehRedirecionamento(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private static HttpResponse<byte[]> buscar(String url) throws java.io.IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(10))
            .header("User-Agent", "Mozilla/5.0")
            .GET().build();
        return CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }
}
