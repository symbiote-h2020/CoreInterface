package eu.h2020.symbiote.controllers;

import eu.h2020.symbiote.communication.RabbitManager;
import eu.h2020.symbiote.core.ci.QueryResponse;
import eu.h2020.symbiote.core.ci.SparqlQueryRequest;
import eu.h2020.symbiote.core.internal.CoreQueryRequest;
import eu.h2020.symbiote.core.internal.CoreSparqlQueryRequest;
import eu.h2020.symbiote.core.internal.ResourceUrlsRequest;
import eu.h2020.symbiote.security.constants.AAMConstants;
import eu.h2020.symbiote.security.payloads.Credentials;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

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

    public static final Log log = LogFactory.getLog(CoreInterfaceController.class);

    private final RabbitManager rabbitManager;

    @Value("${symbiote.aamUrl}")
    private String aamUrl;

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
    public ResponseEntity query(@RequestParam(value = "platform_id", required = false) String platformId,
                                @RequestParam(value = "platform_name", required = false) String platformName,
                                @RequestParam(value = "owner", required = false) String owner,
                                @RequestParam(value = "name", required = false) String name,
                                @RequestParam(value = "id", required = false) String id,
                                @RequestParam(value = "description", required = false) String description,
                                @RequestParam(value = "location_name", required = false) String location_name,
                                @RequestParam(value = "location_lat", required = false) Double location_lat,
                                @RequestParam(value = "location_long", required = false) Double location_long,
                                @RequestParam(value = "max_distance", required = false) Integer max_distance,
                                @RequestParam(value = "observed_property", required = false) String[] observed_property,
                                @RequestParam(value = "resource_type", required = false) String resource_type,
                                @RequestHeader("X-Auth-Token") String token) {

        CoreQueryRequest queryRequest = new CoreQueryRequest();
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
        queryRequest.setResource_type(resource_type);
        queryRequest.setToken(token);
        if (observed_property != null) {
            queryRequest.setObserved_property(Arrays.asList(observed_property));
        }

        QueryResponse resources = this.rabbitManager.sendSearchRequest(queryRequest);
        if (resources == null) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return new ResponseEntity<>(resources, HttpStatus.OK);
    }

    /**
     * Endpoint for querying registered resources using SPARQL.
     * <p>
     * After receiving query results, user (or application) may choose interesting resources to contact, but it does not
     * have any means of communicate with resources' Interworking Interface. Therefore, it needs to send another request
     * querying for URLs Interworking Services of resources of specified IDs.
     *
     * @param sparqlQuery query object containing sparql query and output format to get results in
     * @param token       security token
     */
    @RequestMapping(method = RequestMethod.POST,
            value = URI_PREFIX + "/sparqlQuery")
    public ResponseEntity sparqlQuery(@RequestBody SparqlQueryRequest sparqlQuery, @RequestHeader("X-Auth-Token") String token) {
        CoreSparqlQueryRequest request = new CoreSparqlQueryRequest();
        request.setToken(token);
        request.setQuery(sparqlQuery.getSparqlQuery());
        request.setOutputFormat(sparqlQuery.getOutputFormat());
        String resources = this.rabbitManager.sendSparqlSearchRequest(request);
        if (resources == null) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return new ResponseEntity<>(resources, HttpStatus.OK);
    }


    /**
     * Endpoint for querying URL of resources' Interworking Interface.
     * <p>
     * After receiving query results, user (or application) may choose interesting resources to contact, but it does not
     * have any means of communicate with resources' Interworking Interface. Therefore, it needs to send another request
     * querying for URLs Interworking Services of resources of specified IDs.
     *
     * @param resourceId ID of a resource to get Interworking Interface URL; multiple IDs can be passed
     * @return map containing entries in a form of {"resourceId1":"InterworkingInterfaceUrl1", "resourceId2":"InterworkingInterface2", ... }
     */
    @RequestMapping(method = RequestMethod.GET,
            value = URI_PREFIX + "/resourceUrls")
    public ResponseEntity getResourceUrls(@RequestParam("id") String[] resourceId, @RequestHeader("X-Auth-Token") String token) {
        ResourceUrlsRequest request = new ResourceUrlsRequest();
        request.setIdList(Arrays.asList(resourceId));
        request.setToken(token);

        Map<String, String> response = this.rabbitManager.sendResourceUrlsRequest(request);
        if (response == null) {
            return new ResponseEntity<>("", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @RequestMapping(method = RequestMethod.POST,
            value = URI_PREFIX + AAMConstants.AAM_LOGIN)
    public ResponseEntity login(@RequestBody Credentials user) {
        log.debug("Login request");
        try {
            ResponseEntity<String> entity = new RestTemplate().postForEntity(this.aamUrl + AAMConstants.AAM_LOGIN, null, String.class);

            HttpHeaders headers = stripTransferEndoding(entity.getHeaders());

            return new ResponseEntity(entity.getBody(), headers, entity.getStatusCode());
        } catch (HttpStatusCodeException e) {
            return new ResponseEntity<>(e.getResponseBodyAsString(), e.getStatusCode());
        }
    }

    @RequestMapping(method = RequestMethod.GET,
            value = URI_PREFIX + AAMConstants.AAM_GET_CA_CERTIFICATE)
    public ResponseEntity getCaCert() {
        log.debug("Get CA Cert request");
        try {
            ResponseEntity<String> entity = new RestTemplate().getForEntity(this.aamUrl + AAMConstants.AAM_GET_CA_CERTIFICATE, String.class);

            HttpHeaders headers = stripTransferEndoding(entity.getHeaders());

            return new ResponseEntity(entity.getBody(), headers, entity.getStatusCode());
        } catch (HttpStatusCodeException e) {
            return new ResponseEntity<>(e.getResponseBodyAsString(), e.getStatusCode());
        }
    }

    @RequestMapping(method = RequestMethod.POST,
            value = URI_PREFIX + AAMConstants.AAM_REQUEST_FOREIGN_TOKEN)
    public ResponseEntity requestForeignToken(@RequestHeader(AAMConstants.TOKEN_HEADER_NAME) String token) {
        log.debug("Foreign token request");
        try {
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.add(AAMConstants.TOKEN_HEADER_NAME, token);

            HttpEntity<String> entity = new HttpEntity<>(null, httpHeaders);

            ResponseEntity<String> stringResponseEntity = new RestTemplate().postForEntity(this.aamUrl + AAMConstants.AAM_REQUEST_FOREIGN_TOKEN, entity, String.class);

            HttpHeaders headers = stripTransferEndoding(stringResponseEntity.getHeaders());

            return new ResponseEntity(stringResponseEntity.getBody(), headers, stringResponseEntity.getStatusCode());
        } catch (HttpStatusCodeException e) {
            return new ResponseEntity<>(e.getResponseBodyAsString(), e.getStatusCode());
        }
    }

    @RequestMapping(method = RequestMethod.POST,
            value = URI_PREFIX + AAMConstants.AAM_CHECK_HOME_TOKEN_REVOCATION)
    public ResponseEntity checkHomeTokenRevocation(@RequestHeader(AAMConstants.TOKEN_HEADER_NAME) String token) {
        log.debug("Check Home Token revocation request");
        try {
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.add(AAMConstants.TOKEN_HEADER_NAME, token);

            HttpEntity<String> entity = new HttpEntity<>(null, httpHeaders);

            ResponseEntity<String> stringResponseEntity = new RestTemplate().postForEntity(this.aamUrl + AAMConstants.AAM_CHECK_HOME_TOKEN_REVOCATION, entity, String.class);

            HttpHeaders headers = stripTransferEndoding(stringResponseEntity.getHeaders());

            return new ResponseEntity(stringResponseEntity.getBody(), headers, stringResponseEntity.getStatusCode());
        } catch (HttpStatusCodeException e) {
            return new ResponseEntity<>(e.getResponseBodyAsString(), e.getStatusCode());
        }
    }

    @RequestMapping(method = RequestMethod.GET,
            value = URI_PREFIX + AAMConstants.AAM_GET_AVAILABLE_AAMS)
    public ResponseEntity getAvailableAAMs() {
        log.debug("Get Available AAMS request");
        try {
            ResponseEntity<String> entity = new RestTemplate().getForEntity(this.aamUrl + AAMConstants.AAM_GET_AVAILABLE_AAMS, String.class);

            HttpHeaders headers = stripTransferEndoding(entity.getHeaders());

            return new ResponseEntity(entity.getBody(), headers, entity.getStatusCode());
        } catch (HttpStatusCodeException e) {
            return new ResponseEntity<>(e.getResponseBodyAsString(), e.getStatusCode());
        }
    }

    private HttpHeaders stripTransferEndoding(HttpHeaders headers){
        if (headers == null)
            return null;

        HttpHeaders newHeaders = new HttpHeaders();

        for (Map.Entry<String, List<String>> entry : headers.entrySet()){
            if (!entry.getKey().equals(HttpHeaders.TRANSFER_ENCODING)){
                for (String value : entry.getValue())
                    newHeaders.add(entry.getKey(), value);
            }

        }

        return newHeaders;
    }


}
