package com.arrow.acn.client;

import com.arrow.acn.MqttConstants;
import com.rabbitmq.client.ConnectionFactory;
import org.junit.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class RabbitLoadTestConsumer extends RabbitLoadTest {

    ArrayList<Integer> telemetryHistory = new ArrayList<Integer>();
    AtomicInteger currentReceivedCount = new AtomicInteger(0);
    Integer iterations = 0;
    Integer settlingPeriodMin = 10;  // Allow consumer to consume backed up messages for this number of minutes.
    long averageReceiveRate = 0;

    public class AMQPConsumer implements MessageListener {

        Timer timer = new Timer();
        // Task to calculate average receive rate every minute
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                String method = "run";
                Date now = new Date();
                Integer currentCount = currentReceivedCount.get();
                if(iterations == settlingPeriodMin) {
                    logInfo(method,"Clearing values and starting test.");
                    telemetryHistory.clear();
                    currentReceivedCount.set(0);
                } else if (currentCount > 0) {
                    telemetryHistory.add(currentCount);
                    Integer total = 0;
                    for (Integer i : telemetryHistory) {
                        total  += i;
                    }
                    averageReceiveRate = total / telemetryHistory.size();
                    logInfo(method,"Average Receive Rate: " + averageReceiveRate);
                    currentReceivedCount.set(0);
                }
                iterations++;
            }
        };

        public AMQPConsumer(){
            timer.schedule(task,0L,60000L); // run task every minute
        }

        @Override
        public void onMessage(Message message) {
            currentReceivedCount.incrementAndGet();
        }

    }

    @Test
    public void startConsumerTest() {

        String method = "startConsumerTest";
        logInfo(method, "Starting test...");

        // Consider Adding the following to RabbitLoadTest.json
        String amqpUrl = config.mqttUrl.replace("ssl://","").replace(":8883","");
        Integer amqpPort = 5671;
        String virtualHost = "/pegasus";
        // End Consider Adding the following to RabbitLoadTest.json

        ConnectionFactory factory = new ConnectionFactory();
        try {
            factory.useSslProtocol();
        } catch (Exception e) {
            logError(method,"Failed to set connection to use SSL: " + e);
        }
        factory.setUsername(config.appHid);
        factory.setPassword(config.appApiKey);
        factory.setVirtualHost(virtualHost);
        factory.setHost(amqpUrl);
        factory.setPort(amqpPort);

        CachingConnectionFactory cachingConnectionFactory = new CachingConnectionFactory(factory);

        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(cachingConnectionFactory);
        container.addQueueNames(MqttConstants.applicationTelemetryRouting(config.appHid));
        container.setMessageListener(new AMQPConsumer());
        container.setConcurrentConsumers(2);
        container.setPrefetchCount(20);
        container.afterPropertiesSet();
        container.start();

        while(true) {
            // testing
        }

    }
}
