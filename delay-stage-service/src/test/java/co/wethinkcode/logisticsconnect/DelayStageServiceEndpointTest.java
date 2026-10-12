package co.wethinkcode.logisticsconnect;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.javalin.Javalin;

class DelayStageServiceEndpointTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private static Javalin app;
    private static String baseUrl;
    private static final ConcurrentMap<String, List<Integer>> publishedStages = new ConcurrentHashMap<>();

    @BeforeAll
    static void startServer() {
        app = DelayStageServiceApp.createApp((hubId, stage) -> publishedStages
            .computeIfAbsent(hubId, ignored -> new CopyOnWriteArrayList<>())
            .add(stage)).start(0);
        baseUrl = "http://localhost:" + app.port();
    }

    @AfterAll
    static void stopServer() {
        if (app != null) {
            app.stop();
        }
    }

    @Test
    void health_returnsOk() throws Exception {
        HttpResponse<String> response = get("/health");

        assertEquals(200, response.statusCode());
        assertEquals("OK", response.body());
    }

    @Test
    void get_returnsZeroForAnUnsetHubAndNormalisesTheHubId() throws Exception {
        JsonNode response = json(get("/delay-stage/ep-never-set"));

        assertEquals("EP-NEVER-SET", response.get("hubId").asText());
        assertEquals(0, response.get("delayStage").asInt());
    }

    @Test
    void post_thenGet_returnsTheStoredStage() throws Exception {
        HttpResponse<String> posted = post("/delay-stage/EP-ROUNDTRIP", "{\"delayStage\":6}");

        assertEquals(200, posted.statusCode());
        assertEquals(6, json(get("/delay-stage/EP-ROUNDTRIP")).get("delayStage").asInt());
    }

    @Test
    void post_reportsWhetherTheStageChanged() throws Exception {
        JsonNode first = json(post("/delay-stage/EP-CHANGED", "{\"delayStage\":4}"));
        JsonNode repeated = json(post("/delay-stage/EP-CHANGED", "{\"delayStage\":4}"));
        JsonNode changed = json(post("/delay-stage/EP-CHANGED", "{\"delayStage\":5}"));

        assertTrue(first.get("stageChanged").asBoolean());
        assertFalse(repeated.get("stageChanged").asBoolean());
        assertTrue(changed.get("stageChanged").asBoolean());
    }

    @Test
    void post_publishesOnlyValidStageChanges() throws Exception {
        post("/delay-stage/EP-PUBLISH-TEST", "{\"delayStage\":2}");
        post("/delay-stage/EP-PUBLISH-TEST", "{\"delayStage\":2}");
        post("/delay-stage/EP-PUBLISH-TEST", "{\"delayStage\":99}");
        post("/delay-stage/EP-PUBLISH-TEST", "{\"delayStage\":5}");

        assertEquals(List.of(2, 5), publishedStages.get("EP-PUBLISH-TEST"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 8})
    void post_acceptsBothStageBoundaries(int stage) throws Exception {
        HttpResponse<String> response = post("/delay-stage/EP-BOUND-" + stage,
                "{\"delayStage\":" + stage + "}");

        assertEquals(200, response.statusCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"delayStage\":9}", "{\"delayStage\":-1}", "{\"delayStage\":\"3\"}",
            "{\"delayStage\":2.5}", "{\"stage\":3}", "{}", "not-json", ""
    })
    void post_returns400ForInvalidBodies(String body) throws Exception {
        HttpResponse<String> response = post("/delay-stage/EP-BAD-BODY", body);

        assertEquals(400, response.statusCode());
        assertTrue(json(response).has("error"));
    }

    @Test
    void post_invalidBodyDoesNotOverwriteTheStoredStage() throws Exception {
        post("/delay-stage/EP-KEEP", "{\"delayStage\":3}");

        post("/delay-stage/EP-KEEP", "{\"delayStage\":99}");

        assertEquals(3, json(get("/delay-stage/EP-KEEP")).get("delayStage").asInt());
    }

    private static HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .GET()
                .build();
        return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static JsonNode json(HttpResponse<String> response) throws Exception {
        return MAPPER.readTree(response.body());
    }
}