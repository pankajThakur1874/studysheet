package net.tridha.studysheet.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class RenderKeepAliveService {

    private static final Logger log = LoggerFactory.getLogger(RenderKeepAliveService.class);

    @Value("${RENDER_EXTERNAL_URL:${APP_PING_URL:}}")
    private String renderExternalUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Executes every 14 minutes (840,000 ms) to keep the Render free tier service active.
     * Render puts free services to sleep after 15 minutes of inactivity on external HTTP traffic.
     */
    @Scheduled(fixedRate = 840000, initialDelay = 60000)
    public void keepAlivePing() {
        if (renderExternalUrl == null || renderExternalUrl.isBlank()) {
            log.debug("Render keep-alive ping skipped: RENDER_EXTERNAL_URL environment variable is not configured.");
            return;
        }

        try {
            String baseUrl = renderExternalUrl.trim().replaceAll("/+$", "");
            String targetEndpoint = baseUrl + "/api/ping";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetEndpoint))
                    .header("User-Agent", "StudySheet-KeepAlive/1.0")
                    .GET()
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("Render keep-alive self-ping sent to [{}]. Response Status: {}", targetEndpoint, response.statusCode());
        } catch (Exception e) {
            log.warn("Render keep-alive self-ping attempt failed: {}", e.getMessage());
        }
    }
}
