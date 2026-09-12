package com.sp.syncPoint.common.job;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Pings this service's OWN public URL every 3 minutes, so Render sees
 * ongoing inbound traffic and never spins the instance down for
 * inactivity — with no dependency on anyone having the app open (contrast
 * the client's own keepAlive.ts, which does the same thing but only while
 * a browser tab actually has it open).
 *
 * <p>MUST go over the public internet, not localhost: Render's idle
 * detector watches its own reverse proxy for inbound requests, not
 * anything happening purely inside the container's own network namespace.
 * A loopback call would compile, run, and do nothing whatsoever to
 * prevent a spin-down. That's why this reads {@code base-url} — set
 * BASE_URL in Render's dashboard to this service's real
 * https://*.onrender.com address, or this job is a no-op in production
 * (it'll just ping localhost forever).
 *
 * <p>LIMIT, stated plainly: this can only keep an ALREADY-RUNNING instance
 * warm. Nothing can run a @Scheduled method on an instance that has
 * already spun down — there is no process alive to run it. The very
 * first request after a genuine cold start (right after a deploy, or
 * after Render restarts the instance for any other reason) still pays
 * the cold-start cost; this only stops every SUBSEQUENT spin-down once
 * it's running.
 */
@Slf4j
@Component
public class KeepAliveJob {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Value("${base-url:http://localhost:2000}")
    private String baseUrl;

    @Value("${app.title}")
    private String apiTitle;

    /**
     * fixedRate, not fixedDelay: this should fire every 3 minutes on the
     * clock regardless of how long the previous ping took, not 3 minutes
     * after the previous one FINISHED — the two only differ under a slow
     * or hanging request, and fixedRate is the one that actually keeps a
     * steady cadence of inbound traffic for Render to see.
     */
    @Scheduled(fixedRate = 180_000)
    public void ping() {
        URI uri = URI.create(baseUrl + "/" + apiTitle + "/keep-alive");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        try {
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            log.debug("Self-ping to {} returned {}", uri, response.statusCode());
        } catch (IOException e) {
            // Fire-and-forget, same reasoning as the client's own keep-alive
            // ping — a failed self-ping (instance still booting, brief
            // network hiccup) isn't worth doing anything about beyond
            // trying again in 3 minutes.
            log.warn("Self-ping to {} failed", uri, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Self-ping to {} interrupted", uri);
        }
    }
}
