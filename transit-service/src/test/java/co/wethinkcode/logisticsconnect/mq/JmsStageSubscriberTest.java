package co.wethinkcode.logisticsconnect.mq;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class JmsStageSubscriberTest {

    @Test
    void applyStageEventNormalizesHubIdAndUpdatesLatestStage() {
        Map<String, Integer> stages = new ConcurrentHashMap<>();

        assertTrue(JmsStageSubscriber.applyStageEvent(
                "{\"hubId\":\" h-501 \",\"stage\":4,\"timestamp\":\"2026-10-12T00:00:00Z\"}",
                stages));

        assertEquals(4, stages.get("H-501"));
    }

    @Test
    void applyStageEventRejectsMalformedOrOutOfRangeMessages() {
        Map<String, Integer> stages = new ConcurrentHashMap<>();

        assertFalse(JmsStageSubscriber.applyStageEvent("not-json", stages));
        assertFalse(JmsStageSubscriber.applyStageEvent("{\"hubId\":\"H-501\",\"stage\":9}", stages));
        assertFalse(JmsStageSubscriber.applyStageEvent("{\"hubId\":\"H-501\",\"stage\":\"3\"}", stages));
        assertTrue(stages.isEmpty());
    }
}