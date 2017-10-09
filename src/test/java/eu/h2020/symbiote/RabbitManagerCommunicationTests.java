package eu.h2020.symbiote;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import eu.h2020.symbiote.communication.RabbitManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Created by jawora on 12.07.17.
 */
public class RabbitManagerCommunicationTests {
    private RabbitManager rabbitManager;

    private Channel channel;

    private static final String QUEUE_RPC_NAME = "ci_rpc_test_queue";
    private static final boolean QUEUE_RPC_DURABLE = true;
    private static final boolean QUEUE_RPC_EXCLUSIVE = false;
    private static final boolean QUEUE_RPC_AUTODELETE = false;

    private static final String EXCHANGE_NAME = "ci_test_exchange";
    private static final String EXCHANGE_TYPE = "topic";
    private static final boolean EXCHANGE_DURABLE = true;
    private static final boolean EXCHANGE_AUTODELTE = false;
    private static final boolean EXCHANGE_INTERNAL = false;

    private static final String ROUTING_RPC_KEY = "ci_test_routing_key";

    private static final String RPC_MESSAGE = "ci_rpc_test_message";
    private static final String RPC_RESPONSE = "ci_rpc_test_response";

    @Before
    public void initCommunication() throws IOException, TimeoutException {
        this.rabbitManager = new RabbitManager();
        this.rabbitManager.setTestParameters("localhost", "guest", "guest", EXCHANGE_NAME, EXCHANGE_TYPE, EXCHANGE_DURABLE, EXCHANGE_AUTODELTE, EXCHANGE_INTERNAL);
        this.rabbitManager.initCommunication();

        this.channel = this.rabbitManager.getChannel();

        this.channel.queueDelete(QUEUE_RPC_NAME);
        this.channel.exchangeDelete(EXCHANGE_NAME);

        this.channel.queueDeclare(QUEUE_RPC_NAME, QUEUE_RPC_DURABLE, QUEUE_RPC_EXCLUSIVE, QUEUE_RPC_AUTODELETE, null);
        this.channel.exchangeDeclare(EXCHANGE_NAME, EXCHANGE_TYPE, EXCHANGE_DURABLE, EXCHANGE_AUTODELTE, EXCHANGE_INTERNAL, null);

        this.channel.queueBind(QUEUE_RPC_NAME, EXCHANGE_NAME, ROUTING_RPC_KEY);
    }

    @Test
    public void sendRpcMessageTimeoutTest() throws IOException {
        this.channel.basicConsume(QUEUE_RPC_NAME, new DefaultConsumer(this.channel){
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                //DO NOTHING
            }
        });

        String response = this.rabbitManager.sendRpcMessage(EXCHANGE_NAME, ROUTING_RPC_KEY, RPC_MESSAGE, String.class.getCanonicalName());

        assertNull(response);
    }

    @Test
    public void sendRpcMessageOkTest() throws IOException {
        this.channel.basicConsume(QUEUE_RPC_NAME, new DefaultConsumer(this.channel){
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                String message = new String(body);
                assertEquals(RPC_MESSAGE, message);

                assertNotNull(properties);

                String correlationId = properties.getCorrelationId();
                String replyQueueName = properties.getReplyTo();

                assertNotNull(correlationId);
                assertNotNull(replyQueueName);

                AMQP.BasicProperties props = new AMQP.BasicProperties()
                        .builder()
                        .correlationId(correlationId)
                        .replyTo(replyQueueName)
                        .contentType("application/json")
                        .build();

                this.getChannel().basicPublish("", replyQueueName, props, RPC_RESPONSE.getBytes());
            }
        });

        String response = this.rabbitManager.sendRpcMessage(EXCHANGE_NAME, ROUTING_RPC_KEY, RPC_MESSAGE, String.class.getCanonicalName());

        assertEquals(RPC_RESPONSE, response);
    }

    @Test
    public void sendRpcMessageWrongCorrelationIdTest() throws IOException {
        this.channel.basicConsume(QUEUE_RPC_NAME, new DefaultConsumer(this.channel){
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                String message = new String(body);
                assertEquals(RPC_MESSAGE, message);

                assertNotNull(properties);

                String correlationId = "wrong_correlation_ID";
                String replyQueueName = properties.getReplyTo();

                assertNotNull(correlationId);
                assertNotNull(replyQueueName);

                AMQP.BasicProperties props = new AMQP.BasicProperties()
                        .builder()
                        .correlationId(correlationId)
                        .replyTo(replyQueueName)
                        .contentType("application/json")
                        .build();

                this.getChannel().basicPublish("", replyQueueName, props, RPC_RESPONSE.getBytes());
            }
        });

        String response = this.rabbitManager.sendRpcMessage(EXCHANGE_NAME, ROUTING_RPC_KEY, RPC_MESSAGE, String.class.getCanonicalName());

        assertNull(response);
    }

    @After
    public void cleanup() throws IOException {
        if (this.rabbitManager != null) {
            this.channel.queueUnbind(QUEUE_RPC_NAME, EXCHANGE_NAME, ROUTING_RPC_KEY);
            this.channel.queueDelete(QUEUE_RPC_NAME);
            this.channel.exchangeDelete(EXCHANGE_NAME);

            this.rabbitManager.cleanup();
        }
    }
}
