package co.wethinkcode.logisticsconnect;

import javax.jms.JMSException;

@FunctionalInterface
public interface StageChangePublisher {

    void publish(String hubId, int stage) throws JMSException;
}