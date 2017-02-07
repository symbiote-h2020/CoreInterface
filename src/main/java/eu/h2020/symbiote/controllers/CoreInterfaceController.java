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
import java.util.List;
import java.util.Map;

/**
 * Class defining all REST endpoints.
 * <p>
 * CoreInterface, as the name suggests, is just an interface, therefore it forwards all requests to modules responsible
 * for handling them via RabbitMQ.
 */
@RestController
@CrossOrigin
public class CoreInterfaceController {
    private static final String URI_PREFIX = "/coreInterface/v1";

    public static Log log = LogFactory.getLog(CoreInterfaceController.class);

    private final RabbitManager rabbitManager;

    /**
     * Class constructor which autowires RabbitManager bean.
     *
     * @param rabbitManager RabbitManager bean
     */
    @Autowired
    public CoreInterfaceController(RabbitManager rabbitManager) {
        this.rabbitManager = rabbitManager;
    }

    /**
     * Endpoint for querying registered resources. Query parameters are passed via GET request params and are all
     * optional. When passing multiple parameters, including multiple observed_properties, they are all linked with
     * logical AND operator. In "text" parameters (name, description, platformName, owner, locationName), * can be used
     * as a wildcard in the beginning or/and in the end of value.
     *
     * @param platformId        symbIoTe ID of a platform that resource belongs to
     * @param platformName      name of a platform that resource belongs to
     * @param owner             owner of the resource
     * @param name              name of the resource
     * @param id                symbIoTe ID of the resource
     * @param description       description of the resource
     * @param location_name     name of resource location
     * @param location_lat      latitude of resource location
     * @param location_long     longitude of resource location
     * @param max_distance      maximal distance from specified resource latitude and longitude (in meters)
     * @param observed_property property observed by resource; can be set multiple times to indicate more than one
     *                          observed property
     * @return query result as body or null along with appropriate error HTTP status code
     */
    @RequestMapping(method = RequestMethod.GET,
            value = URI_PREFIX + "/query")
    public ResponseEntity<?> query(@RequestParam(value = "platform_id", required = false) String platformId,
                                   @RequestParam(value = "platform_name", required = false) String platformName,
                                   @RequestParam(value = "owner", required = false) String owner,
                                   @RequestParam(value = "name", required = false) String name,
                                   @RequestParam(value = "id", required = false) String id,
                                   @RequestParam(value = "description", required = false) String description,
                                   @RequestParam(value = "location_name", required = false) String location_name,
                                   @RequestParam(value = "location_lat", required = false) Double location_lat,
                                   @RequestParam(value = "location_long", required = false) Double location_long,
                                   @RequestParam(value = "max_distance", required = false) Integer max_distance,
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
        if (observed_property != null) {
            queryRequest.setObserved_property(Arrays.asList(observed_property));
        }

        List<Resource> resources = this.rabbitManager.sendSearchRequest(queryRequest);
        if (resources == null) {
            return new ResponseEntity<>("", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return new ResponseEntity<>(resources, HttpStatus.OK);
    }

    /**
     * Endpoint for querying registered resources using SPARQL.
     * <p>
     * Currently not implemented.
     */
    @RequestMapping(method = RequestMethod.POST,
            value = URI_PREFIX + "/sparqlQuery")
    public ResponseEntity<?> sparqlQuery(@RequestBody String sparqlQuery) {
        return new ResponseEntity<>("Sparql Query " + sparqlQuery, HttpStatus.NOT_IMPLEMENTED);
    }


    /**
     * Endpoint for querying URL of resources' Interworking Interface.
     *
     * After receiving query results, user (or application) may choose interesting resources to contact, but it does not
     * have any means of communicate with resources' Interworking Interface. Therefore, it needs to send another request
     * querying for URLs Interworking Services of resources of specified IDs.
     *
     * @param resourceId ID of a resource to get Interworking Interface URL; multiple IDs can be passed
     *
     * @return map containing entries in a form of {"resourceId1":"InterworkingInterfaceUrl1", "resourceId2":"InterworkingInterface2", ... }
     */
    @RequestMapping(method = RequestMethod.GET,
            value = URI_PREFIX + "/resourceUrls")
    public ResponseEntity<?> getResourceUrls(@RequestParam("id") String[] resourceId) {
        ResourceUrlsRequest request = new ResourceUrlsRequest();
        request.setIdList(Arrays.asList(resourceId));

        Map<String, String> response = this.rabbitManager.sendResourceUrlsRequest(request);
        if (response == null) {
            return new ResponseEntity<>("", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

}
