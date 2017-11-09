package eu.h2020.symbiote;

import eu.h2020.symbiote.communication.RabbitManager;
import eu.h2020.symbiote.controllers.CoreInterfaceController;
import eu.h2020.symbiote.core.ci.QueryResourceResult;
import eu.h2020.symbiote.core.ci.QueryResponse;
import eu.h2020.symbiote.core.ci.SparqlQueryRequest;
import eu.h2020.symbiote.core.ci.SparqlQueryResponse;
import eu.h2020.symbiote.core.internal.CoreQueryRequest;
import eu.h2020.symbiote.core.internal.CoreSparqlQueryRequest;
import eu.h2020.symbiote.core.internal.cram.ResourceUrlsRequest;
import eu.h2020.symbiote.core.internal.cram.ResourceUrlsResponse;
import eu.h2020.symbiote.security.commons.Certificate;
import eu.h2020.symbiote.security.commons.SecurityConstants;
import eu.h2020.symbiote.security.commons.enums.ValidationStatus;
import eu.h2020.symbiote.security.communication.payloads.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CoreInterfaceControllerTests {

    @Test
    public void testQuery_noSecurityHeaders() {
        RabbitManager rabbitManager = Mockito.mock(RabbitManager.class);
        when(rabbitManager.sendSearchRequest((CoreQueryRequest) notNull())).thenReturn(null);

        CoreInterfaceController controller = new CoreInterfaceController(rabbitManager);

        ResponseEntity response = controller.query(null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    public void testQuery_timeout() {
        RabbitManager rabbitManager = Mockito.mock(RabbitManager.class);
        when(rabbitManager.sendSearchRequest((CoreQueryRequest) notNull())).thenReturn(null);

        HttpHeaders headers = new HttpHeaders();
        headers.add(SecurityConstants.SECURITY_CREDENTIALS_TIMESTAMP_HEADER, "1500000000");
        headers.add(SecurityConstants.SECURITY_CREDENTIALS_SIZE_HEADER, "1");
        headers.add(SecurityConstants.SECURITY_CREDENTIALS_HEADER_PREFIX + "1", "{\"token\":\"token\"," +
                "\"authenticationChallenge\":\"authenticationChallenge\"," +
                "\"clientCertificate\":\"clientCertificate\"," +
                "\"clientCertificateSigningAAMCertificate\":\"clientCertificateSigningAAMCertificate\"," +
                "\"foreignTokenIssuingAAMCertificate\":\"foreignTokenIssuingAAMCertificate\"}");

        CoreInterfaceController controller = new CoreInterfaceController(rabbitManager);

        ResponseEntity response = controller.query(null, null, null, null, null, null, null, null, null, null, null, null, null, headers);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    public void testQuery_emptyResults() {
        RabbitManager rabbitManager = Mockito.mock(RabbitManager.class);
        when(rabbitManager.sendSearchRequest((CoreQueryRequest) notNull())).thenReturn(new QueryResponse(200,"",new ArrayList<QueryResourceResult>()));

        HttpHeaders headers = new HttpHeaders();
        headers.add(SecurityConstants.SECURITY_CREDENTIALS_TIMESTAMP_HEADER, "1500000000");
        headers.add(SecurityConstants.SECURITY_CREDENTIALS_SIZE_HEADER, "1");
        headers.add(SecurityConstants.SECURITY_CREDENTIALS_HEADER_PREFIX + "1", "{\"token\":\"token\"," +
                "\"authenticationChallenge\":\"authenticationChallenge\"," +
                "\"clientCertificate\":\"clientCertificate\"," +
                "\"clientCertificateSigningAAMCertificate\":\"clientCertificateSigningAAMCertificate\"," +
                "\"foreignTokenIssuingAAMCertificate\":\"foreignTokenIssuingAAMCertificate\"}");

        CoreInterfaceController controller = new CoreInterfaceController(rabbitManager);

        ResponseEntity response = controller.query(null, null, null, null, null, null, null, null, null, null, new String[]{"property1"}, null, null, headers);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof QueryResponse);
        assertEquals(0, ((QueryResponse) response.getBody()).getBody().size());
    }

    @Test
    public void testQuery_3results() {
        RabbitManager rabbitManager = Mockito.mock(RabbitManager.class);
        QueryResponse resourceList = new QueryResponse(200,"",new ArrayList<>());

        HttpHeaders headers = new HttpHeaders();
        headers.add(SecurityConstants.SECURITY_CREDENTIALS_TIMESTAMP_HEADER, "1500000000");
        headers.add(SecurityConstants.SECURITY_CREDENTIALS_SIZE_HEADER, "1");
        headers.add(SecurityConstants.SECURITY_CREDENTIALS_HEADER_PREFIX + "1", "{\"token\":\"token\"," +
                "\"authenticationChallenge\":\"authenticationChallenge\"," +
                "\"clientCertificate\":\"clientCertificate\"," +
                "\"clientCertificateSigningAAMCertificate\":\"clientCertificateSigningAAMCertificate\"," +
                "\"foreignTokenIssuingAAMCertificate\":\"foreignTokenIssuingAAMCertificate\"}");

        QueryResourceResult resource1 = new QueryResourceResult();
        resource1.setPlatformId("platform1");
        resource1.setPlatformName("Platform 1");
        resource1.setOwner("Owner 1");
        resource1.setName("Resource 1");
        resource1.setId("res1");
        resource1.setDescription("Resource description");
        resource1.setLocationName("Poznan");
        resource1.setLocationLatitude(52.407193);
        resource1.setLocationLongitude(16.953494);
        resource1.setLocationAltitude(80.0);
        resource1.setObservedProperties(Arrays.asList("Temp", "Hum"));

        QueryResourceResult resource2 = new QueryResourceResult();
        resource2.setPlatformId("platform1");
        resource2.setPlatformName("Platform 1");
        resource2.setOwner("Owner 1");
        resource2.setName("Resource 2");
        resource2.setId("res2");
        resource2.setDescription("Resource description");
        resource2.setLocationName("Poznan");
        resource2.setLocationLatitude(52.407193);
        resource2.setLocationLongitude(16.953494);
        resource2.setLocationAltitude(80.0);
        resource2.setObservedProperties(Collections.singletonList("CO2"));

        QueryResourceResult resource3 = new QueryResourceResult();
        resource3.setPlatformId("platform2");
        resource3.setPlatformName("Platform 2");
        resource3.setOwner("Owner 2");
        resource3.setName("Resource 3");
        resource3.setId("res3");
        resource3.setDescription("Resource description");
        resource3.setLocationName("Poznan");
        resource3.setLocationLatitude(52.407193);
        resource3.setLocationLongitude(16.953494);
        resource3.setLocationAltitude(80.0);
        resource3.setObservedProperties(Arrays.asList("Temp", "Hum"));

        resourceList.getBody().add(resource1);
        resourceList.getBody().add(resource2);
        resourceList.getBody().add(resource3);

        when(rabbitManager.sendSearchRequest((CoreQueryRequest) notNull())).thenReturn(resourceList);

        CoreInterfaceController controller = new CoreInterfaceController(rabbitManager);

        ResponseEntity response = controller.query(null, null, null, null, null, null, null, null, null, null, new String[]{"property1"}, null, null, headers);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof QueryResponse);
        assertEquals(3, ((QueryResponse) response.getBody()).getBody().size());
    }

    @Test
    public void testResourceUrls_noSecurityHeaders() {
        RabbitManager rabbitManager = Mockito.mock(RabbitManager.class);
        when(rabbitManager.sendResourceUrlsRequest((ResourceUrlsRequest) notNull())).thenReturn(null);

        CoreInterfaceController controller = new CoreInterfaceController(rabbitManager);

        ResponseEntity response = controller.getResourceUrls(new String[]{"123"}, null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    public void testResourceUrls_noIds() {
        RabbitManager rabbitManager = Mockito.mock(RabbitManager.class);

        HttpHeaders headers = new HttpHeaders();
        headers.add(SecurityConstants.SECURITY_CREDENTIALS_TIMESTAMP_HEADER, "1500000000");
        headers.add(SecurityConstants.SECURITY_CREDENTIALS_SIZE_HEADER, "1");
        headers.add(SecurityConstants.SECURITY_CREDENTIALS_HEADER_PREFIX + "1", "{\"token\":\"token\"," +
                "\"authenticationChallenge\":\"authenticationChallenge\"," +
                "\"clientCertificate\":\"clientCertificate\"," +
                "\"clientCertificateSigningAAMCertificate\":\"clientCertificateSigningAAMCertificate\"," +
                "\"foreignTokenIssuingAAMCertificate\":\"foreignTokenIssuingAAMCertificate\"}");

        CoreInterfaceController controller = new CoreInterfaceController(rabbitManager);

        try {
            controller.getResourceUrls(null, headers);
            fail();
        } catch (NullPointerException e) {
            //test passed - method should throw exception when passing null
        }
    }

    @Test
    public void testResourceUrls_timeout() {
        RabbitManager rabbitManager = Mockito.mock(RabbitManager.class);
        when(rabbitManager.sendResourceUrlsRequest((ResourceUrlsRequest) notNull())).thenReturn(null);

        HttpHeaders headers = new HttpHeaders();
        headers.add(SecurityConstants.SECURITY_CREDENTIALS_TIMESTAMP_HEADER, "1500000000");
        headers.add(SecurityConstants.SECURITY_CREDENTIALS_SIZE_HEADER, "1");
        headers.add(SecurityConstants.SECURITY_CREDENTIALS_HEADER_PREFIX + "1", "{\"token\":\"token\"," +
                "\"authenticationChallenge\":\"authenticationChallenge\"," +
                "\"clientCertificate\":\"clientCertificate\"," +
                "\"clientCertificateSigningAAMCertificate\":\"clientCertificateSigningAAMCertificate\"," +
                "\"foreignTokenIssuingAAMCertificate\":\"foreignTokenIssuingAAMCertificate\"}");

        CoreInterfaceController controller = new CoreInterfaceController(rabbitManager);

        ResponseEntity response = controller.getResourceUrls(new String[]{"123"}, headers);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    public void testResourceUrls_3ids() {
        RabbitManager rabbitManager = Mockito.mock(RabbitManager.class);
        ResourceUrlsResponse responseObject = new ResourceUrlsResponse();

        HashMap<String, String> result = new HashMap<>();
        result.put("123", "http://example.com/123");
        result.put("abc", "http://example.com/abc");
        result.put("xyz", "http://example.com/xyz");

        responseObject.setBody(result);
        responseObject.setServiceResponse("serviceResponse");
        responseObject.setStatus(200);

        HttpHeaders headers = new HttpHeaders();
        headers.add(SecurityConstants.SECURITY_CREDENTIALS_TIMESTAMP_HEADER, "1500000000");
        headers.add(SecurityConstants.SECURITY_CREDENTIALS_SIZE_HEADER, "1");
        headers.add(SecurityConstants.SECURITY_CREDENTIALS_HEADER_PREFIX + "1", "{\"token\":\"token\"," +
                "\"authenticationChallenge\":\"authenticationChallenge\"," +
                "\"clientCertificate\":\"clientCertificate\"," +
                "\"clientCertificateSigningAAMCertificate\":\"clientCertificateSigningAAMCertificate\"," +
                "\"foreignTokenIssuingAAMCertificate\":\"foreignTokenIssuingAAMCertificate\"}");

        when(rabbitManager.sendResourceUrlsRequest((ResourceUrlsRequest) notNull())).thenReturn(responseObject);

        CoreInterfaceController controller = new CoreInterfaceController(rabbitManager);

        ResponseEntity response = controller.getResourceUrls(new String[]{"123", "abc", "xyz"}, headers);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getHeaders());
        assertTrue(response.getHeaders().containsKey(SecurityConstants.SECURITY_RESPONSE_HEADER));
        assertTrue(response.getBody() instanceof ResourceUrlsResponse);
        assertEquals(3, ((ResourceUrlsResponse) response.getBody()).getBody().size());
    }

    @Test
    public void testSparqlQuery_noSecurityHeaders() {
        RabbitManager rabbitManager = Mockito.mock(RabbitManager.class);
        when(rabbitManager.sendSparqlSearchRequest((CoreSparqlQueryRequest) notNull())).thenReturn(null);

        CoreInterfaceController controller = new CoreInterfaceController(rabbitManager);

        ResponseEntity response = controller.sparqlQuery(new SparqlQueryRequest(), null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    public void testSparqlQuery_timeout() {
        RabbitManager rabbitManager = Mockito.mock(RabbitManager.class);
        when(rabbitManager.sendSparqlSearchRequest((CoreSparqlQueryRequest) notNull())).thenReturn(null);

        HttpHeaders headers = new HttpHeaders();
        headers.add(SecurityConstants.SECURITY_CREDENTIALS_TIMESTAMP_HEADER, "1500000000");
        headers.add(SecurityConstants.SECURITY_CREDENTIALS_SIZE_HEADER, "1");
        headers.add(SecurityConstants.SECURITY_CREDENTIALS_HEADER_PREFIX + "1", "{\"token\":\"token\"," +
                "\"authenticationChallenge\":\"authenticationChallenge\"," +
                "\"clientCertificate\":\"clientCertificate\"," +
                "\"clientCertificateSigningAAMCertificate\":\"clientCertificateSigningAAMCertificate\"," +
                "\"foreignTokenIssuingAAMCertificate\":\"foreignTokenIssuingAAMCertificate\"}");

        CoreInterfaceController controller = new CoreInterfaceController(rabbitManager);

        ResponseEntity response = controller.sparqlQuery(new SparqlQueryRequest(), headers);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    public void testSparqlQuery_emptyResults() {
        RabbitManager rabbitManager = Mockito.mock(RabbitManager.class);
        when(rabbitManager.sendSparqlSearchRequest((CoreSparqlQueryRequest) notNull())).thenReturn(new SparqlQueryResponse(200,"",""));

        HttpHeaders headers = new HttpHeaders();
        headers.add(SecurityConstants.SECURITY_CREDENTIALS_TIMESTAMP_HEADER, "1500000000");
        headers.add(SecurityConstants.SECURITY_CREDENTIALS_SIZE_HEADER, "1");
        headers.add(SecurityConstants.SECURITY_CREDENTIALS_HEADER_PREFIX + "1", "{\"token\":\"token\"," +
                "\"authenticationChallenge\":\"authenticationChallenge\"," +
                "\"clientCertificate\":\"clientCertificate\"," +
                "\"clientCertificateSigningAAMCertificate\":\"clientCertificateSigningAAMCertificate\"," +
                "\"foreignTokenIssuingAAMCertificate\":\"foreignTokenIssuingAAMCertificate\"}");

        CoreInterfaceController controller = new CoreInterfaceController(rabbitManager);

        ResponseEntity response = controller.sparqlQuery(new SparqlQueryRequest(), headers);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof SparqlQueryResponse);
        assertEquals(0, ((SparqlQueryResponse) response.getBody()).getBody().length());
    }

    @Test
    public void testSparqlQuery_3results() {
        RabbitManager rabbitManager = Mockito.mock(RabbitManager.class);
        String rdfResources = "RDF resources";
        SparqlQueryResponse sparqlResponse = new SparqlQueryResponse(200,"OK",rdfResources);

        when(rabbitManager.sendSparqlSearchRequest((CoreSparqlQueryRequest) notNull())).thenReturn(sparqlResponse);

        HttpHeaders headers = new HttpHeaders();
        headers.add(SecurityConstants.SECURITY_CREDENTIALS_TIMESTAMP_HEADER, "1500000000");
        headers.add(SecurityConstants.SECURITY_CREDENTIALS_SIZE_HEADER, "1");
        headers.add(SecurityConstants.SECURITY_CREDENTIALS_HEADER_PREFIX + "1", "{\"token\":\"token\"," +
                "\"authenticationChallenge\":\"authenticationChallenge\"," +
                "\"clientCertificate\":\"clientCertificate\"," +
                "\"clientCertificateSigningAAMCertificate\":\"clientCertificateSigningAAMCertificate\"," +
                "\"foreignTokenIssuingAAMCertificate\":\"foreignTokenIssuingAAMCertificate\"}");

        CoreInterfaceController controller = new CoreInterfaceController(rabbitManager);

        ResponseEntity response = controller.sparqlQuery(new SparqlQueryRequest(), headers);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof SparqlQueryResponse);
        assertEquals(13, ((SparqlQueryResponse) response.getBody()).getBody().length());
    }

    @Test
    public void testGetAams_ok() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);

        List<AAM> aamsList = new ArrayList<>();
        aamsList.add(new AAM("localhost","localhost","localhost",null,new HashMap<String, Certificate>()));
        aamsList.add(new AAM("localhost2","localhost2","localhost2",null,new HashMap<String, Certificate>()));

        ResponseEntity response = new ResponseEntity(aamsList, HttpStatus.OK);

        when(restTemplate.getForEntity(anyString(), eq(String.class))).thenReturn(response);

        CoreInterfaceController controller = new CoreInterfaceController(null);
        controller.setRestTemplate(restTemplate);

        ResponseEntity result = controller.getAvailableAAMs();
        assertNotNull(result);
        assertEquals(HttpStatus.OK, result.getStatusCode());

        assertNotNull(result.getBody());
        assertTrue(result.getBody() instanceof List);
        assertEquals(2, ((List) result.getBody()).size());
        assertTrue(((List) result.getBody()).get(0) instanceof AAM);
    }

    @Test
    public void testGetAams_internalServer() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);

        HttpServerErrorException exception = new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR);

        when(restTemplate.getForEntity(anyString(), eq(String.class))).thenThrow(exception);

        CoreInterfaceController controller = new CoreInterfaceController(null);
        controller.setRestTemplate(restTemplate);

        ResponseEntity result = controller.getAvailableAAMs();

        assertNotNull(result);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());

    }

    @Test
    public void testGetComponentCertificate_ok() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);

        ResponseEntity response = new ResponseEntity("Component certificate", HttpStatus.OK);

        when(restTemplate.getForEntity(anyString(), eq(String.class))).thenReturn(response);

        CoreInterfaceController controller = new CoreInterfaceController(null);
        controller.setRestTemplate(restTemplate);

        ResponseEntity result = controller.getComponentCertificate("ComponentID", "PlatformID");
        assertNotNull(result);
        assertEquals(HttpStatus.OK, result.getStatusCode());

        assertNotNull(result.getBody());
        assertTrue(result.getBody() instanceof String);
        assertEquals(21, ((String) result.getBody()).length());
    }

    @Test
    public void testGetComponentCertificate_internalServer() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);

        HttpServerErrorException exception = new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR);

        when(restTemplate.getForEntity(anyString(), eq(String.class))).thenThrow(exception);

        CoreInterfaceController controller = new CoreInterfaceController(null);
        controller.setRestTemplate(restTemplate);

        ResponseEntity result = controller.getComponentCertificate("ComponentID", "PlatformID");

        assertNotNull(result);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());

    }

    @Test
    public void testSignCertificateRequest_ok() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);

        ResponseEntity response = new ResponseEntity("Sign certificate", HttpStatus.OK);

        when(restTemplate.postForEntity(anyString(), anyString(), eq(String.class))).thenReturn(response);

        CertificateRequest certificateRequest = new CertificateRequest("username", "password", "clientId", "clientCSRinPEMFormat");

        CoreInterfaceController controller = new CoreInterfaceController(null);
        controller.setRestTemplate(restTemplate);

        ResponseEntity result = controller.signCertificateRequest(certificateRequest);
        assertNotNull(result);
        assertEquals(HttpStatus.OK, result.getStatusCode());

        assertNotNull(result.getBody());
        assertTrue(result.getBody() instanceof String);
        assertEquals(16, ((String) result.getBody()).length());
    }

    @Test
    public void testSignCertificateRequest_internalServer() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);

        HttpServerErrorException exception = new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR);

        when(restTemplate.postForEntity(anyString(), anyString(), eq(String.class))).thenThrow(exception);

        CertificateRequest certificateRequest = new CertificateRequest("username", "password", "clientId", "clientCSRinPEMFormat");

        CoreInterfaceController controller = new CoreInterfaceController(null);
        controller.setRestTemplate(restTemplate);

        ResponseEntity result = controller.signCertificateRequest(certificateRequest);

        assertNotNull(result);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());

    }

    @Test
    public void testRevokeCredentials_ok() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);

        ResponseEntity response = new ResponseEntity("Revoke crednetials", HttpStatus.OK);

        when(restTemplate.postForEntity(anyString(), anyString(), eq(String.class))).thenReturn(response);

        RevocationRequest revocationRequest = new RevocationRequest();

        CoreInterfaceController controller = new CoreInterfaceController(null);
        controller.setRestTemplate(restTemplate);

        ResponseEntity result = controller.revokeCredentials(revocationRequest);
        assertNotNull(result);
        assertEquals(HttpStatus.OK, result.getStatusCode());

        assertNotNull(result.getBody());
        assertTrue(result.getBody() instanceof String);
        assertEquals(18, ((String) result.getBody()).length());
    }

    @Test
    public void testRevokeCredentials_internalServer() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);

        HttpServerErrorException exception = new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR);

        when(restTemplate.postForEntity(anyString(), anyString(), eq(String.class))).thenThrow(exception);

        RevocationRequest revocationRequest = new RevocationRequest();

        CoreInterfaceController controller = new CoreInterfaceController(null);
        controller.setRestTemplate(restTemplate);

        ResponseEntity result = controller.revokeCredentials(revocationRequest);

        assertNotNull(result);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());

    }

    @Test
    public void testGetGuestToken_ok() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);

        ResponseEntity response = new ResponseEntity("Guest token", HttpStatus.OK);

        when(restTemplate.postForEntity(anyString(), anyString(), eq(String.class))).thenReturn(response);

        CoreInterfaceController controller = new CoreInterfaceController(null);
        controller.setRestTemplate(restTemplate);

        ResponseEntity result = controller.getGuestToken();
        assertNotNull(result);
        assertEquals(HttpStatus.OK, result.getStatusCode());

        assertNotNull(result.getBody());
        assertTrue(result.getBody() instanceof String);
        assertEquals(11, ((String) result.getBody()).length());
    }

    @Test
    public void testGetGuestToken_internalServer() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);

        HttpServerErrorException exception = new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR);

        when(restTemplate.postForEntity(anyString(), anyString(), eq(String.class))).thenThrow(exception);

        CoreInterfaceController controller = new CoreInterfaceController(null);
        controller.setRestTemplate(restTemplate);

        ResponseEntity result = controller.getGuestToken();

        assertNotNull(result);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());

    }

    @Test
    public void testGetHomeToken_ok() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);

        ResponseEntity response = new ResponseEntity("Home token", HttpStatus.OK);

        when(restTemplate.postForEntity(anyString(), anyString(), eq(String.class))).thenReturn(response);

        CoreInterfaceController controller = new CoreInterfaceController(null);
        controller.setRestTemplate(restTemplate);

        ResponseEntity result = controller.getHomeToken("loginRequest");
        assertNotNull(result);
        assertEquals(HttpStatus.OK, result.getStatusCode());

        assertNotNull(result.getBody());
        assertTrue(result.getBody() instanceof String);
        assertEquals(10, ((String) result.getBody()).length());
    }

    @Test
    public void testGetHomeToken_internalServer() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);

        HttpServerErrorException exception = new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR);

        when(restTemplate.postForEntity(anyString(), anyString(), eq(String.class))).thenThrow(exception);

        CoreInterfaceController controller = new CoreInterfaceController(null);
        controller.setRestTemplate(restTemplate);

        ResponseEntity result = controller.getHomeToken("loginRequest");

        assertNotNull(result);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());

    }

    @Test
    public void testGetForeignToken_ok() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);

        ResponseEntity response = new ResponseEntity("Foreign token", HttpStatus.OK);

        when(restTemplate.postForEntity(anyString(), anyString(), eq(String.class))).thenReturn(response);

        CoreInterfaceController controller = new CoreInterfaceController(null);
        controller.setRestTemplate(restTemplate);

        ResponseEntity result = controller.getForeignToken("remoteHomeToken", "clientCertificate","aamCertificate");
        assertNotNull(result);
        assertEquals(HttpStatus.OK, result.getStatusCode());

        assertNotNull(result.getBody());
        assertTrue(result.getBody() instanceof String);
        assertEquals(13, ((String) result.getBody()).length());
    }

    @Test
    public void testGetForeignToken_internalServer() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);

        HttpServerErrorException exception = new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR);

        when(restTemplate.postForEntity(anyString(), anyString(), eq(String.class))).thenThrow(exception);

        CoreInterfaceController controller = new CoreInterfaceController(null);
        controller.setRestTemplate(restTemplate);

        ResponseEntity result = controller.getForeignToken("remoteHomeToken", "clientCertificate","aamCertificate");

        assertNotNull(result);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());

    }

    @Test
    public void testValidateCredentials_ok() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);

        ResponseEntity response = new ResponseEntity(ValidationStatus.VALID, HttpStatus.OK);

        when(restTemplate.postForEntity(anyString(), anyString(), eq(String.class))).thenReturn(response);

        CoreInterfaceController controller = new CoreInterfaceController(null);
        controller.setRestTemplate(restTemplate);

        ResponseEntity result = controller.validateCredentials("token", "clientCertificate", "clientCertificateSigningAAMcertificate", "foreignTokenIssuingAAMCertificate");
        assertNotNull(result);
        assertEquals(HttpStatus.OK, result.getStatusCode());

        assertNotNull(result.getBody());
        assertTrue(result.getBody() instanceof ValidationStatus);
        assertEquals(ValidationStatus.VALID, (ValidationStatus) result.getBody());
    }

    @Test
    public void testValidateCredentials_internalServer() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);

        HttpServerErrorException exception = new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR);

        when(restTemplate.postForEntity(anyString(), anyString(), eq(String.class))).thenThrow(exception);

        CoreInterfaceController controller = new CoreInterfaceController(null);
        controller.setRestTemplate(restTemplate);

        ResponseEntity result = controller.validateCredentials("token", "clientCertificate", "clientCertificateSigningAAMcertificate", "foreignTokenIssuingAAMCertificate");

        assertNotNull(result);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());

    }

    @Test
    public void testGetUserDetails_ok() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);

        UserDetails userDetails = new UserDetails();
        Credentials credentials = new Credentials();

        ResponseEntity response = new ResponseEntity(userDetails, HttpStatus.OK);

        when(restTemplate.postForEntity(anyString(), anyString(), eq(String.class))).thenReturn(response);

        CoreInterfaceController controller = new CoreInterfaceController(null);
        controller.setRestTemplate(restTemplate);

        ResponseEntity result = controller.getUserDetails(credentials);
        assertNotNull(result);
        assertEquals(HttpStatus.OK, result.getStatusCode());

        assertNotNull(result.getBody());
        assertTrue(result.getBody() instanceof UserDetails);
    }

    @Test
    public void testGetUserDetails_internalServer() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);

        Credentials credentials = new Credentials();

        HttpServerErrorException exception = new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR);

        when(restTemplate.postForEntity(anyString(), anyString(), eq(String.class))).thenThrow(exception);

        CoreInterfaceController controller = new CoreInterfaceController(null);
        controller.setRestTemplate(restTemplate);

        ResponseEntity result = controller.getUserDetails(credentials);

        assertNotNull(result);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());

    }

}

