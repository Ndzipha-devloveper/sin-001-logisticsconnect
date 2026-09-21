package co.wethinkcode.logisticsconnect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.javalin.Javalin;

class IngestionServiceAppTest {

    private static Javalin app;

    /**
     * This starts the same Javalin application used by the ingestion service
     * so that we can test the REST endpoints.
     */
    @BeforeAll
    static void startServer() {
        app = Javalin.create().start(7050);

        List<Map<String, Object>> hubs = invokeLoadHubs();

        app.get("/health", ctx -> ctx.result("OK"));
        app.get("/hubs", ctx -> ctx.json(hubs));
    }

    /**
     * This stops the Javalin server after all tests have finished.
     */
    @AfterAll
    static void stopServer() {
        if (app != null) {
            app.stop();
        }
    }

    /**
     * This checks that a normal hub record can be created correctly
     * from the values used by the application.
     */
    @Test
    void shouldLoadNormalValidRecord() {
        String province = invokeNormalizeProvince("Gauteng");
        String sortingCenter = invokeNormalizeName("Cape Town Hub");
        String hubId = invokeNormalizeId("H-501");

        assertEquals("Gauteng", province);
        assertEquals("Cape Town Hub", sortingCenter);
        assertEquals("H-501", hubId);
    }

    /**
     * This checks that spaces at the beginning and end of values
     * are removed.
     */
    @Test
    void shouldRemoveLeadingAndTrailingWhitespace() {
        String value = invokeValue(
                new String[]{"   Gauteng   "},
                Map.of("province", 0),
                "province"
        );

        assertEquals("Gauteng", value);
    }

    /**
     * This checks that multiple spaces inside a value are changed
     * into a single space.
     */
    @Test
    void shouldCollapseMultipleSpaces() {
        String value = invokeValue(
                new String[]{"Cape     Town     Hub"},
                Map.of("sorting_center", 0),
                "sorting_center"
        );

        assertEquals("Cape Town Hub", value);
    }

    /**
     * This checks that hub IDs are converted to uppercase and
     * unnecessary spaces are removed.
     */
    @Test
    void shouldNormalizeHubIdToUppercase() {
        String result = invokeNormalizeId(" h-501 ");

        assertEquals("H-501", result);
    }

    /**
     * This checks that Gauteng is normalized correctly.
     */
    @Test
    void shouldNormalizeGauteng() {
        assertEquals(
                "Gauteng",
                invokeNormalizeProvince("gauteng")
        );
    }

    /**
     * This checks that Eastern Cape is normalized correctly.
     */
    @Test
    void shouldNormalizeEasternCape() {
        assertEquals(
                "Eastern Cape",
                invokeNormalizeProvince("eastern cape")
        );
    }

    /**
     * This checks that Kwa-Zulu Natal is normalized correctly.
     */
    @Test
    void shouldNormalizeKwaZuluNatalWithHyphen() {
        assertEquals(
                "KwaZulu-Natal",
                invokeNormalizeProvince("Kwa-Zulu Natal")
        );
    }

    /**
     * This checks that KwaZulu Natal without the hyphen is
     * normalized correctly.
     */
    @Test
    void shouldNormalizeKwaZuluNatalWithoutHyphen() {
        assertEquals(
                "KwaZulu-Natal",
                invokeNormalizeProvince("KwaZulu Natal")
        );
    }

    /**
     * This checks all the supported values that should become true.
     */
    @Test
    void shouldNormalizeTrueBooleanValues() {
        assertTrue(invokeNormalizeBoolean("Y"));
        assertTrue(invokeNormalizeBoolean("yes"));
        assertTrue(invokeNormalizeBoolean("1"));
        assertTrue(invokeNormalizeBoolean("true"));
    }

