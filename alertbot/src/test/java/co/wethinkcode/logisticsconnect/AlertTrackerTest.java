package co.wethinkcode.logisticsconnect;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AlertTrackerTest {

    @Test
    void alertsWhenHubCrossesThreshold() {
        AlertTracker tracker = new AlertTracker();
        assertFalse(tracker.shouldAlert("H-500", 3));
        assertTrue(tracker.shouldAlert("H-500", 5));
    }

    @Test
    void doesNotAlertAgainWhileHubStaysAboveThreshold() {
        AlertTracker tracker = new AlertTracker();
        assertTrue(tracker.shouldAlert("H-500", 5));
        assertFalse(tracker.shouldAlert("H-500", 6));
        assertFalse(tracker.shouldAlert("H-500", 7));
    }

    @Test
    void alertsAgainAfterHubDropsBelowAndCrossesAgain() {
        AlertTracker tracker = new AlertTracker();
        assertTrue(tracker.shouldAlert("H-500", 6));
        assertFalse(tracker.shouldAlert("H-500", 2));
        assertTrue(tracker.shouldAlert("H-500", 5));
    }

    @Test
    void doesNotAlertBelowThreshold() {
        AlertTracker tracker = new AlertTracker();
        assertFalse(tracker.shouldAlert("H-500", 4));
    }

    @Test
    void tracksHubsIndependently() {
        AlertTracker tracker = new AlertTracker();
        assertTrue(tracker.shouldAlert("H-500", 6));
        assertTrue(tracker.shouldAlert("H-501", 6));
    }

    @Test
    void treatsHubIdCaseAndPaddingAsTheSameHub() {
        AlertTracker tracker = new AlertTracker();
        assertTrue(tracker.shouldAlert("h-500 ", 6));
        assertFalse(tracker.shouldAlert("H-500", 7));
    }
}