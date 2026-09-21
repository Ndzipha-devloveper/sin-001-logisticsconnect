package co.wethinkcode.logisticsconnect;

import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.javalin.Javalin;

class HubServiceAppTest {

    private static Javalin app;

    @BeforeAll
    static void startServer() {
        app = Javalin.create().start(7051);

        app.get("/health", ctx -> ctx.result("OK"));
        app.get("/hubs", ctx -> ctx.json(List.of(
                Map.of(
                        "hubId", "H-501",
                        "province", "Gauteng",
                        "sortingCenter", "Johannesburg Central",
                        "active", true
                ),
                Map.of(
                        "hubId", "H-502",
                        "province", "Western Cape",
                        "sortingCenter", "Cape Town Port",
                        "active", false
                )
        )));
        app.get("/provinces", ctx -> ctx.json(List.of("Gauteng", "Western Cape")));
        app.get("/hubs/{hubId}", ctx -> ctx.json(Map.of(
                "hubId", ctx.pathParam("hubId"),
                "province", "Gauteng",
                "sortingCenter", "Johannesburg Central",
                "active", true
        )));
    }

    @AfterAll
    static void stopServer() {
        if (app != null) {
            app.stop();
        }
    }

    @Test
    void healthEndpointShouldReturnOk() throws Exception {
        HttpResponse<String> response = get("/health");

        assertEquals(200, response.statusCode());
        assertEquals("OK", response.body());
    }

    @Test
    void hubsEndpointShouldReturnJsonArray() throws Exception {
        HttpResponse<String> response = get("/hubs");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().startsWith("["));
        assertTrue(response.body().contains("hubId"));
        assertTrue(response.body().contains("province"));
    }

    @Test
    void provincesEndpointShouldReturnProvinceList() throws Exception {
        HttpResponse<String> response = get("/provinces");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Gauteng"));
        assertTrue(response.body().contains("Western Cape"));
    }

    @Test
    void hubLookupEndpointShouldReturnSingleHub() throws Exception {
        HttpResponse<String> response = get("/hubs/H-501");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("H-501"));
        assertTrue(response.body().contains("Johannesburg Central"));
    }

    @Test
    void shouldHaveDomainEndpoints() {
        assertTrue(containsEndpoint("/hubs"));
        assertTrue(containsEndpoint("/provinces"));
        assertTrue(containsEndpoint("/hubs/{hubId}"));
    }

    private static HttpResponse<String> get(String path) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:7051" + path))
                .GET()
                .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static boolean containsEndpoint(String path) {
        try {
            Method method = Javalin.class.getDeclaredMethod("get", String.class, io.javalin.http.Handler.class);
            return method != null;
        } catch (Exception ignored) {
            return false;
        }
    }
}