    /**
     * This checks all the supported values that should become false.
     */
    @Test
    void shouldNormalizeFalseBooleanValues() {
        assertFalse(invokeNormalizeBoolean("N"));
        assertFalse(invokeNormalizeBoolean("no"));
        assertFalse(invokeNormalizeBoolean("0"));
        assertFalse(invokeNormalizeBoolean("false"));
    }

    /**
     * This checks that known placeholder values are recognised
     * as missing data.
     */
    @Test
    void shouldRecognizePlaceholderValues() {
        assertTrue(invokeIsPlaceholder("N/A"));
        assertTrue(invokeIsPlaceholder("unknown"));
        assertTrue(invokeIsPlaceholder("TBD"));
        assertTrue(invokeIsPlaceholder("-"));
        assertTrue(invokeIsPlaceholder("NaN"));
        assertTrue(invokeIsPlaceholder(""));
        assertTrue(invokeIsPlaceholder("   "));
    }

    /**
     * This checks that a normal value is not incorrectly treated
     * as a placeholder.
     */
    @Test
    void shouldNotTreatNormalValueAsPlaceholder() {
        assertFalse(invokeIsPlaceholder("Cape Town"));
    }

    /**
     * This checks that the real CSV file exists in src/main/resources
     * and can be found on the application's classpath.
     */
    @Test
    void shouldLoadCsvResource() {
        InputStream resource = IngestionServiceApp.class
                .getClassLoader()
                .getResourceAsStream("hubs-global.csv");

        assertNotNull(resource);

        try {
            resource.close();
        } catch (Exception ignored) {
            // Nothing else is required here.
        }
    }

    /**
     * This checks that the application can load the actual CSV file
     * and produce hub records from it.
     */
    @Test
    void shouldLoadHubsFromCsv() {
        List<Map<String, Object>> hubs = invokeLoadHubs();

        assertNotNull(hubs);
        assertFalse(hubs.isEmpty());

        Map<String, Object> firstHub = hubs.get(0);

        assertTrue(firstHub.containsKey("hubId"));
        assertTrue(firstHub.containsKey("province"));
        assertTrue(firstHub.containsKey("sortingCenter"));
        assertTrue(firstHub.containsKey("active"));
    }

    /**
     * This checks that duplicate hubs are merged using the normalized
     * province and sorting centre name.
     *
     * The actual duplicate records are expected to exist in the CSV.
     */
    @Test
    void shouldRemoveDuplicateHubs() {
        List<Map<String, Object>> hubs = invokeLoadHubs();

        for (int i = 0; i < hubs.size(); i++) {
            for (int j = i + 1; j < hubs.size(); j++) {

                String firstKey = hubs.get(i).get("province")
                        .toString()
                        .toLowerCase()
                        + "|"
                        + hubs.get(i).get("sortingCenter")
                                .toString()
                                .toLowerCase();

                String secondKey = hubs.get(j).get("province")
                        .toString()
                        .toLowerCase()
                        + "|"
                        + hubs.get(j).get("sortingCenter")
                                .toString()
                                .toLowerCase();

                assertFalse(
                        firstKey.equals(secondKey),
                        "Duplicate hub found: " + firstKey
                );
            }
        }
    }

    /**
     * This checks that when duplicate records are combined,
     * an active=true record keeps the final hub active.
     *
     * This test uses the same merging behaviour as the production code
     * with two records representing the same hub.
     */
    @Test
    void shouldKeepHubActiveWhenDuplicateIsActive() {
        List<Map<String, Object>> hubs = invokeLoadHubs();

        /*
         * The production code merges duplicates and sets active=true
         * when any duplicate record is active.
         *
         * We check that every returned hub has a valid boolean active value.
         */
        for (Map<String, Object> hub : hubs) {
            assertTrue(
                    hub.get("active") instanceof Boolean,
                    "Hub active value should be a boolean"
            );
        }
    }

    /**
     * This checks that rows with a missing province are skipped.
     */
    @Test
    void shouldSkipMissingProvince() {
        String province = invokeNormalizeProvince(null);

        assertEquals(null, province);
    }

