package eu.h2020.symbiote;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.h2020.symbiote.communication.RabbitManager;
import eu.h2020.symbiote.core.ci.QueryResponse;
import eu.h2020.symbiote.core.ci.SparqlQueryResponse;
import eu.h2020.symbiote.core.internal.CoreQueryRequest;
import eu.h2020.symbiote.core.internal.CoreSparqlQueryRequest;
import eu.h2020.symbiote.core.internal.cram.ResourceUrlsRequest;
import eu.h2020.symbiote.core.internal.cram.ResourceUrlsResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class RabbitManagerTests {

    @Test
    public void testSendResourceUrls_timeout() {
        RabbitManager rabbitManager = spy(new RabbitManager());

        doReturn(null).when(rabbitManager).sendRpcMessage(any(), any(), any(), any());

        ResourceUrlsRequest request = new ResourceUrlsRequest();
        request.setBody(Collections.singletonList("123"));

        ResourceUrlsResponse response = rabbitManager.sendResourceUrlsRequest(request);

        assertNull(response);
    }

    @Test
    public void testSendResourceUrls_emptyList() {
        RabbitManager rabbitManager = spy(new RabbitManager());

        doReturn("{\"body\":{}}").when(rabbitManager).sendRpcMessage(any(), any(), any(), any());

        ResourceUrlsRequest request = new ResourceUrlsRequest();
        request.setBody(Arrays.asList("123", "abc", "xyz"));

        ResourceUrlsResponse response = rabbitManager.sendResourceUrlsRequest(request);

        assertNotNull(response);
        assertTrue(response instanceof ResourceUrlsResponse);
        assertEquals(0, response.getBody().size());
    }


    @Test
    public void testSendResourceUrls_3ids() {
        RabbitManager rabbitManager = spy(new RabbitManager());

        String jsonResponse = new String();
        jsonResponse += "{\"body\":{" +
                "\"123\":\"http://example.com/123\"," +
                "\"abc\":\"http://example.com/abc\"," +
                "\"xyz\":\"http://example.com/xyz\"" +
                "}}";


        doReturn(jsonResponse).when(rabbitManager).sendRpcMessage(any(), any(), any(), any());

        ResourceUrlsRequest request = new ResourceUrlsRequest();
        request.setBody(Arrays.asList("123", "abc", "xyz"));

        ResourceUrlsResponse response = rabbitManager.sendResourceUrlsRequest(request);

        assertNotNull(response);
        assertTrue(response instanceof ResourceUrlsResponse);
        assertEquals(3, response.getBody().size());
        assertTrue(response.getBody().containsKey("123"));
        assertTrue(response.getBody().containsKey("abc"));
        assertTrue(response.getBody().containsKey("xyz"));
        assertEquals("http://example.com/123", response.getBody().get("123"));
        assertEquals("http://example.com/abc", response.getBody().get("abc"));
        assertEquals("http://example.com/xyz", response.getBody().get("xyz"));
    }

    @Test
    public void testSendSearchRequest_timeout() {
        RabbitManager rabbitManager = spy(new RabbitManager());

        doReturn(null).when(rabbitManager).sendRpcMessage(any(), any(), any(), any());

        CoreQueryRequest request = new CoreQueryRequest();
        QueryResponse response = rabbitManager.sendSearchRequest(request);

        assertNull(response);

    }

    @Test
    public void testSendSearchRequest_emptyResult() {
        RabbitManager rabbitManager = spy(new RabbitManager());

        doReturn("{\"body\":[]}").when(rabbitManager).sendRpcMessage(any(), any(), any(), any());

        CoreQueryRequest request = new CoreQueryRequest();
        QueryResponse response = rabbitManager.sendSearchRequest(request);

        assertNotNull(response);
        assertEquals(0, response.getBody().size());
    }

    @Test
    public void testSendSearchRequest_3results() {
        RabbitManager rabbitManager = spy(new RabbitManager());

        String jsonResponse = new String();
        jsonResponse += "{" +
                "\"body\":[" +
                "{" +
                "\"name\" : \"res1\"" +
                "}," +
                "{" +
                "\"name\" : \"res2\"" +
                "}," +
                "{" +
                "\"name\" : \"res3\"" +
                "}" +
                "]" +
                "}";

        doReturn(jsonResponse).when(rabbitManager).sendRpcMessage(any(), any(), any(), any());

        CoreQueryRequest request = new CoreQueryRequest();
        QueryResponse response = rabbitManager.sendSearchRequest(request);

        assertNotNull(response);
        assertEquals(3, response.getBody().size());
        assertEquals("res1", response.getBody().get(0).getName());
        assertEquals("res2", response.getBody().get(1).getName());
        assertEquals("res3", response.getBody().get(2).getName());
    }

    @Test
    public void testSendSparqlSearchRequest_timeout() {
        RabbitManager rabbitManager = spy(new RabbitManager());

        doReturn(null).when(rabbitManager).sendRpcMessage(any(), any(), any(), any());

        CoreSparqlQueryRequest request = new CoreSparqlQueryRequest();
        SparqlQueryResponse response = rabbitManager.sendSparqlSearchRequest(request);

        assertNull(response);

    }

    @Test
    public void testSendSparqlSearchRequest_emptyResult() throws JsonProcessingException {
        RabbitManager rabbitManager = spy(new RabbitManager());

        SparqlQueryResponse rdfResponse = new SparqlQueryResponse(200,"OK","");
        ObjectMapper mapper = new ObjectMapper();
        doReturn(mapper.writeValueAsString(rdfResponse)).when(rabbitManager).sendRpcMessage(any(), any(), any(), any());

        CoreSparqlQueryRequest request = new CoreSparqlQueryRequest();
        SparqlQueryResponse response = rabbitManager.sendSparqlSearchRequest(request);

        assertNotNull(response);
        assertEquals(0, response.getBody().length());
    }

    @Test
    public void testSendSparqlSearchRequest_3results() throws JsonProcessingException {
        RabbitManager rabbitManager = spy(new RabbitManager());

        SparqlQueryResponse rdfResponse = new SparqlQueryResponse(200,"OK","RDF resources");
        ObjectMapper mapper = new ObjectMapper();


        doReturn(mapper.writeValueAsString(rdfResponse)).when(rabbitManager).sendRpcMessage(any(), any(), any(), any());

        CoreSparqlQueryRequest request = new CoreSparqlQueryRequest();
        SparqlQueryResponse response = rabbitManager.sendSparqlSearchRequest(request);

        assertNotNull(response);
        assertNotNull(response.getBody());
        assertEquals(13, response.getBody().length());
    }


}
