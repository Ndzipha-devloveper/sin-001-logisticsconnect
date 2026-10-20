package co.wethinkcode.logisticsconnect.mq;
import co.wethinkcode.logisticsconnect.AlertTracker;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


public class JmsAlertSubscriber implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(JmsAlertSubscriber.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_STAGE = 8;
    private final AlertTracker tracker = new AlertTracker();

    private final ActiveMQConnectionFactory connectionFactory =
            new ActiveMQConnectionFactory(MqConfig.BROKER_URL);
    private final ScheduledExecutorService reconnectExecutor =
            Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "alertbot-topic-reconnect");
                thread.setDaemon(true);
                return thread;
            });

    private Connection connection;
    private Session session;
    private MessageConsumer consumer;
    private boolean started;
    private boolean closed;

    /** Starts the asynchronous subscription/reconnection loop. */
    public synchronized void start() {
        if (started || closed) {
            return;
        }
        started = true;
        reconnectExecutor.scheduleWithFixedDelay(this::connectIfNeeded, 0, 5, TimeUnit.SECONDS);
    }

    private synchronized void connectIfNeeded() {
        if (closed || connection != null) {
            return;
        }
        try {
            connection = connectionFactory.createConnection();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            Topic topic = session.createTopic(MqConfig.TOPIC);
            consumer = session.createConsumer(topic);
            consumer.setMessageListener(this::handleMessage);
            connection.setExceptionListener(this::handleConnectionFailure);

            connection.start();
            logger.info("AlertBot subscribed to {}", MqConfig.TOPIC);
        } catch (JMSException exception) {
            closeJmsResources();
            logger.warn("ActiveMQ unavailable; AlertBot will retry the topic subscription", exception);
        }
    }

    private synchronized void handleConnectionFailure(JMSException exception) {
        logger.warn("AlertBot topic connection failed; will retry", exception);
        closeJmsResources();
    }

    private void handleMessage(Message message) {
        if (!(message instanceof TextMessage textMessage)) {
            logger.warn("Ignoring non-text message on {}", MqConfig.TOPIC);
            return;
        }

        try {
            JsonNode event = MAPPER.readTree(textMessage.getText());
            if (!isValidEvent(event)) {
                logger.warn("Ignoring invalid stage event on {}", MqConfig.TOPIC);
                return;
            }

            String hubId = event.get("hubId").asText().trim();
            int stage = event.get("stage").asInt();

            if (tracker.shouldAlert(hubId,stage)) {
                logger.info("SIMULATED DELAY ALERT: hub {} is at stage {}", hubId, stage);
            }
        } catch (JMSException | IOException exception) {
            logger.warn("Could not process stage event", exception);
        }
    }

    private static boolean isValidEvent(JsonNode event) {
        if (event == null || !event.isObject()) {
            return false;
        }
        if (!hasValidHubId(event)) {
            return false;
        }
        return hasValidStage(event);
    }

    private static boolean hasValidHubId(JsonNode event) {
        JsonNode hubId = event.get("hubId");
        if (hubId == null || !hubId.isTextual()) {
            return false;
        }
        return !hubId.asText().isBlank();
    }

    private static boolean hasValidStage(JsonNode event) {
        JsonNode stageNode = event.get("stage");
        if (stageNode == null || !stageNode.isInt()) {
            return false;
        }
        int stage = stageNode.asInt();
        if (stage < 0) {
            return false;
        }
        return stage <= MAX_STAGE;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        reconnectExecutor.shutdownNow();
        closeJmsResources();
    }

    private void closeJmsResources() {
        closeQuietly(consumer);
        closeQuietly(session);
        closeQuietly(connection);

        consumer = null;
        session = null;
        connection = null;
    }

    private static void closeQuietly(AutoCloseable resource) {
        if (resource != null) {
            try {
                resource.close();
            } catch (Exception exception) {
                logger.warn("Error while closing JMS resource", exception);
            }
        }
    }
}