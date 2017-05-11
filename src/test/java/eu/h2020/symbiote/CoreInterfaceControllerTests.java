package eu.h2020.symbiote;

import eu.h2020.symbiote.communication.RabbitManager;
import eu.h2020.symbiote.controllers.CoreInterfaceController;
import eu.h2020.symbiote.core.ci.QueryResourceResult;
import eu.h2020.symbiote.core.ci.QueryResponse;
import eu.h2020.symbiote.core.internal.CoreQueryRequest;
import eu.h2020.symbiote.core.internal.ResourceUrlsRequest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.notNull;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CoreInterfaceControllerTests {

    @Test
    public void testQuery_timeout() {
        RabbitManager rabbitManager = Mockito.mock(RabbitManager.class);
        when(rabbitManager.sendSearchRequest((CoreQueryRequest) notNull())).thenReturn(null);

        CoreInterfaceController controller = new CoreInterfaceController(rabbitManager);

        ResponseEntity response = controller.query(null, null, null, null, null, null, null, null, null, null, null, null,null);

        assertEquals(response.getStatusCode(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    public void testQuery_emptyResults() {
        RabbitManager rabbitManager = Mockito.mock(RabbitManager.class);
        when(rabbitManager.sendSearchRequest((CoreQueryRequest) notNull())).thenReturn(new QueryResponse());

        CoreInterfaceController controller = new CoreInterfaceController(rabbitManager);

        ResponseEntity response = controller.query(null, null, null, null, null, null, null, null, null, null, new String[]{"property1"}, null,null);

        assertEquals(response.getStatusCode(), HttpStatus.OK);
        assertTrue(response.getBody() instanceof QueryResponse);
        assertEquals(0, ((QueryResponse) response.getBody()).getResources().size());
    }

    @Test
    public void testQuery_3results() {
        RabbitManager rabbitManager = Mockito.mock(RabbitManager.class);
        QueryResponse resourceList = new QueryResponse();

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

        resourceList.getResources().add(resource1);
        resourceList.getResources().add(resource2);
        resourceList.getResources().add(resource3);

        when(rabbitManager.sendSearchRequest((CoreQueryRequest) notNull())).thenReturn(resourceList);

        CoreInterfaceController controller = new CoreInterfaceController(rabbitManager);

        ResponseEntity response = controller.query(null, null, null, null, null, null, null, null, null, null, new String[]{"property1"}, null, null);

        assertEquals(response.getStatusCode(), HttpStatus.OK);
        assertTrue(response.getBody() instanceof QueryResponse);
        assertEquals(3, ((QueryResponse) response.getBody()).getResources().size());
    }

    @Test
    public void testResourceUrls_noIds() {
        RabbitManager rabbitManager = Mockito.mock(RabbitManager.class);

        CoreInterfaceController controller = new CoreInterfaceController(rabbitManager);

        try {
            controller.getResourceUrls(null, null);
            fail();
        } catch (NullPointerException e) {
            //test passed - method should throw exception when passing null
        }
    }

    @Test
    public void testResourceUrls_timeout() {
        RabbitManager rabbitManager = Mockito.mock(RabbitManager.class);
        when(rabbitManager.sendResourceUrlsRequest((ResourceUrlsRequest) notNull())).thenReturn(null);

        CoreInterfaceController controller = new CoreInterfaceController(rabbitManager);

        ResponseEntity response = controller.getResourceUrls(new String[]{"123"}, null);

        assertEquals(response.getStatusCode(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    public void testResourceUrls_3ids() {
        RabbitManager rabbitManager = Mockito.mock(RabbitManager.class);
        Map<String, String> result = new HashMap<>();
        result.put("123","http://example.com/123");
        result.put("abc","http://example.com/abc");
        result.put("xyz","http://example.com/xyz");

        when(rabbitManager.sendResourceUrlsRequest((ResourceUrlsRequest) notNull())).thenReturn(result);

        CoreInterfaceController controller = new CoreInterfaceController(rabbitManager);

        ResponseEntity response = controller.getResourceUrls(new String[]{"123", "abc", "xyz"}, null);

        assertEquals(response.getStatusCode(), HttpStatus.OK);
        assertTrue(response.getBody() instanceof Map);
        assertEquals(3, ((Map) response.getBody()).size());
    }


}

