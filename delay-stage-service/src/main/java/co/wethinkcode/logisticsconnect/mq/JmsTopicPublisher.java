package co.wethinkcode.logisticsconnect.mq;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import org.apache.activemq.ActiveMQConnectionFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.wethinkcode.logisticsconnect.StageChangePublisher;

/** Publishes changed hub delay stages as JSON text messages to the shared topic. */
public class JmsTopicPublisher implements StageChangePublisher, AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ActiveMQConnectionFactory connectionFactory =
            new ActiveMQConnectionFactory(MqConfig.BROKER_URL);

    private Connection connection;
    private Session session;
    private MessageProducer producer;

    @Override
    public synchronized void publish(String hubId, int stage) throws JMSException {
        try {
            ensureConnected();
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("hubId", hubId);
            event.put("stage", stage);
            event.put("timestamp", Instant.now().toString());
            TextMessage message = session.createTextMessage(MAPPER.writeValueAsString(event));
            producer.send(message);
        } catch (JsonProcessingException exception) {
            JMSException jmsException = new JMSException("Could not serialize stage update");
            jmsException.setLinkedException(exception);
            throw jmsException;
        } catch (JMSException exception) {
            closeResources();
            throw exception;
        }
    }

    private void ensureConnected() throws JMSException {
        if (producer != null) {
            return;
        }

        connection = connectionFactory.createConnection();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Topic topic = session.createTopic(MqConfig.TOPIC);
        producer = session.createProducer(topic);
        connection.start();
    }

    @Override
    public synchronized void close() {
        closeResources();
    }

    private void closeResources() {
        closeQuietly(producer);
        closeQuietly(session);
        closeQuietly(connection);
        producer = null;
        session = null;
        connection = null;
    }

    private static void closeQuietly(AutoCloseable resource) {
        if (resource != null) {
            try {
                resource.close();
            } catch (Exception ignored) {
                // Closing is best-effort during shutdown or after broker failures.
            }
        }
    }
}