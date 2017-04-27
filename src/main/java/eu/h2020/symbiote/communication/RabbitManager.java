package eu.h2020.symbiote.communication;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.*;
import eu.h2020.symbiote.core.internal.ResourceUrlsRequest;
import eu.h2020.symbiote.model.QueryRequest;
import eu.h2020.symbiote.model.Resource;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

/**
 * Class used for all internal communication using RabbitMQ AMQP implementation.
 * It works as a Spring Bean, and should be used via autowiring.
 * <p>
 * RabbitManager uses properties taken from CoreConfigServer to set up communication (exchange parameters, routing keys etc.)
 */
@Component
public class RabbitManager {
    private static Log log = LogFactory.getLog(RabbitManager.class);

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
     * Method used to initialise RabbitMQ connection and declare all required exchanges.
     * This method should be called once, after bean initialization (so that properties from CoreConfigServer are obtained),
     * but before using RabbitManager to send any message.
     */
    public void initCommunication() {
        log.info("RabbitMQ communication init");
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

        } catch (IOException | TimeoutException e) {
            log.error(e.getMessage(), e);
        }
    }

    /**
     * Cleanup method, used to close RabbitMQ channel and connection.
     */
    @PreDestroy
    private void cleanup() {
        log.info("Closing RabbitMQ channel and connection");
        try {
            if (this.channel != null && this.channel.isOpen())
                this.channel.close();
            if (this.connection != null && this.connection.isOpen())
                this.connection.close();
        } catch (IOException | TimeoutException e) {
            log.error(e.getMessage(), e);
        }
    }

    /**
     * Method used to send message via RPC (Remote Procedure Call) pattern.
     * In this implementation it covers asynchronous Rabbit communication with synchronous one, as it is used by conventional REST facade.
     * Before sending a message, a temporary response queue is declared and its name is passed along with the message.
     * When a consumer handles the message, it returns the result via the response queue.
     * Since this is a synchronous pattern, it uses timeout of 20 seconds. If the response doesn't come in that time, the method returns with null result.
     *
     * @param exchangeName name of the eschange to send message to
     * @param routingKey   routing key to send message to
     * @param message      message to be sent
     * @return response from the consumer or null if timeout occurs
     */
    public String sendRpcMessage(String exchangeName, String routingKey, String message, String classType) {
        try {
            log.info("Sending RPC message: " + message);

            String replyQueueName = this.channel.queueDeclare().getQueue();

            String correlationId = UUID.randomUUID().toString();

            Map<String, Object> headers = new HashMap<>();
            headers.put("__TypeId__", classType);
            headers.put("__ContentTypeId__", Object.class.getCanonicalName());

            AMQP.BasicProperties props = new AMQP.BasicProperties()
                    .builder()
                    .correlationId(correlationId)
                    .replyTo(replyQueueName)
                    .contentType("application/json")
                    .headers(headers)
                    .build();

            QueueingConsumer consumer = new QueueingConsumer(channel);
            this.channel.basicConsume(replyQueueName, true, consumer);

            String responseMsg = null;

            this.channel.basicPublish(exchangeName, routingKey, props, message.getBytes());
            while (true) {
                QueueingConsumer.Delivery delivery = consumer.nextDelivery(20000);
                if (delivery == null) {
                    log.info("Timeout in response retrieval");
                    return null;
                }

                if (delivery.getProperties().getCorrelationId().equals(correlationId)) {
                    log.info("Wrong correlationID in response message");
                    responseMsg = new String(delivery.getBody());
                    break;
                }
            }

            log.info("Response received: " + responseMsg);
            return responseMsg;
        } catch (IOException | InterruptedException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    /**
     * Method used to send RPC request to get specified resources URLs.
     * <p>
     * Upon querying symbIoTe using {@link #sendSearchRequest(QueryRequest)}, user gets resource without their Resource Access Proxy's URLs.
     * It is necessary to obtain them from core Resource Access Monitor by supplying list of chosen resource IDs.
     *
     * @param request request object containing IDs of resources to get URLs
     * @return response map in form of {"id1":"URL1", "id2":"URL2", ... }, or null when timeout occurs
     */
    public Map<String, String> sendResourceUrlsRequest(ResourceUrlsRequest request) {
        try {
            log.info("Request for resource URLs");
            ObjectMapper mapper = new ObjectMapper();
            String message = mapper.writeValueAsString(request);
            log.debug(message);
            String response = sendRpcMessage(this.cramExchangeName, this.getResourceUrlsRoutingKey, message, request.getClass().getCanonicalName());
            if (response == null)
                return null;

            Map<String, String> responseObj = mapper.readValue(response, new TypeReference<Map<String, String>>() {
            });
            return responseObj;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Method used to send RPC request to query symbIoTe registry for resources.
     * <p>
     * Returned resources do not contain URLs to contact them.
     * Another call to {@link #sendResourceUrlsRequest(ResourceUrlsRequest)} is needed to obtain URLs.
     *
     * @param request request object describing query parameters
     * @return response list of requested resources, or null when timeout occurs
     */
    public List<Resource> sendSearchRequest(QueryRequest request) {
        try {
            log.info("Request for resource query");
            ObjectMapper mapper = new ObjectMapper();
            String message = mapper.writeValueAsString(request);
            String response = sendRpcMessage(this.resourceExchangeName, this.resourceSearchRequestedRoutingKey, message, request.getClass().getCanonicalName());
            if (response == null)
                return null;
            List<Resource> responseObj = mapper.readValue(response, new TypeReference<List<Resource>>() {
            });
            return responseObj;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