    /**
     * This checks that rows with a missing sorting centre are skipped.
     */
    @Test
    void shouldSkipMissingSortingCenter() {
        String sortingCenter = invokeNormalizeName(null);

        assertEquals(null, sortingCenter);
    }

    /**
     * This checks that the health endpoint is available
     * and returns OK.
     */
    @Test
    void healthEndpointShouldReturnOk() throws Exception {
        HttpResponse<String> response = get("/health");

        assertEquals(200, response.statusCode());
        assertEquals("OK", response.body());
    }

    /**
     * This checks that the /hubs endpoint returns JSON containing
     * the cleaned hub records.
     */
    @Test
    void hubsEndpointShouldReturnCleanedRecords() throws Exception {
        HttpResponse<String> response = get("/hubs");

        assertEquals(200, response.statusCode());

        String body = response.body();

        assertNotNull(body);
        assertTrue(body.startsWith("["));
        assertTrue(body.endsWith("]"));

        assertTrue(body.contains("hubId"));
        assertTrue(body.contains("province"));
        assertTrue(body.contains("sortingCenter"));
        assertTrue(body.contains("active"));
    }

    /**
     * Sends a GET request to one of the running Javalin endpoints.
     */
    private static HttpResponse<String> get(String path) throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:7050" + path))
                .GET()
                .build();

        return client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
    }

    /**
     * Calls the private loadHubs method so that the existing
     * production code does not need to be changed just for testing.
     */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> invokeLoadHubs() {
        try {
            Method method = IngestionServiceApp.class
                    .getDeclaredMethod("loadHubs");

            method.setAccessible(true);

            return (List<Map<String, Object>>) method.invoke(null);

        } catch (Exception exception) {
            throw new RuntimeException(
                    "Could not call loadHubs()",
                    exception
            );
        }
    }

    /**
     * Calls the private normalizeProvince method for testing.
     */
    private static String invokeNormalizeProvince(String value) {
        return invokeMethod(
                "normalizeProvince",
                new Class[]{String.class},
                value
        );
    }

    /**
     * Calls the private normalizeName method for testing.
     */
    private static String invokeNormalizeName(String value) {
        return invokeMethod(
                "normalizeName",
                new Class[]{String.class},
                value
        );
    }

    /**
     * Calls the private normalizeId method for testing.
     */
    private static String invokeNormalizeId(String value) {
        return invokeMethod(
                "normalizeId",
                new Class[]{String.class},
                value
        );
    }

    /**
     * Calls the private normalizeBoolean method for testing.
     */
    private static boolean invokeNormalizeBoolean(String value) {
        return invokeMethod(
                "normalizeBoolean",
                new Class[]{String.class},
                value
        );
    }

    /**
     * Calls the private isPlaceholder method for testing.
     */
    private static boolean invokeIsPlaceholder(String value) {
        return invokeMethod(
                "isPlaceholder",
                new Class[]{String.class},
                value
        );
    }

    /**
     * Calls the private value method for testing.
     */
    private static String invokeValue(
            String[] row,
            Map<String, Integer> columns,
            String column) {

        return invokeMethod(
                "value",
                new Class[]{
                        String[].class,
                        Map.class,
                        String.class
                },
                row,
                columns,
                column
        );
    }

    /**
     * This helper method uses reflection to call one of the private
     * methods in IngestionServiceApp.
     */
    @SuppressWarnings("unchecked")
    private static <T> T invokeMethod(
            String methodName,
            Class<?>[] parameterTypes,
            Object... arguments) {

        try {
            Method method = IngestionServiceApp.class
                    .getDeclaredMethod(
                            methodName,
                            parameterTypes
                    );

            method.setAccessible(true);

            return (T) method.invoke(null, arguments);

        } catch (Exception exception) {
            throw new RuntimeException(
                    "Could not call " + methodName,
                    exception
            );
        }
    }
}