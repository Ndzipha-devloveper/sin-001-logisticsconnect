package co.wethinkcode.logisticsconnect;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.jms.JMSException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.wethinkcode.logisticsconnect.mq.JmsTopicPublisher;
import io.javalin.Javalin;

public class DelayStageServiceApp {

    private static final Logger logger = LoggerFactory.getLogger(DelayStageServiceApp.class);

    static final int MAX_DELAY_STAGE = 8;
    static final int MIN_DELAY_STAGE = 0;

    private static final Map<String, Integer> delayStages = new ConcurrentHashMap<>();
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Object stageLock = new Object();

    public static void main(String[] args) {
        JmsTopicPublisher publisher = new JmsTopicPublisher();
        Javalin app = createApp(publisher).start(7052);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            app.stop();
            publisher.close();
        }, "delay-stage-shutdown"));
    }

    static Javalin createApp() {
        return createApp((hubId, stage) -> {
        });
    }

    static Javalin createApp(StageChangePublisher publisher) {
        Javalin app = Javalin.create();

        app.get("/health", ctx -> ctx.result("OK"));

        // GET /delay-stage/H-501 -> { "hubId": "H-501", "delayStage": 3 }
        app.get("/delay-stage/{hubId}", ctx -> {
            String hubId = normaliseHubId(ctx.pathParam("hubId"));
            ctx.json(Map.of("hubId", hubId, "delayStage", getDelayStage(hubId)));
        });

        // POST /delay-stage/H-501 with body { "delayStage": 3 }
        app.post("/delay-stage/{hubId}", ctx -> {
            String hubId = normaliseHubId(ctx.pathParam("hubId"));
            int stage = readDelayStageFromBody(ctx.body());

            if (stage == -1) {
                ctx.status(400).json(Map.of("error", "Invalid delay stage. Must be a whole number from 0 to 8."));
                return;
            }

            boolean stageChanged;
            synchronized (stageLock) {
                stageChanged = (getDelayStage(hubId) != stage);
                if (stageChanged) {
                    try {
                        publisher.publish(hubId, stage);
                    } catch (JMSException exception) {
                        logger.warn("Could not publish stage update for hub {} to ActiveMQ", hubId, exception);
                        ctx.status(503).json(Map.of("error", "Stage update could not be published"));
                        return;
                    }
                    updateDelayStage(hubId, stage);
                }
            }

            ctx.json(Map.of("hubId", hubId, "delayStage", stage, "stageChanged", stageChanged));
        });

        return app;
    }

    // Trims and uppercases the hubId so "h-501 " and "H-501" are treated the same.
    static String normaliseHubId(String hubId) {
        return hubId == null ? "" : hubId.trim().toUpperCase();
    }

    /*
     * Reads the stage from a body like {"delayStage": 3} and returns it as an int.
     * Returns -1 if the body is missing, not JSON, not a whole number, or outside 0-8.
     */
    static int readDelayStageFromBody(String body) {
        try {
            JsonNode stageNode = mapper.readTree(body).get("delayStage");
            if (stageNode == null || !stageNode.isInt()) {
                return -1;
            }
            int stage = stageNode.asInt();
            if (stage < MIN_DELAY_STAGE || stage > MAX_DELAY_STAGE) {
                return -1;
            }
            return stage;
        } catch (JsonProcessingException | RuntimeException e) {
            return -1;
        }
    }

    /*
     * Updates the delay stage for a given hub.
     * Ensures that the delay stage is within the valid range (0-8).
     */
    public static void updateDelayStage(String hubId, int newStage) {
        if (newStage < MIN_DELAY_STAGE || newStage > MAX_DELAY_STAGE) {
            throw new IllegalArgumentException("Delay stage must be between 0 and 8.");
        }
        delayStages.put(hubId, newStage);
    }

    // Current delay stage for a given hub. Returns 0 if not set.
    public static int getDelayStage(String hubId) {
        return delayStages.getOrDefault(hubId, 0);
    }

    // A read-only snapshot, so callers can't change the real map.
    public static Map<String, Integer> getAllDelayStages() {
        return Map.copyOf(delayStages);
    }

    // MQ TODO: publishes to ActiveMQ topic MqConfig.TOPIC at MqConfig.BROKER_URL (see co.wethinkcode.logisticsconnect.mq.MqConfig)
}