package eu.h2020.symbiote.communication;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.*;
import eu.h2020.symbiote.core.ci.QueryResponse;
import eu.h2020.symbiote.core.internal.CoreQueryRequest;
import eu.h2020.symbiote.core.internal.CoreSparqlQueryRequest;
import eu.h2020.symbiote.core.internal.ResourceUrlsRequest;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
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

    private static final String CORE_PARSE_ERROR_MSG = "Error while parsing response value from Core components";

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

    @Value("${rabbit.routingKey.resource.sparqlSearchRequested}")
    private String resourceSparqlSearchRequestedRoutingKey;

    private Connection connection;
    private Channel channel;
    private String durableResponseQueueName;

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

            this.durableResponseQueueName = this.channel.queueDeclare("symbIoTe-CoreInterface-rpcReplyQueue",true,true,false,null).getQueue();

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

            String correlationId = UUID.randomUUID().toString();

            Map<String, Object> headers = new HashMap<>();
            headers.put("__TypeId__", classType);
            headers.put("__ContentTypeId__", Object.class.getCanonicalName());

            AMQP.BasicProperties props = new AMQP.BasicProperties()
                    .builder()
                    .correlationId(correlationId)
                    .replyTo(durableResponseQueueName)
                    .contentType("application/json")
                    .headers(headers)
                    .build();

            final BlockingQueue<String> response = new ArrayBlockingQueue<String>(1);

            DefaultConsumer consumer = new DefaultConsumer(channel) {
                @Override
                public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                    if (properties.getCorrelationId().equals(correlationId)) {
                        log.debug("Got reply with correlationId: " + correlationId);
//                        responseMsg = new String(delivery.getBody());
                        response.offer(new String(body, "UTF-8"));
//                        getChannel().basicAck(envelope.getDeliveryTag(),false);
                        getChannel().basicCancel(this.getConsumerTag());

                    } else {
                        log.debug("Got answer with wrong correlationId... should be " + correlationId + " but got " + properties.getCorrelationId() );
                    }
                }
            };

//            QueueingConsumer consumer = new QueueingConsumer(channel);
            this.channel.basicConsume(durableResponseQueueName, true, consumer);



            this.channel.basicPublish(exchangeName, routingKey, props, message.getBytes());
//            while (true) {
//                QueueingConsumer.Delivery delivery = consumer.nextDelivery(20000);
//                if (delivery == null) {
//                    log.info("Timeout in response retrieval");
//                    return null;
//                }
//
//                if (delivery.getProperties().getCorrelationId().equals(correlationId)) {
//                    log.info("Wrong correlationID in response message");
//                    responseMsg = new String(delivery.getBody());
//                    break;
//                }
//            }

            String responseMsg = response.take();
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
     * Upon querying symbIoTe using {@link #sendSearchRequest(CoreQueryRequest)}, user gets resource without their Resource Access Proxy's URLs.
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

            return mapper.readValue(response, new TypeReference<Map<String, String>>() {
            });
        } catch (IOException e) {
            log.error(CORE_PARSE_ERROR_MSG, e);
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
    public QueryResponse sendSearchRequest(CoreQueryRequest request) {
        try {
            log.info("Request for resource query");
            ObjectMapper mapper = new ObjectMapper();
            String message = mapper.writeValueAsString(request);
            String response = sendRpcMessage(this.resourceExchangeName, this.resourceSearchRequestedRoutingKey, message, request.getClass().getCanonicalName());
            if (response == null)
                return null;
            return mapper.readValue(response, QueryResponse.class);
        } catch (IOException e) {
            log.error(CORE_PARSE_ERROR_MSG, e);
        }
        return null;
    }

    /**
     * Method used to send RPC request to query symbIoTe registry for resources using sparql.
     * <p>
     * Returned resources do not contain URLs to contact them.
     * Another call to {@link #sendResourceUrlsRequest(ResourceUrlsRequest)} is needed to obtain URLs.
     *
     * @param request request object describing sparql query parameters
     * @return response string of requested resources, or null when timeout occurs
     */
    public String sendSparqlSearchRequest(CoreSparqlQueryRequest request) {
        try {
            log.info("Request for resource sparql query");
            ObjectMapper mapper = new ObjectMapper();
            String message = mapper.writeValueAsString(request);
            String response = sendRpcMessage(this.resourceExchangeName, this.resourceSparqlSearchRequestedRoutingKey, message, request.getClass().getCanonicalName());
            if (response == null)
                return null;
            return mapper.readValue(response, String.class);
        } catch (IOException e) {
            log.error(CORE_PARSE_ERROR_MSG, e);
        }
        return null;
    }
}
