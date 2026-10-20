package co.wethinkcode.logisticsconnect;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class AlertBotAppTest {

    @Test
    void shouldExposeMainMethod() throws NoSuchMethodException {
        Method mainMethod = AlertBotApp.class.getDeclaredMethod("main", String[].class);
        assertNotNull(mainMethod);
    }

    @Test
    void shouldSendAlertWhenStageReachesFive() {
        assertTrue(AlertBotApp.shouldSendAlert(5));
    }

    @Test
    void shouldNotSendAlertWhenStageIsBelowFive() {
        assertFalse(AlertBotApp.shouldSendAlert(3));
    }

    @Test
    void shouldNotSendAlertAtStageFour() {
        assertFalse(AlertBotApp.shouldSendAlert(4));
    }

    @Test
    void shouldSendAlertWhenStageIsAboveFive() {
        assertTrue(AlertBotApp.shouldSendAlert(7));
    }
}
