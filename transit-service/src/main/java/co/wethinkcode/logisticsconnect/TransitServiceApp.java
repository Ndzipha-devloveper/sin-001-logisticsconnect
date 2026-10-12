package co.wethinkcode.logisticsconnect;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.wethinkcode.logisticsconnect.mq.JmsStageSubscriber;
import io.javalin.Javalin;

public class TransitServiceApp {

    private static final Logger logger = LoggerFactory.getLogger(TransitServiceApp.class);
    private static final long BASELINE_HOURS = 24;
    private static final long HOURS_PER_DELAY_STAGE = 3;

    public static void main(String[] args) {
        JmsStageSubscriber subscriber = new JmsStageSubscriber();
        subscriber.start();
        Javalin app = createApp(new HttpHubLookup(), subscriber.getStages()).start(7053);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            app.stop();
            subscriber.close();
        }, "transit-service-shutdown"));
    }

    static Javalin createApp(HubLookup hubLookup, ConcurrentMap<String, Integer> stages) {
        Javalin app = Javalin.create();
        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/transit/{hubId}", ctx -> {
            String hubId = ctx.pathParam("hubId").trim().toUpperCase();
            try {
                Map<String, Object> hub = hubLookup.findHub(hubId);
                if (hub == null) {
                    ctx.status(404).json(Map.of("error", "Hub not found"));
                    return;
                }

                int stage = stages.getOrDefault(hubId, 0);
                long estimatedHours = BASELINE_HOURS + (long) stage * HOURS_PER_DELAY_STAGE;
                ctx.json(Map.of(
                        "hubId", hubId,
                        "province", hub.getOrDefault("province", ""),
                        "sortingCenter", hub.getOrDefault("sortingCenter", ""),
                        "delayStage", stage,
                        "estimatedHours", estimatedHours,
                        "estimatedArrival", Instant.now().plusSeconds(estimatedHours * 3600).toString()
                ));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                ctx.status(502).json(Map.of("error", "Hub Service request was interrupted"));
            } catch (IOException exception) {
                logger.warn("Hub Service is unavailable while calculating transit for {}", hubId, exception);
                ctx.status(502).json(Map.of("error", "Hub Service is unavailable"));
            }
        });

        return app;
    }

    @FunctionalInterface
    interface HubLookup {
        Map<String, Object> findHub(String hubId) throws IOException, InterruptedException;
    }

    private static final class HttpHubLookup implements HubLookup {

        private final HttpClient client = HttpClient.newHttpClient();
        private final com.fasterxml.jackson.databind.ObjectMapper mapper =
            new com.fasterxml.jackson.databind.ObjectMapper();
        private final String hubServiceUrl = System.getProperty(
                "hub.service.url", "http://localhost:7051");

        @Override
        public Map<String, Object> findHub(String hubId) throws IOException, InterruptedException {
            String encodedHubId = URLEncoder.encode(hubId, StandardCharsets.UTF_8).replace("+", "%20");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(hubServiceUrl + "/hubs/" + encodedHubId))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                return null;
            }
            if (response.statusCode() != 200) {
                throw new IOException("Hub Service returned HTTP " + response.statusCode());
            }
            return mapper.readValue(response.body(), new com.fasterxml.jackson.core.type.TypeReference<>() {
            });
        }
    }
}
