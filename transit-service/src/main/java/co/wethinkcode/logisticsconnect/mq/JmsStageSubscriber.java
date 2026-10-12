package co.wethinkcode.logisticsconnect.mq;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Maintains the latest delay stage per hub from package-status-topic events. */
public class JmsStageSubscriber implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(JmsStageSubscriber.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_STAGE = 8;

    private final ConcurrentMap<String, Integer> stages = new ConcurrentHashMap<>();
    private final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "transit-topic-reconnect");
        thread.setDaemon(true);
        return thread;
    });
    private final ActiveMQConnectionFactory connectionFactory =
            new ActiveMQConnectionFactory(MqConfig.BROKER_URL);

    private Connection connection;
    private Session session;
    private MessageConsumer consumer;

    public ConcurrentMap<String, Integer> getStages() {
        return stages;
    }

    /** Starts a background connection/reconnection loop; HTTP startup is not broker-dependent. */
    public void start() {
        reconnectExecutor.scheduleWithFixedDelay(this::connectIfNeeded, 0, 5, TimeUnit.SECONDS);
    }

    private synchronized void connectIfNeeded() {
        if (connection != null) {
            return;
        }
        try {
            connection = connectionFactory.createConnection();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Topic topic = session.createTopic(MqConfig.TOPIC);
            consumer = session.createConsumer(topic);
            consumer.setMessageListener(this::receive);
            connection.setExceptionListener(exception -> {
                logger.warn("Transit topic connection failed; will retry", exception);
                synchronized (JmsStageSubscriber.this) {
                    closeJmsResources();
                }
            });
            connection.start();
            logger.info("Subscribed to ActiveMQ topic {}", MqConfig.TOPIC);
        } catch (JMSException exception) {
            closeJmsResources();
            logger.warn("ActiveMQ unavailable; transit will retry the topic subscription", exception);
        }
    }

    private void receive(Message message) {
        if (!(message instanceof TextMessage textMessage)) {
            logger.warn("Ignoring non-text message received on {}", MqConfig.TOPIC);
            return;
        }
        try {
            if (!applyStageEvent(textMessage.getText(), stages)) {
                logger.warn("Ignoring invalid stage event received on {}", MqConfig.TOPIC);
            }
        } catch (JMSException exception) {
            logger.warn("Could not read stage event from {}", MqConfig.TOPIC, exception);
        }
    }

    static boolean applyStageEvent(String payload, Map<String, Integer> targetStages) {
        try {
            JsonNode event = MAPPER.readTree(payload);
            JsonNode hubNode = event.get("hubId");
            JsonNode stageNode = event.get("stage");
            if (hubNode == null || !hubNode.isTextual() || stageNode == null || !stageNode.isInt()) {
                return false;
            }
            String hubId = hubNode.asText().trim().toUpperCase();
            int stage = stageNode.asInt();
            if (hubId.isEmpty() || stage < 0 || stage > MAX_STAGE) {
                return false;
            }
            targetStages.put(hubId, stage);
            return true;
        } catch (JsonProcessingException | RuntimeException exception) {
            return false;
        }
    }

    @Override
    public synchronized void close() {
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
            } catch (Exception ignored) {
                // Best-effort cleanup before reconnecting or shutting down.
            }
        }
    }
}