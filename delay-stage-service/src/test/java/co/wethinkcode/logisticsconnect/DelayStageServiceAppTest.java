package co.wethinkcode.logisticsconnect;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class DelayStageServiceAppTest {

    @Test
    void normaliseHubId_trimsAndUppercases() {
        assertEquals("H-501", DelayStageServiceApp.normaliseHubId("  h-501 "));
    }

    @Test
    void normaliseHubId_returnsEmptyForNullOrBlankIds() {
        assertEquals("", DelayStageServiceApp.normaliseHubId(null));
        assertEquals("", DelayStageServiceApp.normaliseHubId("   "));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8})
    void readDelayStageFromBody_acceptsStagesZeroThroughEight(int stage) {
        assertEquals(stage, DelayStageServiceApp.readDelayStageFromBody(
                "{\"delayStage\":" + stage + "}"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "not-json", "{}", "[]", "{\"stage\":3}", "{\"delayStage\":null}",
            "{\"delayStage\":\"3\"}", "{\"delayStage\":2.5}", "{\"delayStage\":true}",
            "{\"delayStage\":-1}", "{\"delayStage\":9}"
    })
    void readDelayStageFromBody_rejectsInvalidBodies(String body) {
        assertEquals(-1, DelayStageServiceApp.readDelayStageFromBody(body));
    }

    @Test
    void getDelayStage_defaultsToZeroAndStoresUpdatesByHub() {
        assertEquals(0, DelayStageServiceApp.getDelayStage("UNIT-NEW-HUB"));

        DelayStageServiceApp.updateDelayStage("UNIT-HUB-A", 4);
        DelayStageServiceApp.updateDelayStage("UNIT-HUB-B", 7);

        assertEquals(4, DelayStageServiceApp.getDelayStage("UNIT-HUB-A"));
        assertEquals(7, DelayStageServiceApp.getDelayStage("UNIT-HUB-B"));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 9})
    void updateDelayStage_rejectsStagesOutsideZeroThroughEight(int stage) {
        assertEquals(IllegalArgumentException.class,
            assertThrows(IllegalArgumentException.class,
                () -> DelayStageServiceApp.updateDelayStage("UNIT-INVALID", stage)).getClass());
    }

    @Test
    void getAllDelayStages_returnsAnImmutableSnapshot() {
        DelayStageServiceApp.updateDelayStage("UNIT-SNAPSHOT", 2);
        Map<String, Integer> snapshot = DelayStageServiceApp.getAllDelayStages();

        DelayStageServiceApp.updateDelayStage("UNIT-SNAPSHOT", 6);

        assertEquals(2, snapshot.get("UNIT-SNAPSHOT"));
        assertEquals(6, DelayStageServiceApp.getDelayStage("UNIT-SNAPSHOT"));
        assertEquals(UnsupportedOperationException.class,
            assertThrows(UnsupportedOperationException.class,
                () -> snapshot.put("UNIT-FAKE", 8)).getClass());
    }
}