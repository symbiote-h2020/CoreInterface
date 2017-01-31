package eu.h2020.symbiote.controllers;

import eu.h2020.symbiote.communication.RabbitManager;
import eu.h2020.symbiote.model.QueryRequest;
import eu.h2020.symbiote.model.Resource;
import eu.h2020.symbiote.model.ResourceUrlsRequest;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST endpoints definition.
 */

@RestController
@CrossOrigin
public class CoreInterfaceController {
    private static final String URI_PREFIX = "/coreInterface/v1";

    public static Log log = LogFactory.getLog(CoreInterfaceController.class);

    private final RabbitManager rabbitManager;

    @Autowired
    public CoreInterfaceController(RabbitManager rabbitManager){
        this.rabbitManager = rabbitManager;
    }

    @RequestMapping(method = RequestMethod.GET,
            value = URI_PREFIX + "/query")
    public ResponseEntity<?> query(@RequestParam(value = "platform_id", required = false) String platformId,
                                   @RequestParam(value = "platform_name", required = false) String platformName,
                                   @RequestParam(value = "owner", required = false) String owner,
                                   @RequestParam(value = "name", required = false) String name,
                                   @RequestParam(value = "id", required = false) String id,
                                   @RequestParam(value = "description", required = false) String description,
                                   @RequestParam(value = "location_name", required = false) String location_name,
                                   @RequestParam(value = "location_lat", required = false) Double location_lat ,
                                   @RequestParam(value = "location_long", required = false) Double location_long ,
                                   @RequestParam(value = "max_distance", required = false) Integer max_distance ,
                                   @RequestParam(value = "observed_property", required = false) String[] observed_property) {

        QueryRequest queryRequest = new QueryRequest();
        queryRequest.setPlatform_id(platformId);
        queryRequest.setPlatform_name(platformName);
        queryRequest.setOwner(owner);
        queryRequest.setName(name);
        queryRequest.setId(id);
        queryRequest.setDescription(description);
        queryRequest.setLocation_name(location_name);
        queryRequest.setLocation_lat(location_lat);
        queryRequest.setLocation_long(location_long);
        queryRequest.setMax_distance(max_distance);
        if (observed_property != null){
            queryRequest.setObserved_property(Arrays.asList(observed_property));
        }

        List<Resource> resources = this.rabbitManager.sendSearchRequest(queryRequest);
        if (resources == null){
            return new ResponseEntity<String>("", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return new ResponseEntity<List<Resource>>(resources, HttpStatus.OK);
    }

    @RequestMapping(method = RequestMethod.POST,
            value = URI_PREFIX + "/sparqlQuery")
    public ResponseEntity<?> sparqlQuery(@RequestBody String sparqlQuery) {
        return new ResponseEntity<String>("Sparql Query " + sparqlQuery, HttpStatus.NOT_IMPLEMENTED);
    }

    @RequestMapping(method = RequestMethod.GET,
            value = URI_PREFIX + "/resourceUrls")
    public ResponseEntity<?> getResourceUrls(@RequestParam("id") String[] resourceId) {
        ResourceUrlsRequest request = new ResourceUrlsRequest();
        request.setIdList(Arrays.asList(resourceId));

        Map<String, String> response = this.rabbitManager.sendResourceUrlsRequest(request);
        if (response == null){
            return new ResponseEntity<String>("", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return new ResponseEntity<Map<String, String>>(response, HttpStatus.OK);
    }

}
