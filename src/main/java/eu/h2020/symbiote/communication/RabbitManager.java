package eu.h2020.symbiote.communication;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.*;
import eu.h2020.symbiote.model.QueryRequest;
import eu.h2020.symbiote.model.Resource;
import eu.h2020.symbiote.model.ResourceUrlsRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

/**
 * Bean used to manage internal communication using RabbitMQ.
 * It is responsible for declaring exchanges and using routing keys from centralized config server.
 */
@Component
public class RabbitManager {

    @Value("${rabbit.host}")
    private String rabbitHost;

    @Value("${rabbit.username}")
    private String rabbitUsername;

    @Value("${rabbit.password}")
    private String rabbitPassword;

    @Value("${rabbit.exchange.resource.name}")
    private String resourceExchangeName;

    @Value("${rabbit.exchange.resource.type}")
    private String resourceExchangeType;

    @Value("${rabbit.exchange.resource.durable}")
    private boolean resourceExchangeDurable;

    @Value("${rabbit.exchange.resource.autodelete}")
    private boolean resourceExchangeAutodelete;

    @Value("${rabbit.exchange.resource.internal}")
    private boolean resourceExchangeInternal;

    @Value("${rabbit.exchange.cram.name}")
    private String cramExchangeName;

    @Value("${rabbit.exchange.cram.type}")
    private String cramExchangeType;

    @Value("${rabbit.exchange.cram.durable}")
    private boolean cramExchangeDurable;

    @Value("${rabbit.exchange.cram.autodelete}")
    private boolean cramExchangeAutodelete;

    @Value("${rabbit.exchange.cram.internal}")
    private boolean cramExchangeInternal;

    @Value("${rabbit.routingKey.resource.creationRequested}")
    private String resourceCreationRequestedRoutingKey;

    @Value("${rabbit.routingKey.resource.removalRequested}")
    private String resourceRemovalRequestedRoutingKey;

    @Value("${rabbit.routingKey.resource.modificationRequested}")
    private String resourceModificationRequestedRoutingKey;

    @Value("${rabbit.routingKey.cram.getResourceUrls}")
    private String getResourceUrlsRoutingKey;

    @Value("${rabbit.routingKey.resource.searchRequested}")
    private String resourceSearchRequestedRoutingKey;

    private Connection connection;
    private Channel channel;

    /**
     * Initialization method.
     */
    public void initCommunication() {
        try {
            ConnectionFactory factory = new ConnectionFactory();

            factory.setHost(this.rabbitHost);
            factory.setUsername(this.rabbitUsername);
            factory.setPassword(this.rabbitPassword);

            this.connection = factory.newConnection();

            this.channel = this.connection.createChannel();
            this.channel.exchangeDeclare(this.resourceExchangeName,
                    this.resourceExchangeType,
                    this.resourceExchangeDurable,
                    this.resourceExchangeAutodelete,
                    this.resourceExchangeInternal,
                    null);

            this.channel.exchangeDeclare(this.cramExchangeName,
                    this.cramExchangeType,
                    this.cramExchangeDurable,
                    this.cramExchangeAutodelete,
                    this.cramExchangeInternal,
                    null);

        } catch (IOException e) {
            e.printStackTrace();
        } catch (TimeoutException e) {
            e.printStackTrace();
        }
    }

    /**
     * Cleanup method
     */
    @PreDestroy
    private void cleanup() {
        try {
            if (this.channel != null && this.channel.isOpen())
                this.channel.close();
            if (this.connection != null && this.connection.isOpen())
                this.connection.close();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (TimeoutException e) {
            e.printStackTrace();
        }
    }

    private void sendMessage(String exchange, String routingKey, String message) {
        try {
            this.channel.basicPublish(exchange, routingKey, null, message.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String sendRpcMessage(String exchangeName, String routingKey, String message) {
        try {
            System.out.println("Sending message...");

            String replyQueueName = this.channel.queueDeclare().getQueue();

            String correlationId = UUID.randomUUID().toString();
            AMQP.BasicProperties props = new AMQP.BasicProperties()
                    .builder()
                    .correlationId(correlationId)
                    .replyTo(replyQueueName)
                    .contentType("application/json")
                    .build();

            QueueingConsumer consumer = new QueueingConsumer(channel);
            this.channel.basicConsume(replyQueueName, true, consumer);

            String responseMsg = null;

            this.channel.basicPublish(exchangeName, routingKey, props, message.getBytes());
            while (true){
                QueueingConsumer.Delivery delivery = consumer.nextDelivery(20000);
                if (delivery == null)
                    return null;

                if (delivery.getProperties().getCorrelationId().equals(correlationId)) {
                    responseMsg = new String(delivery.getBody());
                    break;
                }
            }

            return responseMsg;
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Method used to send RPC request to get specified resources URLs.
     *
     * @param request request object containing IDs of resources to get URLs
     * @return object containing mapping of IDs to respective URLs
     *
     */
    public Map<String, String> sendResourceUrlsRequest(ResourceUrlsRequest request) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            String message = mapper.writeValueAsString(request);
            System.out.println(message);
            String response = sendRpcMessage(this.cramExchangeName, this.getResourceUrlsRoutingKey, message);
            System.out.println("Response: " + response);
            if (response == null)
                return null;

            Map<String, String> responseObj = mapper.readValue(response, new TypeReference<Map<String, String>>(){});
            return responseObj;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<Resource> sendSearchRequest(QueryRequest request){
        try {
            ObjectMapper mapper = new ObjectMapper();
            String message = mapper.writeValueAsString(request);
            String response = sendRpcMessage(this.resourceExchangeName, this.resourceSearchRequestedRoutingKey, message);
            System.out.println("Response: " + response);
            if (response == null)
                return null;
            List<Resource> responseObj = mapper.readValue(response, new TypeReference<List<Resource>>() {});
            return responseObj;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
