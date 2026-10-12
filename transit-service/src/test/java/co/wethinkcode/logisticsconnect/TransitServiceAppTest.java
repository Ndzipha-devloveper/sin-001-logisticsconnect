package co.wethinkcode.logisticsconnect;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.javalin.Javalin;

class TransitServiceAppTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final ConcurrentMap<String, Integer> STAGES = new ConcurrentHashMap<>();

    private static Javalin app;
    private static String baseUrl;

    @BeforeAll
    static void startServer() {
        STAGES.put("H-501", 2);
        app = TransitServiceApp.createApp(
                hubId -> "NO-HUB".equals(hubId) ? null
                        : Map.of("hubId", hubId, "province", "Gauteng",
                                "sortingCenter", "Johannesburg Central"),
                STAGES).start(0);
        baseUrl = "http://localhost:" + app.port();
    }

    @AfterAll
    static void stopServer() {
        if (app != null) {
            app.stop();
        }
    }

    @Test
    void mainMethodIsPresent() throws NoSuchMethodException {
        assertNotNull(TransitServiceApp.class.getDeclaredMethod("main", String[].class));
    }

    @Test
    void transitEndpointReturnsEtaUsingTheCachedDelayStage() throws Exception {
        JsonNode response = MAPPER.readTree(get("/transit/h-501").body());

        assertEquals("H-501", response.get("hubId").asText());
        assertEquals("Gauteng", response.get("province").asText());
        assertEquals(2, response.get("delayStage").asInt());
        assertEquals(30, response.get("estimatedHours").asInt());
        assertTrue(response.get("estimatedArrival").asText().endsWith("Z"));
    }

    @Test
    void transitEndpointReturns404WhenTheHubDoesNotExist() throws Exception {
        HttpResponse<String> response = get("/transit/NO-HUB");

        assertEquals(404, response.statusCode());
    }

    private static HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .GET()
                .build();
        return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
