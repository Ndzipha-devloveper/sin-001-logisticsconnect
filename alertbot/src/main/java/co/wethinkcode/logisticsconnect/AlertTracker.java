package co.wethinkcode.logisticsconnect;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers the last delay stage seen for each hub so that an alert is only
 * raised when a hub crosses the alert threshold, not on every message above it.
 */
public class AlertTracker {

    private final Map<String, Integer> lastStages = new ConcurrentHashMap<>();

    /**
     * Records the new stage for the hub and returns true only if the hub moved
     * from below the threshold to at or above it. A hub we haven't seen before
     * counts as stage 0, matching the other services.
     */
    public boolean shouldAlert(String hubId, int newStage) {
        String normalisedId = hubId.trim().toUpperCase();
        Integer previous = lastStages.put(normalisedId, newStage);
        int previousStage = previous == null ? 0 : previous;

        return !AlertBotApp.shouldSendAlert(previousStage)
                && AlertBotApp.shouldSendAlert(newStage);
    }
}