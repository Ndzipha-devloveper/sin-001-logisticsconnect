package co.wethinkcode.logisticsconnect;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

public class HubServiceApp {

    private static final String INGESTION_URL =
            "http://localhost:7050";

    private static final HttpClient client =
            HttpClient.newHttpClient();

    private static final ObjectMapper mapper =
            new ObjectMapper();

    /**
     * This function starts the Hub Service on port 7051
     * and sets up the endpoints used by the service.
     */
    public static void main(String[] args) {
        Javalin app = createApp();
        app.start(7051);
    }

    /**
     * This function creates the Javalin app and adds
     * all the endpoints used by the Hub Service.
     */
    private static Javalin createApp() {
        Javalin app = Javalin.create();

        addHealthEndpoint(app);
        addHubsEndpoint(app);
        addProvincesEndpoint(app);
        addHubLookupEndpoint(app);
        addSortingCentersEndpoint(app);

        return app;
    }

    /**
     * This function adds the health endpoint so we can
     * check if the Hub Service is running.
     */
    private static void addHealthEndpoint(Javalin app) {
        app.get("/health", ctx -> ctx.result("OK"));
    }

    /**
     * This function gets the cleaned hubs from the
     * Ingestion Service and returns them to the client.
     */
    private static void addHubsEndpoint(Javalin app) {
        app.get("/hubs", ctx -> {

            try {
                ctx.json(getHubs());

            } catch (Exception exception) {
                ctx.status(502).json(
                        Map.of("error", "Ingestion Service is unavailable")
                );
            }
        });
    }

    /**
     * This function gets all the hubs and uses them
     * to build a list of unique provinces.
     */
    private static void addProvincesEndpoint(Javalin app) {
        app.get("/provinces", ctx -> {

            try {
                List<Map<String, Object>> hubs = getHubs();

                List<String> provinces = hubs.stream()
                        .map(hub -> (String) hub.get("province"))
                        .filter(province -> province != null)
                        .distinct()
                        .toList();

                ctx.json(provinces);

            } catch (Exception exception) {
                ctx.status(502).json(
                        Map.of("error", "Ingestion Service is unavailable")
                );
            }
        });
    }

    /**
     * This function finds a hub using the ID provided
     * in the URL and returns that hub.
     */
    private static void addHubLookupEndpoint(Javalin app) {
        app.get("/hubs/{hubId}", ctx -> {

            String hubId = ctx.pathParam("hubId");

            try {
                List<Map<String, Object>> hubs = getHubs();

                Map<String, Object> hub = hubs.stream()
                        .filter(item -> hubId.equals(item.get("hubId")))
                        .findFirst()
                        .orElse(null);

                if (hub == null) {
                    ctx.status(404).json(
                            Map.of("error", "Hub not found")
                    );
                    return;
                }

                ctx.json(hub);

            } catch (Exception exception) {
                ctx.status(502).json(
                        Map.of("error", "Ingestion Service is unavailable")
                );
            }
        });
    }

    /**
     * This function gets all the hubs and returns the
     * sorting centres belonging to the requested province.
     */
    private static void addSortingCentersEndpoint(Javalin app) {
        app.get("/sorting-centers/{province}", ctx -> {

            String province = ctx.pathParam("province");

            try {
                List<Map<String, Object>> hubs = getHubs();

                List<String> sortingCenters = hubs.stream()
                        .filter(hub -> province.equalsIgnoreCase(
                                (String) hub.get("province")
                        ))
                        .map(hub -> (String) hub.get("sortingCenter"))
                        .filter(center -> center != null)
                        .distinct()
                        .toList();

                ctx.json(sortingCenters);

            } catch (Exception exception) {
                ctx.status(502).json(
                        Map.of("error", "Ingestion Service is unavailable")
                );
            }
        });
    }

    /**
     * This function calls the Ingestion Service and gets
     * the cleaned hub data from its /hubs endpoint.
     */
    private static List<Map<String, Object>> getHubs()
            throws IOException, InterruptedException {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(INGESTION_URL + "/hubs"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            throw new IOException("Could not get hubs from Ingestion Service");
        }

        return mapper.readValue(
                response.body(),
                new TypeReference<List<Map<String, Object>>>() {}
        );
    }
}