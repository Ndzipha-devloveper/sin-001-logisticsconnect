package co.wethinkcode.logisticsconnect;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.opencsv.CSVReader;

import io.javalin.Javalin;

public class IngestionServiceApp {

    /**
     * This function starts the ingestion service on port 7050,
     * loads the hub data, and creates the REST endpoints for
     * checking the service and getting the cleaned hubs.
     */
    public static void main(String[] args) {
        List<Map<String, Object>> hubs = loadHubs();
        Javalin app = Javalin.create().start(7050);

        app.get("/health", ctx -> ctx.result("OK"));
        app.get("/hubs", ctx -> ctx.json(hubs));
    }

    /**
     * This function reads the CSV file, cleans the hub information,
     * removes invalid records and duplicates, and returns the
     * cleaned hub data.
     */
    private static List<Map<String, Object>> loadHubs() {
        InputStream resource = IngestionServiceApp.class
                .getClassLoader()
                .getResourceAsStream("hubs-global.csv");

        if (resource == null) {
            throw new IllegalStateException(
                    "hubs-global.csv was not found on the classpath");
        }

        Map<String, Map<String, Object>> uniqueHubs = new LinkedHashMap<>();

        try (resource;
             CSVReader reader = new CSVReader(
                     new InputStreamReader(resource, StandardCharsets.UTF_8))) {

            String[] header = reader.readNext();

            if (header == null) {
                return List.of();
            }

            Map<String, Integer> columns = indexColumns(header);

            String[] row;

            while ((row = reader.readNext()) != null) {

                String province = normalizeProvince(
                        value(row, columns, "province"));

                String sortingCenter = normalizeName(
                        value(row, columns, "sorting_center"));

                if (province == null || sortingCenter == null) {
                    continue;
                }

                String key = province.toLowerCase(Locale.ROOT)
                        + "|"
                        + sortingCenter.toLowerCase(Locale.ROOT);

                Map<String, Object> hub = uniqueHubs.get(key);

                boolean active = normalizeBoolean(
                        value(row, columns, "active"));

                if (hub == null) {
                    hub = new LinkedHashMap<>();

                    hub.put(
                            "hubId",
                            normalizeId(value(row, columns, "hub_id"))
                    );

                    hub.put("province", province);
                    hub.put("sortingCenter", sortingCenter);
                    hub.put("active", active);

                    uniqueHubs.put(key, hub);

                } else if (active) {
                    hub.put("active", true);
                }
            }

        } catch (IOException
                | com.opencsv.exceptions.CsvValidationException exception) {

            throw new IllegalStateException(
                    "Could not read hubs-global.csv",
                    exception
            );
        }

        return new ArrayList<>(uniqueHubs.values());
    }

    /**
     * This function goes through the CSV header and creates a map
     * that tells us which position each column is in.
     */
    private static Map<String, Integer> indexColumns(String[] header) {
        Map<String, Integer> columns = new LinkedHashMap<>();

        for (int index = 0; index < header.length; index++) {
            columns.put(
                    header[index].trim().toLowerCase(Locale.ROOT),
                    index
            );
        }

        return columns;
    }

    /**
     * This function gets a value from a specific CSV column,
     * removes unnecessary spaces, and ignores placeholder values
     * such as "N/A" and "unknown".
     */
    private static String value(
            String[] row,
            Map<String, Integer> columns,
            String column) {

        Integer index = columns.get(column);

        if (index == null || index >= row.length) {
            return null;
        }

        String value = row[index]
                .trim()
                .replaceAll("\\s+", " ");

        return isPlaceholder(value) ? null : value;
    }

    /**
     * This function cleans the hub ID by removing spaces
     * and converting it to uppercase.
     */
    private static String normalizeId(String value) {
        return value == null
                ? null
                : value.toUpperCase(Locale.ROOT)
                        .replaceAll("\\s+", "");
    }

    /**
     * This function cleans the sorting centre name
     * and converts it into a consistent format.
     */
    private static String normalizeName(String value) {
        return value == null ? null : toTitleCase(value);
    }

    /**
     * This function cleans province names and makes sure
     * different spellings of the same province are changed
     * into one standard name.
     */
    private static String normalizeProvince(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value
                .toLowerCase(Locale.ROOT)
                .replace("-", " ")
                .replaceAll("\\s+", " ")
                .trim();

        Map<String, String> provinceNames = Map.of(
                "gauteng", "Gauteng",
                "western cape", "Western Cape",
                "kwazulu natal", "KwaZulu-Natal",
                "kwa zulu natal", "KwaZulu-Natal",
                "free state", "Free State",
                "eastern cape", "Eastern Cape",
                "limpopo", "Limpopo",
                "north west", "North West",
                "mpumalanga", "Mpumalanga",
                "northern cape", "Northern Cape"
        );

        return provinceNames.getOrDefault(
                normalized,
                toTitleCase(value)
        );
    }

    /**
     * This function converts different values such as "yes",
     * "Y", "1", and "true" into a true or false value.
     */
    private static boolean normalizeBoolean(String value) {
        return value != null
                && Set.of(
                        "y",
                        "yes",
                        "1",
                        "true"
                ).contains(value.toLowerCase(Locale.ROOT));
    }

    /**
     * This function checks whether a value is empty or is a
     * placeholder such as "N/A", "TBD", "unknown", or "-".
     */
    private static boolean isPlaceholder(String value) {
        return value.isBlank()
                || Set.of(
                        "n/a",
                        "tbd",
                        "unknown",
                        "-",
                        "nan"
                ).contains(value.toLowerCase(Locale.ROOT));
    }

    /**
     * This function changes text into title case so that names
     * are displayed consistently, for example "CAPE TOWN HUB"
     * becomes "Cape Town Hub".
     */
    private static String toTitleCase(String value) {
        return List.of(
                value.toLowerCase(Locale.ROOT).split(" ")
        ).stream()
                .map(word -> word.isEmpty()
                        ? word
                        : Character.toUpperCase(word.charAt(0))
                                + word.substring(1))
                .collect(Collectors.joining(" "));
    }
}