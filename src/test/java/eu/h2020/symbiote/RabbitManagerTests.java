package eu.h2020.symbiote;

import eu.h2020.symbiote.communication.RabbitManager;
import eu.h2020.symbiote.core.internal.ResourceUrlsRequest;
import eu.h2020.symbiote.model.QueryRequest;
import eu.h2020.symbiote.model.Resource;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class RabbitManagerTests {

    @Test
    public void testSendResourceUrls_timeout() {
        RabbitManager rabbitManager = spy(new RabbitManager());

        doReturn(null).when(rabbitManager).sendRpcMessage(any(), any(), any(), any());

        ResourceUrlsRequest request = new ResourceUrlsRequest();
        request.setIdList(Collections.singletonList("123"));

        Map<String, String> response = rabbitManager.sendResourceUrlsRequest(request);

        assertNull(response);
    }

    @Test
    public void testSendResourceUrls_emptyList() {
        RabbitManager rabbitManager = spy(new RabbitManager());

        doReturn("{}").when(rabbitManager).sendRpcMessage(any(), any(), any(), any());

        ResourceUrlsRequest request = new ResourceUrlsRequest();
        request.setIdList(Arrays.asList("123", "abc", "xyz"));

        Map<String, String> response = rabbitManager.sendResourceUrlsRequest(request);

        assertNotNull(response);
        assertTrue(response instanceof Map);
        assertEquals(0, response.size());
    }


    @Test
    public void testSendResourceUrls_3ids() {
        RabbitManager rabbitManager = spy(new RabbitManager());

        String jsonResponse = new String();
        jsonResponse += "{" +
                "\"123\":\"http://example.com/123\"," +
                "\"abc\":\"http://example.com/abc\"," +
                "\"xyz\":\"http://example.com/xyz\"" +
                "}";


        doReturn(jsonResponse).when(rabbitManager).sendRpcMessage(any(), any(), any(), any());

        ResourceUrlsRequest request = new ResourceUrlsRequest();
        request.setIdList(Arrays.asList("123", "abc", "xyz"));

        Map<String, String> response = rabbitManager.sendResourceUrlsRequest(request);

        assertNotNull(response);
        assertTrue(response instanceof Map);
        assertEquals(3, response.size());
        assertTrue(response.containsKey("123"));
        assertTrue(response.containsKey("abc"));
        assertTrue(response.containsKey("xyz"));
        assertEquals("http://example.com/123", response.get("123"));
        assertEquals("http://example.com/abc", response.get("abc"));
        assertEquals("http://example.com/xyz", response.get("xyz"));
    }

    @Test
    public void testSendSearchRequest_timeout() {
        RabbitManager rabbitManager = spy(new RabbitManager());

        doReturn(null).when(rabbitManager).sendRpcMessage(any(), any(), any(), any());

        QueryRequest request = new QueryRequest();
        List<Resource> response = rabbitManager.sendSearchRequest(request);

        assertNull(response);

    }

    @Test
    public void testSendSearchRequest_emptyResult() {
        RabbitManager rabbitManager = spy(new RabbitManager());

        doReturn("[]").when(rabbitManager).sendRpcMessage(any(), any(), any(), any());

        QueryRequest request = new QueryRequest();
        List<Resource> response = rabbitManager.sendSearchRequest(request);

        assertNotNull(response);
        assertTrue(response instanceof List);
        assertEquals(0, response.size());
    }

    @Test
    public void testSendSearchRequest_3results() {
        RabbitManager rabbitManager = spy(new RabbitManager());

        String jsonResponse = new String();
        jsonResponse += "[" +
                "{" +
                "\"name\" : \"res1\"" +
                "}," +
                "{" +
                "\"name\" : \"res2\"" +
                "}," +
                "{" +
                "\"name\" : \"res3\"" +
                "}" +
                "]";

        doReturn(jsonResponse).when(rabbitManager).sendRpcMessage(any(), any(), any(), any());

        QueryRequest request = new QueryRequest();
        List<Resource> response = rabbitManager.sendSearchRequest(request);

        assertNotNull(response);
        assertTrue(response instanceof List);
        assertEquals(3, response.size());
        assertEquals("res1", response.get(0).getName());
        assertEquals("res2", response.get(1).getName());
        assertEquals("res3", response.get(2).getName());
    }


}
