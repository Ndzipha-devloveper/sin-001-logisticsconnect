package co.wethinkcode.logisticsconnect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.wethinkcode.logisticsconnect.mq.JmsAlertSubscriber;
import co.wethinkcode.logisticsconnect.mq.MqConfig;
import io.javalin.Javalin;

public class AlertBotApp {

    private static final Logger logger = LoggerFactory.getLogger(AlertBotApp.class);
    private static final int ALERT_THRESHOLD = 5;

    public static boolean shouldSendAlert(int stage) {
        return stage >= ALERT_THRESHOLD;
    }

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7054);
        app.get("/health", ctx -> ctx.result("OK"));

        JmsAlertSubscriber subscriber = new JmsAlertSubscriber();
        subscriber.start();
        logger.info("AlertBot is running; subscription to {} will retry if needed", MqConfig.TOPIC);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            subscriber.close();
            app.stop();
        }, "alertbot-shutdown"));
    }
}
