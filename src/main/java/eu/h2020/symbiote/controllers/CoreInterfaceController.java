package eu.h2020.symbiote.controllers;

import eu.h2020.symbiote.communication.RabbitManager;
import eu.h2020.symbiote.core.cci.AbstractResponseSecured;
import eu.h2020.symbiote.core.cci.ResourceRegistryResponse;
import eu.h2020.symbiote.core.ci.QueryResponse;
import eu.h2020.symbiote.core.ci.SparqlQueryRequest;
import eu.h2020.symbiote.core.ci.SparqlQueryResponse;
import eu.h2020.symbiote.core.internal.CoreQueryRequest;
import eu.h2020.symbiote.core.internal.CoreSparqlQueryRequest;
import eu.h2020.symbiote.core.internal.cram.ResourceUrlsRequest;
import eu.h2020.symbiote.core.internal.cram.ResourceUrlsResponse;
import eu.h2020.symbiote.security.commons.SecurityConstants;
import eu.h2020.symbiote.security.commons.enums.ValidationStatus;
import eu.h2020.symbiote.security.commons.exceptions.custom.InvalidArgumentsException;
import eu.h2020.symbiote.security.communication.payloads.*;
import io.swagger.annotations.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Class defining all REST endpoints.
 * <p>
 * CoreInterface, as the name suggests, is just an interface, therefore it forwards all requests to modules responsible
 * for handling them via RabbitMQ.
 */
@RestController
@CrossOrigin
@Api(tags = "Core Interface Controller", description = "Operations of Core Interface Controller")
public class CoreInterfaceController {
    private static final String LEGACY_URI_PREFIX = "/coreInterface/v1";
    private static final String AAM_PREFIX = "/aam";
    private static final String BTM_PREFIX = "/btm";
    private static final String ERROR_PROXY_STATUS_MSG = "Error status code in proxy communication: ";
    private static final String ERROR_GATEWAY_TIMEOUT = "Timeout occured when contacting symbIoTe Core services";

    public static final Log log = LogFactory.getLog(CoreInterfaceController.class);

    private final RabbitManager rabbitManager;
    private RestTemplate restTemplate;

    @Value("${symbiote.aamUrl}")
    private String aamUrl;

    @Value("${symbiote.admUrl}")
    private String admUrl;

    @Value("${symbiote.btmUrl}")
    private String btmUrl;

    /**
     * Class constructor which autowires RabbitManager bean.
     *
     * @param rabbitManager RabbitManager bean
     */
    @Autowired
    public CoreInterfaceController(RabbitManager rabbitManager) {
        this.rabbitManager = rabbitManager;
        this.restTemplate = new RestTemplate();
    }

    private ResponseEntity handleBadSecurityHeaders(InvalidArgumentsException e) {
        log.error("No proper security headers passed", e);
        ResourceRegistryResponse response = new ResourceRegistryResponse();
        response.setStatus(401);
        response.setMessage("Invalid security headers");
        response.setBody(null);

        return new ResponseEntity<>(response, HttpStatus.valueOf(response.getStatus()));
    }

    /* -------------------------------------------- */
    /*                   SEARCH                     */
    /* -------------------------------------------- */

    @Deprecated
    @RequestMapping(method = RequestMethod.GET,
            value = LEGACY_URI_PREFIX + "/query")
    public ResponseEntity legacyQuery(@ApiParam(value = "ID of a platform that resource belongs to") @RequestParam(value = "platform_id", required = false) String platform_id,
                                      @ApiParam(value = "ID of a platform that resource belongs to") @RequestParam(value = "platformId", required = false) String platformId,
                                      @ApiParam(value = "name of a platform that resource belongs to") @RequestParam(value = "platform_name", required = false) String platform_name,
                                      @ApiParam(value = "name of a platform that resource belongs to") @RequestParam(value = "platformName", required = false) String platformName,
                                      @ApiParam(value = "owner of a platform that resource belongs to") @RequestParam(value = "owner", required = false) String owner,
                                      @ApiParam(value = "name of a resource") @RequestParam(value = "name", required = false) String name,
                                      @ApiParam(value = "ID of a resource") @RequestParam(value = "id", required = false) String id,
                                      @ApiParam(value = "description of a resource") @RequestParam(value = "description", required = false) String description,
                                      @ApiParam(value = "name of resource's location") @RequestParam(value = "location_name", required = false) String location_name,
                                      @ApiParam(value = "name of resource's location") @RequestParam(value = "locationName", required = false) String locationName,
                                      @ApiParam(value = "latitude of resource's location") @RequestParam(value = "location_lat", required = false) Double location_lat,
                                      @ApiParam(value = "latitude of resource's location") @RequestParam(value = "locationLatitude", required = false) Double locationLatitude,
                                      @ApiParam(value = "longitude of resource's location") @RequestParam(value = "location_long", required = false) Double location_long,
                                      @ApiParam(value = "longitude of resource's location") @RequestParam(value = "locationLongitude", required = false) Double locationLongitude,
                                      @ApiParam(value = "maximum radius from specified latitude and longitude to look for resources") @RequestParam(value = "max_distance", required = false) Integer max_distance,
                                      @ApiParam(value = "maximum radius from specified latitude and longitude to look for resources") @RequestParam(value = "maxDistance", required = false) Integer maxDistance,
                                      @ApiParam(value = "recource's observed property; can be passed multiple times (acts as AND)") @RequestParam(value = "observed_property", required = false) String[] observed_property,
                                      @ApiParam(value = "recource's observed property; can be passed multiple times (acts as AND)") @RequestParam(value = "observedProperty", required = false) String[] observedProperty,
                                      @ApiParam(value = "type of a resource") @RequestParam(value = "resource_type", required = false) String resource_type,
                                      @ApiParam(value = "type of a resource") @RequestParam(value = "resourceType", required = false) String resourceType,
                                      @ApiParam(value = "whether results should be ranked") @RequestParam(value = "should_rank", required = false) Boolean should_rank,
                                      @ApiParam(value = "whether results should be ranked") @RequestParam(value = "shouldRank", required = false) Boolean shouldRank,
                                      @ApiParam(value = "Headers, containing X-Auth-Timestamp, X-Auth-Size and X-Auth-{1..n} fields", required = true) @RequestHeader HttpHeaders httpHeaders) {
        return query(platform_id, platformId, platform_name, platformName, owner, name, id, description, location_name, locationName, location_lat, locationLatitude, location_long, locationLongitude, max_distance, maxDistance, observed_property, observedProperty, null, null, resource_type, resourceType, should_rank, shouldRank, httpHeaders);
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
     * @param resource_type     type of queried resource
     * @param httpHeaders       request headers
     * @return query result as body or null along with appropriate error HTTP status code
     */
    @RequestMapping(method = RequestMethod.GET,
            value = "/query")
    @ApiOperation(value = "Query for resources",
            notes = "Search for resources using defined query parameters",
            response = QueryResponse.class)
    @ApiResponses(value = {
            @ApiResponse(code = 500, message = "Query execution error on server side")})
    public ResponseEntity query(@ApiParam(value = "ID of a platform that resource belongs to") @RequestParam(value = "platform_id", required = false) String platform_id,
                                @ApiParam(value = "ID of a platform that resource belongs to") @RequestParam(value = "platformId", required = false) String platformId,
                                @ApiParam(value = "name of a platform that resource belongs to") @RequestParam(value = "platform_name", required = false) String platform_name,
                                @ApiParam(value = "name of a platform that resource belongs to") @RequestParam(value = "platformName", required = false) String platformName,
                                @ApiParam(value = "owner of a platform that resource belongs to") @RequestParam(value = "owner", required = false) String owner,
                                @ApiParam(value = "name of a resource") @RequestParam(value = "name", required = false) String name,
                                @ApiParam(value = "ID of a resource") @RequestParam(value = "id", required = false) String id,
                                @ApiParam(value = "description of a resource") @RequestParam(value = "description", required = false) String description,
                                @ApiParam(value = "name of resource's location") @RequestParam(value = "location_name", required = false) String location_name,
                                @ApiParam(value = "name of resource's location") @RequestParam(value = "locationName", required = false) String locationName,
                                @ApiParam(value = "latitude of resource's location") @RequestParam(value = "location_lat", required = false) Double location_lat,
                                @ApiParam(value = "latitude of resource's location") @RequestParam(value = "locationLatitude", required = false) Double locationLatitude,
                                @ApiParam(value = "longitude of resource's location") @RequestParam(value = "location_long", required = false) Double location_long,
                                @ApiParam(value = "longitude of resource's location") @RequestParam(value = "locationLongitude", required = false) Double locationLongitude,
                                @ApiParam(value = "maximum radius from specified latitude and longitude to look for resources") @RequestParam(value = "max_distance", required = false) Integer max_distance,
                                @ApiParam(value = "maximum radius from specified latitude and longitude to look for resources") @RequestParam(value = "maxDistance", required = false) Integer maxDistance,
                                @ApiParam(value = "recource's observed property; can be passed multiple times (acts as AND)") @RequestParam(value = "observed_property", required = false) String[] observed_property,
                                @ApiParam(value = "recource's observed property; can be passed multiple times (acts as AND)") @RequestParam(value = "observedProperty", required = false) String[] observedProperty,
                                @ApiParam(value = "recource's observed property by using full IRI; can be passed multiple times (acts as AND)") @RequestParam(value = "observed_property_iri", required = false) String[] observed_property_iri,
                                @ApiParam(value = "recource's observed property by using full IRI; can be passed multiple times (acts as AND)") @RequestParam(value = "observedPropertyIri", required = false) String[] observedPropertyIri,
                                @ApiParam(value = "type of a resource") @RequestParam(value = "resource_type", required = false) String resource_type,
                                @ApiParam(value = "type of a resource") @RequestParam(value = "resourceType", required = false) String resourceType,
                                @ApiParam(value = "whether results should be ranked") @RequestParam(value = "should_rank", required = false) Boolean should_rank,
                                @ApiParam(value = "whether results should be ranked") @RequestParam(value = "shouldRank", required = false) Boolean shouldRank,
                                @ApiParam(value = "Headers, containing X-Auth-Timestamp, X-Auth-Size and X-Auth-{1..n} fields", required = true) @RequestHeader HttpHeaders httpHeaders) {

        try {
            long in = System.currentTimeMillis();
            if (httpHeaders == null)
                throw new InvalidArgumentsException();
            SecurityRequest securityRequest = new SecurityRequest(httpHeaders.toSingleValueMap());

            CoreQueryRequest queryRequest = new CoreQueryRequest();
            queryRequest.setPlatform_id(platformId != null ? platformId : platform_id);
            queryRequest.setPlatform_name(platformName != null ? platformName : platform_name);
            queryRequest.setOwner(owner);
            queryRequest.setName(name);
            queryRequest.setId(id);
            queryRequest.setDescription(description);
            queryRequest.setLocation_name(locationName != null ? locationName : location_name);
            queryRequest.setLocation_lat(locationLatitude != null ? locationLatitude : location_lat);
            queryRequest.setLocation_long(locationLongitude != null ? locationLongitude : location_long);
            queryRequest.setMax_distance(maxDistance != null ? maxDistance : max_distance);
            queryRequest.setResource_type(resourceType != null ? resourceType : resource_type);
            queryRequest.setShould_rank(shouldRank != null ? shouldRank : should_rank);
            queryRequest.setSecurityRequest(securityRequest);

            if (observedProperty != null) {
                queryRequest.setObserved_property(Arrays.asList(observedProperty).stream().map(s -> decodeUrlParameters(s)).collect(Collectors.toList()));
            } else if (observed_property != null) {
                queryRequest.setObserved_property(Arrays.asList(observed_property).stream().map(s -> decodeUrlParameters(s)).collect(Collectors.toList()));
            }


            if (observedPropertyIri != null) {
                queryRequest.setObserved_property_iri(Arrays.asList(observedPropertyIri).stream().map(s -> decodeUrlParameters(s)).collect(Collectors.toList()));
            } else if (observedPropertyIri != null) {
                queryRequest.setObserved_property_iri(Arrays.asList(observed_property_iri).stream().map(s -> decodeUrlParameters(s)).collect(Collectors.toList()));
            }

            QueryResponse resources = this.rabbitManager.sendSearchRequest(queryRequest);
            if (resources == null) {
                return new ResponseEntity<>(new QueryResponse(HttpStatus.GATEWAY_TIMEOUT.value(), ERROR_GATEWAY_TIMEOUT, null), getServiceResponseHeaders(resources), HttpStatus.GATEWAY_TIMEOUT);
            }

            log.debug("Returning search query in total " + (System.currentTimeMillis() - in) + " ms.");
            return new ResponseEntity<>(resources, getServiceResponseHeaders(resources), HttpStatus.valueOf(resources.getStatus()));
        } catch (InvalidArgumentsException e) {
            return handleBadSecurityHeaders(e);
        }
    }

    @Deprecated
    @RequestMapping(method = RequestMethod.POST,
            value = LEGACY_URI_PREFIX + "/sparqlQuery")
    public ResponseEntity legacySparqlQuery(@ApiParam(name = "Sparql query", value = "Sparql query with desired response format") @RequestBody SparqlQueryRequest sparqlQuery,
                                            @ApiParam(value = "Headers, containing X-Auth-Timestamp, X-Auth-Size and X-Auth-{1..n} fields", required = true) @RequestHeader HttpHeaders httpHeaders) {
        return sparqlQuery(sparqlQuery, httpHeaders);
    }

    /**
     * Endpoint for querying registered resources using SPARQL.
     * <p>
     * After receiving query results, user (or application) may choose interesting resources to contact, but it does not
     * have any means of communicate with resources' Interworking Interface. Therefore, it needs to send another request
     * querying for URLs Interworking Services of resources of specified IDs.
     *
     * @param sparqlQuery query object containing sparql query and output format to get results in
     * @param httpHeaders request headers
     */
    @ApiOperation(value = "Sparql query for resources",
            notes = "Search for resources using sparql query",
            response = String.class)
    @ApiResponses(value = {
            @ApiResponse(code = 500, message = "Query execution error on server side")})
    @RequestMapping(method = RequestMethod.POST,
            value = "/sparqlQuery")
    public ResponseEntity sparqlQuery(@ApiParam(name = "Sparql query", value = "Sparql query with desired response format") @RequestBody SparqlQueryRequest sparqlQuery,
                                      @ApiParam(value = "Headers, containing X-Auth-Timestamp, X-Auth-Size and X-Auth-{1..n} fields", required = true) @RequestHeader HttpHeaders httpHeaders) {
        try {
            if (httpHeaders == null)
                throw new InvalidArgumentsException();
            SecurityRequest securityRequest = new SecurityRequest(httpHeaders.toSingleValueMap());

            CoreSparqlQueryRequest request = new CoreSparqlQueryRequest();
            request.setSecurityRequest(securityRequest);
            request.setBody(sparqlQuery.getSparqlQuery());
            request.setOutputFormat(sparqlQuery.getOutputFormat());
            SparqlQueryResponse sparqlQueryResponse = this.rabbitManager.sendSparqlSearchRequest(request);
            if (sparqlQueryResponse == null) {
                return new ResponseEntity<>(new SparqlQueryResponse(HttpStatus.GATEWAY_TIMEOUT.value(), ERROR_GATEWAY_TIMEOUT, null), getServiceResponseHeaders(sparqlQueryResponse), HttpStatus.GATEWAY_TIMEOUT);
            }

            return new ResponseEntity<>(sparqlQueryResponse, getServiceResponseHeaders(sparqlQueryResponse), HttpStatus.valueOf(sparqlQueryResponse.getStatus()));
        } catch (InvalidArgumentsException e) {
            return handleBadSecurityHeaders(e);
        }
    }


    /* -------------------------------------------- */
    /*        CORE RESOURCE ACCESS MONITOR          */
    /* -------------------------------------------- */

    @Deprecated
    @RequestMapping(method = RequestMethod.GET,
            value = LEGACY_URI_PREFIX + "/resourceUrls")
    public ResponseEntity legacyGetResourceUrls(@ApiParam(value = "Resource ID; can be passed multiple times to serve multiple resources at once", required = true) @RequestParam("id") String[] resourceId,
                                                @ApiParam(value = "Headers, containing X-Auth-Timestamp, X-Auth-Size and X-Auth-{1..n} fields", required = true) @RequestHeader HttpHeaders httpHeaders) {
        return getResourceUrls(resourceId, httpHeaders);
    }

    /**
     * Endpoint for querying URL of resources' Interworking Interface.
     * <p>
     * After receiving query results, user (or application) may choose interesting resources to contact, but it does not
     * have any means of communicate with resources' Interworking Interface. Therefore, it needs to send another request
     * querying for URLs Interworking Services of resources of specified IDs.
     *
     * @param resourceId  ID of a resource to get Interworking Interface URL; multiple IDs can be passed
     * @param httpHeaders request headers
     * @return map containing entries in a form of {"resourceId1":"InterworkingInterfaceUrl1", "resourceId2":"InterworkingInterface2", ... }
     */
    @ApiOperation(value = "Get resources' URLs",
            notes = "Gets URLs of resources specified by passed IDs"
    )
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Returns requested URLs in a form of {\"id1\":\"url1\",\"id2\":\"url2\" ... }", response = String.class, responseContainer = "Map"),
            @ApiResponse(code = 500, message = "Query execution error on server side")

    })
    @RequestMapping(method = RequestMethod.GET,
            value = "/resourceUrls")
    public ResponseEntity getResourceUrls(@ApiParam(value = "Resource ID; can be passed multiple times to serve multiple resources at once", required = true) @RequestParam("id") String[] resourceId,
                                          @ApiParam(value = "Headers, containing X-Auth-Timestamp, X-Auth-Size and X-Auth-{1..n} fields", required = true) @RequestHeader HttpHeaders httpHeaders) {
        try {
            if (httpHeaders == null)
                throw new InvalidArgumentsException();
            SecurityRequest securityRequest = new SecurityRequest(httpHeaders.toSingleValueMap());

            ResourceUrlsRequest request = new ResourceUrlsRequest();
            request.setBody(Arrays.asList(resourceId));
            request.setSecurityRequest(securityRequest);

            ResourceUrlsResponse response = this.rabbitManager.sendResourceUrlsRequest(request);
            if (response == null) {
                return new ResponseEntity<>(new ResourceUrlsResponse(HttpStatus.GATEWAY_TIMEOUT.value(), ERROR_GATEWAY_TIMEOUT, null), getServiceResponseHeaders(response), HttpStatus.GATEWAY_TIMEOUT);
            }

            return new ResponseEntity<>(response, getServiceResponseHeaders(response), HttpStatus.valueOf(response.getStatus()));
        } catch (InvalidArgumentsException e) {
            return handleBadSecurityHeaders(e);
        }
    }

    private HttpHeaders getServiceResponseHeaders(AbstractResponseSecured response) {
        HttpHeaders headers = new HttpHeaders();
        if (response != null && response.getServiceResponse() != null) {
            headers.put(SecurityConstants.SECURITY_RESPONSE_HEADER, Arrays.asList(response.getServiceResponse()));
        }
        return headers;
    }

    /* -------------------------------------------- */
    /*    AUTHENTICATION AUTHORIZATION MANAGER      */
    /* -------------------------------------------- */

    @Deprecated
    @RequestMapping(method = RequestMethod.GET,
            value = LEGACY_URI_PREFIX + SecurityConstants.AAM_GET_AVAILABLE_AAMS)
    public ResponseEntity legacyGetAvailableAAMs() {
        return getAvailableAAMs();
    }

    @Deprecated
    @RequestMapping(method = RequestMethod.GET,
            value = SecurityConstants.AAM_GET_AVAILABLE_AAMS)
    public ResponseEntity legacy2GetAvailableAAMs() {
        return getAvailableAAMs();
    }

    /**
     * Endpoint for listing available AAM instances.
     *
     * @return list of available AAM instances
     */
    @ApiOperation(value = "Get available AAMs",
            notes = "Get list of available AAM instances"
    )
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK", response = AvailableAAMsCollection.class),
            @ApiResponse(code = 500, message = "Error on server side")})
    @RequestMapping(method = RequestMethod.GET,
            value = AAM_PREFIX + SecurityConstants.AAM_GET_AVAILABLE_AAMS)
    public ResponseEntity getAvailableAAMs() {
        log.debug("Get Available AAMS request");
        try {
            ResponseEntity<String> entity = this.restTemplate.getForEntity(this.aamUrl + SecurityConstants.AAM_GET_AVAILABLE_AAMS, String.class);

            HttpHeaders headers = stripTransferEncoding(entity.getHeaders());

            return new ResponseEntity<>(entity.getBody(), headers, entity.getStatusCode());
        } catch (HttpStatusCodeException e) {
            log.info(ERROR_PROXY_STATUS_MSG + e.getStatusCode());
            log.debug(e);
            return new ResponseEntity<>(e.getResponseBodyAsString(), e.getStatusCode());
        }
    }

    @Deprecated
    @RequestMapping(method = RequestMethod.GET,
            value = LEGACY_URI_PREFIX + SecurityConstants.AAM_GET_COMPONENT_CERTIFICATE + "/platform/{platformIdentifier}/component/{componentIdentifier}")
    public ResponseEntity legacyGetComponentCertificate(@ApiParam(value = "Component identifier", required = true) @PathVariable String componentIdentifier,
                                                        @ApiParam(value = "Platform identifier", required = true) @PathVariable String platformIdentifier) {
        return getComponentCertificate(componentIdentifier, platformIdentifier);
    }

    @Deprecated
    @RequestMapping(method = RequestMethod.GET,
            value = SecurityConstants.AAM_GET_COMPONENT_CERTIFICATE + "/platform/{platformIdentifier}/component/{componentIdentifier}")
    public ResponseEntity legacy2GetComponentCertificate(@ApiParam(value = "Component identifier", required = true) @PathVariable String componentIdentifier,
                                                         @ApiParam(value = "Platform identifier", required = true) @PathVariable String platformIdentifier) {
        return getComponentCertificate(componentIdentifier, platformIdentifier);
    }

    /**
     * Endpoint for getting component certificate.
     *
     * @param componentIdentifier component identifier
     * @param platformIdentifier  platform identifier
     * @return component certificate
     */
    @ApiOperation(value = "Get component certificate",
            notes = "Get component certificate"
    )
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK", response = String.class, responseContainer = "List"),
            @ApiResponse(code = 500, message = "Error on server side")})
    @RequestMapping(method = RequestMethod.GET,
            value = AAM_PREFIX + SecurityConstants.AAM_GET_COMPONENT_CERTIFICATE + "/platform/{platformIdentifier}/component/{componentIdentifier}")
    public ResponseEntity getComponentCertificate(@ApiParam(value = "Component identifier", required = true) @PathVariable String componentIdentifier,
                                                  @ApiParam(value = "Platform identifier", required = true) @PathVariable String platformIdentifier) {
        log.debug("Get component certificate request");
        try {
            ResponseEntity<String> entity = this.restTemplate.getForEntity(this.aamUrl + SecurityConstants.AAM_GET_COMPONENT_CERTIFICATE + "/platform/" + platformIdentifier + "/component/" + componentIdentifier, String.class);

            HttpHeaders headers = stripTransferEncoding(entity.getHeaders());

            return new ResponseEntity<>(entity.getBody(), headers, entity.getStatusCode());
        } catch (HttpStatusCodeException e) {
            log.info(ERROR_PROXY_STATUS_MSG + e.getStatusCode());
            log.debug(e);
            return new ResponseEntity<>(e.getResponseBodyAsString(), e.getStatusCode());
        }
    }

    @Deprecated
    @RequestMapping(method = RequestMethod.POST,
            value = LEGACY_URI_PREFIX + SecurityConstants.AAM_SIGN_CERTIFICATE_REQUEST)
    public ResponseEntity legacySignCertificateRequest(@ApiParam(value = "Certificate request", required = true) @RequestBody CertificateRequest certificateRequest) {
        return signCertificateRequest(certificateRequest);
    }

    @Deprecated
    @RequestMapping(method = RequestMethod.POST,
            value = SecurityConstants.AAM_SIGN_CERTIFICATE_REQUEST)
    public ResponseEntity legacy2SignCertificateRequest(@ApiParam(value = "Certificate request", required = true) @RequestBody CertificateRequest certificateRequest) {
        return signCertificateRequest(certificateRequest);
    }

    /**
     * Endpoint for getting client certificate
     *
     * @param certificateRequest certificate request
     * @return client certificate
     */
    @ApiOperation(value = "Get client certificate",
            notes = "Get client certificate",
            response = String.class
    )
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK", response = String.class),
            @ApiResponse(code = 500, message = "Error on server side")})
    @RequestMapping(method = RequestMethod.POST,
            value = AAM_PREFIX + SecurityConstants.AAM_SIGN_CERTIFICATE_REQUEST)
    public ResponseEntity signCertificateRequest(@ApiParam(value = "Certificate request", required = true) @RequestBody CertificateRequest certificateRequest) {
        log.debug("Get client certificate");
        try {
            HttpEntity<CertificateRequest> entity = new HttpEntity<>(certificateRequest, null);

            ResponseEntity<String> stringResponseEntity = this.restTemplate.postForEntity(this.aamUrl + SecurityConstants.AAM_SIGN_CERTIFICATE_REQUEST, entity, String.class);

            HttpHeaders headers = stripTransferEncoding(stringResponseEntity.getHeaders());

            return new ResponseEntity<>(stringResponseEntity.getBody(), headers, stringResponseEntity.getStatusCode());
        } catch (HttpStatusCodeException e) {
            log.info(ERROR_PROXY_STATUS_MSG + e.getStatusCode());
            log.debug(e);
            return new ResponseEntity<>(e.getResponseBodyAsString(), e.getStatusCode());
        }
    }

    @Deprecated
    @RequestMapping(method = RequestMethod.POST,
            value = LEGACY_URI_PREFIX + SecurityConstants.AAM_REVOKE_CREDENTIALS)
    public ResponseEntity legacyRevokeCredentials(@ApiParam(value = "Revocation request", required = true) @RequestBody RevocationRequest revocationRequest) {
        return revokeCredentials(revocationRequest);
    }

    @Deprecated
    @RequestMapping(method = RequestMethod.POST,
            value = SecurityConstants.AAM_REVOKE_CREDENTIALS)
    public ResponseEntity legacy2RevokeCredentials(@ApiParam(value = "Revocation request", required = true) @RequestBody RevocationRequest revocationRequest) {
        return revokeCredentials(revocationRequest);
    }

    /**
     * Endpoint for revoking token
     *
     * @param revocationRequest revocation request
     * @return status of revocation
     */
    @ApiOperation(value = "Revoke token",
            notes = "Revoke token",
            response = String.class
    )
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK", response = String.class),
            @ApiResponse(code = 500, message = "Error on server side")})
    @RequestMapping(method = RequestMethod.POST,
            value = AAM_PREFIX + SecurityConstants.AAM_REVOKE_CREDENTIALS)
    public ResponseEntity revokeCredentials(@ApiParam(value = "Revocation request", required = true) @RequestBody RevocationRequest revocationRequest) {
        log.debug("Revoke token");
        try {
            HttpEntity<RevocationRequest> entity = new HttpEntity<>(revocationRequest, null);

            ResponseEntity<String> stringResponseEntity = this.restTemplate.postForEntity(this.aamUrl + SecurityConstants.AAM_REVOKE_CREDENTIALS, entity, String.class);

            HttpHeaders headers = stripTransferEncoding(stringResponseEntity.getHeaders());

            return new ResponseEntity<>(stringResponseEntity.getBody(), headers, stringResponseEntity.getStatusCode());
        } catch (HttpStatusCodeException e) {
            log.info(ERROR_PROXY_STATUS_MSG + e.getStatusCode());
            log.debug(e);
            return new ResponseEntity<>(e.getResponseBodyAsString(), e.getStatusCode());
        }
    }

    @Deprecated
    @RequestMapping(method = RequestMethod.POST,
            value = LEGACY_URI_PREFIX + SecurityConstants.AAM_GET_GUEST_TOKEN)
    public ResponseEntity legacyGetGuestToken() {
        return getGuestToken();
    }

    @Deprecated
    @RequestMapping(method = RequestMethod.POST,
            value = SecurityConstants.AAM_GET_GUEST_TOKEN)
    public ResponseEntity legacy2GetGuestToken() {
        return getGuestToken();
    }

    /**
     * Endpoint for getting guest token
     *
     * @return guest token
     */
    @ApiOperation(value = "Get guest token",
            notes = "Get guest token",
            response = String.class
    )
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK", response = String.class),
            @ApiResponse(code = 500, message = "Error on server side")})
    @CrossOrigin(exposedHeaders = {"x-auth-token"})
    @RequestMapping(method = RequestMethod.POST,
            value = AAM_PREFIX + SecurityConstants.AAM_GET_GUEST_TOKEN)
    public ResponseEntity getGuestToken() {
        log.debug("Get guest token");
        try {
            ResponseEntity<String> stringResponseEntity = this.restTemplate.postForEntity(this.aamUrl + SecurityConstants.AAM_GET_GUEST_TOKEN, null, String.class);

            HttpHeaders headers = stripTransferEncoding(stringResponseEntity.getHeaders());

            return new ResponseEntity<>(stringResponseEntity.getBody(), headers, stringResponseEntity.getStatusCode());
        } catch (HttpStatusCodeException e) {
            log.info(ERROR_PROXY_STATUS_MSG + e.getStatusCode());
            log.debug(e);
            return new ResponseEntity<>(e.getResponseBodyAsString(), e.getStatusCode());
        }
    }

    @Deprecated
    @RequestMapping(method = RequestMethod.POST,
            value = LEGACY_URI_PREFIX + SecurityConstants.AAM_GET_HOME_TOKEN)
    public ResponseEntity legacyGetHomeToken(@ApiParam(value = "Login request", required = true) @RequestHeader(SecurityConstants.TOKEN_HEADER_NAME) String loginRequest) {
        return getHomeToken(loginRequest);
    }

    @Deprecated
    @RequestMapping(method = RequestMethod.POST,
            value = SecurityConstants.AAM_GET_HOME_TOKEN)
    public ResponseEntity legacy2GetHomeToken(@ApiParam(value = "Login request", required = true) @RequestHeader(SecurityConstants.TOKEN_HEADER_NAME) String loginRequest) {
        return getHomeToken(loginRequest);
    }

    /**
     * Endpoint for getting home token
     *
     * @param loginRequest login request
     * @return home token
     */
    @ApiOperation(value = "Get home token",
            notes = "Get home token",
            response = String.class
    )
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK", response = String.class),
            @ApiResponse(code = 500, message = "Error on server side")})
    @RequestMapping(method = RequestMethod.POST,
            value = AAM_PREFIX + SecurityConstants.AAM_GET_HOME_TOKEN)
    public ResponseEntity getHomeToken(@ApiParam(value = "Login request", required = true) @RequestHeader(SecurityConstants.TOKEN_HEADER_NAME) String loginRequest) {
        log.debug("Get home token");
        try {
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.add(SecurityConstants.TOKEN_HEADER_NAME, loginRequest);
            HttpEntity<String> entity = new HttpEntity<>(null, httpHeaders);

            ResponseEntity<String> stringResponseEntity = this.restTemplate.postForEntity(this.aamUrl + SecurityConstants.AAM_GET_HOME_TOKEN, entity, String.class);

            HttpHeaders headers = stripTransferEncoding(stringResponseEntity.getHeaders());

            return new ResponseEntity<>(stringResponseEntity.getBody(), headers, stringResponseEntity.getStatusCode());
        } catch (HttpStatusCodeException e) {
            log.info(ERROR_PROXY_STATUS_MSG + e.getStatusCode());
            log.debug(e);
            return new ResponseEntity<>(e.getResponseBodyAsString(), e.getStatusCode());
        }
    }

    @Deprecated
    @RequestMapping(method = RequestMethod.POST,
            value = LEGACY_URI_PREFIX + SecurityConstants.AAM_GET_FOREIGN_TOKEN)
    public ResponseEntity legacyGetForeignToken(@ApiParam(value = "Remote home token", required = true) @RequestHeader(SecurityConstants.TOKEN_HEADER_NAME) String remoteHomeToken,
                                                @ApiParam(value = "Client certificate") @RequestHeader(name = SecurityConstants.CLIENT_CERTIFICATE_HEADER_NAME, defaultValue = "") String clientCertificate,
                                                @ApiParam(value = "AAM certificate") @RequestHeader(name = SecurityConstants.AAM_CERTIFICATE_HEADER_NAME, defaultValue = "") String aamCertificate) {
        return getForeignToken(remoteHomeToken, clientCertificate, aamCertificate);
    }

    @Deprecated
    @RequestMapping(method = RequestMethod.POST,
            value = SecurityConstants.AAM_GET_FOREIGN_TOKEN)
    public ResponseEntity legacy2GetForeignToken(@ApiParam(value = "Remote home token", required = true) @RequestHeader(SecurityConstants.TOKEN_HEADER_NAME) String remoteHomeToken,
                                                 @ApiParam(value = "Client certificate") @RequestHeader(name = SecurityConstants.CLIENT_CERTIFICATE_HEADER_NAME, defaultValue = "") String clientCertificate,
                                                 @ApiParam(value = "AAM certificate") @RequestHeader(name = SecurityConstants.AAM_CERTIFICATE_HEADER_NAME, defaultValue = "") String aamCertificate) {
        return getForeignToken(remoteHomeToken, clientCertificate, aamCertificate);
    }


    /**
     * Endpoint for getting foreign token
     *
     * @param remoteHomeToken   remote home token
     * @param clientCertificate client certificate
     * @param aamCertificate    AAM certificate
     * @return foreign token
     */
    @ApiOperation(value = "Get foreign token",
            notes = "Get foreign token",
            response = String.class
    )
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK", response = String.class),
            @ApiResponse(code = 500, message = "Error on server side")})
    @RequestMapping(method = RequestMethod.POST,
            value = AAM_PREFIX + SecurityConstants.AAM_GET_FOREIGN_TOKEN)
    public ResponseEntity getForeignToken(@ApiParam(value = "Remote home token", required = true) @RequestHeader(SecurityConstants.TOKEN_HEADER_NAME) String remoteHomeToken,
                                          @ApiParam(value = "Client certificate") @RequestHeader(name = SecurityConstants.CLIENT_CERTIFICATE_HEADER_NAME, defaultValue = "") String clientCertificate,
                                          @ApiParam(value = "AAM certificate") @RequestHeader(name = SecurityConstants.AAM_CERTIFICATE_HEADER_NAME, defaultValue = "") String aamCertificate) {
        log.debug("Get foreign token");
        try {
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.add(SecurityConstants.TOKEN_HEADER_NAME, remoteHomeToken);
            httpHeaders.add(SecurityConstants.CLIENT_CERTIFICATE_HEADER_NAME, clientCertificate);
            httpHeaders.add(SecurityConstants.AAM_CERTIFICATE_HEADER_NAME, aamCertificate);
            HttpEntity<String> entity = new HttpEntity<>(null, httpHeaders);

            ResponseEntity<String> stringResponseEntity = this.restTemplate.postForEntity(this.aamUrl + SecurityConstants.AAM_GET_FOREIGN_TOKEN, entity, String.class);

            HttpHeaders headers = stripTransferEncoding(stringResponseEntity.getHeaders());

            return new ResponseEntity<>(stringResponseEntity.getBody(), headers, stringResponseEntity.getStatusCode());
        } catch (HttpStatusCodeException e) {
            log.info(ERROR_PROXY_STATUS_MSG + e.getStatusCode());
            log.debug(e);
            return new ResponseEntity<>(e.getResponseBodyAsString(), e.getStatusCode());
        }
    }

    @Deprecated
    @RequestMapping(method = RequestMethod.POST,
            value = LEGACY_URI_PREFIX + SecurityConstants.AAM_VALIDATE_CREDENTIALS)
    public ResponseEntity legacyValidateCredentials(@ApiParam(value = "Token", required = true) @RequestHeader(SecurityConstants.TOKEN_HEADER_NAME) String token,
                                                    @ApiParam(value = "Client certificate") @RequestHeader(name = SecurityConstants.CLIENT_CERTIFICATE_HEADER_NAME, defaultValue = "") String clientCertificate,
                                                    @ApiParam(value = "AAM certificate") @RequestHeader(name = SecurityConstants.AAM_CERTIFICATE_HEADER_NAME, defaultValue = "") String clientCertificateSigningAAMCertificate,
                                                    @ApiParam(value = "Foreign token") @RequestHeader(name = SecurityConstants.FOREIGN_TOKEN_ISSUING_AAM_CERTIFICATE, defaultValue = "") String foreignTokenIssuingAAMCertificate) {
        return validateCredentials(token, clientCertificate, clientCertificateSigningAAMCertificate, foreignTokenIssuingAAMCertificate);
    }

    @Deprecated
    @RequestMapping(method = RequestMethod.POST,
            value = SecurityConstants.AAM_VALIDATE_CREDENTIALS)
    public ResponseEntity legacy2ValidateCredentials(@ApiParam(value = "Token", required = true) @RequestHeader(SecurityConstants.TOKEN_HEADER_NAME) String token,
                                                     @ApiParam(value = "Client certificate") @RequestHeader(name = SecurityConstants.CLIENT_CERTIFICATE_HEADER_NAME, defaultValue = "") String clientCertificate,
                                                     @ApiParam(value = "AAM certificate") @RequestHeader(name = SecurityConstants.AAM_CERTIFICATE_HEADER_NAME, defaultValue = "") String clientCertificateSigningAAMCertificate,
                                                     @ApiParam(value = "Foreign token") @RequestHeader(name = SecurityConstants.FOREIGN_TOKEN_ISSUING_AAM_CERTIFICATE, defaultValue = "") String foreignTokenIssuingAAMCertificate) {
        return validateCredentials(token, clientCertificate, clientCertificateSigningAAMCertificate, foreignTokenIssuingAAMCertificate);
    }

    /**
     * Endpoint for validating tokens and certificates
     *
     * @param token                                  token
     * @param clientCertificate                      client certificate
     * @param clientCertificateSigningAAMCertificate AAM certificate
     * @param foreignTokenIssuingAAMCertificate      foreign token
     * @return validation status
     */
    @ApiOperation(value = "Validate tokens and certificates",
            notes = "Validate tokens and certificates",
            response = ValidationStatus.class
    )
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK", response = ValidationStatus.class),
            @ApiResponse(code = 500, message = "Error on server side")})
    @RequestMapping(method = RequestMethod.POST,
            value = AAM_PREFIX + SecurityConstants.AAM_VALIDATE_CREDENTIALS)
    public ResponseEntity validateCredentials(@ApiParam(value = "Token", required = true) @RequestHeader(SecurityConstants.TOKEN_HEADER_NAME) String token,
                                              @ApiParam(value = "Client certificate") @RequestHeader(name = SecurityConstants.CLIENT_CERTIFICATE_HEADER_NAME, defaultValue = "") String clientCertificate,
                                              @ApiParam(value = "AAM certificate") @RequestHeader(name = SecurityConstants.AAM_CERTIFICATE_HEADER_NAME, defaultValue = "") String clientCertificateSigningAAMCertificate,
                                              @ApiParam(value = "Foreign token") @RequestHeader(name = SecurityConstants.FOREIGN_TOKEN_ISSUING_AAM_CERTIFICATE, defaultValue = "") String foreignTokenIssuingAAMCertificate) {
        log.debug("Validate token/certificate");
        try {
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.add(SecurityConstants.TOKEN_HEADER_NAME, token);
            httpHeaders.add(SecurityConstants.CLIENT_CERTIFICATE_HEADER_NAME, clientCertificate);
            httpHeaders.add(SecurityConstants.AAM_CERTIFICATE_HEADER_NAME, clientCertificateSigningAAMCertificate);
            httpHeaders.add(SecurityConstants.FOREIGN_TOKEN_ISSUING_AAM_CERTIFICATE, foreignTokenIssuingAAMCertificate);
            HttpEntity<String> entity = new HttpEntity<>(null, httpHeaders);

            ResponseEntity<String> stringResponseEntity = this.restTemplate.postForEntity(this.aamUrl + SecurityConstants.AAM_VALIDATE_CREDENTIALS, entity, String.class);

            HttpHeaders headers = stripTransferEncoding(stringResponseEntity.getHeaders());

            return new ResponseEntity<>(stringResponseEntity.getBody(), headers, stringResponseEntity.getStatusCode());
        } catch (HttpStatusCodeException e) {
            log.info(ERROR_PROXY_STATUS_MSG + e.getStatusCode());
            log.debug(e);
            return new ResponseEntity<>(e.getResponseBodyAsString(), e.getStatusCode());
        }
    }

    @Deprecated
    @RequestMapping(method = RequestMethod.POST,
            value = LEGACY_URI_PREFIX + SecurityConstants.AAM_GET_USER_DETAILS)
    public ResponseEntity legacyGetUserDetails(@ApiParam(value = "User credentials", required = true) @RequestBody Credentials credentials) {
        return getUserDetails(credentials);
    }

    @Deprecated
    @RequestMapping(method = RequestMethod.POST,
            value = SecurityConstants.AAM_GET_USER_DETAILS)
    public ResponseEntity legacy2GetUserDetails(@ApiParam(value = "User credentials", required = true) @RequestBody Credentials credentials) {
        return getUserDetails(credentials);
    }

    /**
     * Endpoint for getting user details
     *
     * @return user details
     */
    @ApiOperation(value = "Get user details",
            notes = "Get user details",
            response = UserDetails.class
    )
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK", response = UserDetails.class),
            @ApiResponse(code = 500, message = "Error on server side")})
    @RequestMapping(method = RequestMethod.POST,
            value = AAM_PREFIX + SecurityConstants.AAM_GET_USER_DETAILS)
    public ResponseEntity getUserDetails(@ApiParam(value = "User credentials", required = true) @RequestBody Credentials credentials) {
        log.debug("Get user details");
        try {
            HttpEntity<Credentials> entity = new HttpEntity<>(credentials, null);

            ResponseEntity<String> stringResponseEntity = this.restTemplate.postForEntity(this.aamUrl + SecurityConstants.AAM_GET_USER_DETAILS, entity, String.class);

            HttpHeaders headers = stripTransferEncoding(stringResponseEntity.getHeaders());

            return new ResponseEntity<>(stringResponseEntity.getBody(), headers, stringResponseEntity.getStatusCode());
        } catch (HttpStatusCodeException e) {
            log.info(ERROR_PROXY_STATUS_MSG + e.getStatusCode());
            log.debug(e);
            return new ResponseEntity<>(e.getResponseBodyAsString(), e.getStatusCode());
        }
    }

    /* -------------------------------------------- */
    /*          ANOMALY DETECTION MODULE            */
    /* -------------------------------------------- */

    /**
     * Endpoint for reporting failed federation authorization
     *
     * @param failedFederationAuthorizationReport failed federation authorization report
     * @return operation result
     */
    @ApiOperation(value = "TODO",
            notes = "TODO",
            response = String.class
    )
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 500, message = "Error on server side")})
    @RequestMapping(method = RequestMethod.POST,
            value = SecurityConstants.ADM_PREFIX + SecurityConstants.ADM_LOG_FAILED_FEDERATION_AUTHORIZATION)
    public ResponseEntity handleFailFederationAuthorizationReport(@ApiParam(value = "Failed federation authorization report", required = true) @RequestBody FailedFederationAuthorizationReport failedFederationAuthorizationReport) {
        log.debug("Handle fail federation authorization report");
        try {
            ResponseEntity<String> stringResponseEntity = this.restTemplate.postForEntity(this.admUrl + SecurityConstants.ADM_LOG_FAILED_FEDERATION_AUTHORIZATION, failedFederationAuthorizationReport, String.class);

            HttpHeaders headers = stripTransferEncoding(stringResponseEntity.getHeaders());

            return new ResponseEntity<>(stringResponseEntity.getBody(), headers, stringResponseEntity.getStatusCode());
        } catch (HttpStatusCodeException e) {
            log.info(ERROR_PROXY_STATUS_MSG + e.getStatusCode());
            log.debug(e);
            return new ResponseEntity<>(e.getResponseBodyAsString(), e.getStatusCode());
        }
    }

    /**
     * TODO
     */
    @ApiOperation(value = "TODO",
            notes = "TODO",
            response = String.class
    )
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 500, message = "Error on server side")})
    @RequestMapping(method = RequestMethod.GET,
            value = SecurityConstants.ADM_PREFIX + SecurityConstants.ADM_GET_FEDERATED_MISDEEDS + "/bySearchOriginPlatform")
    public ResponseEntity getMisdeedsGroupedByPlatform(@ApiParam(value = "Headers", required = true) @RequestHeader HttpHeaders httpHeaders,
                                                       @ApiParam(value = "Platform ID") @RequestParam(name = "platformId", required = false) String platformIdFilter,
                                                       @ApiParam(value = "Search origin platform ID") @RequestParam(name = "searchOriginPlatformId", required = false) String singleSearchOriginPlatformFilter) {
        log.debug("Get misdeeds group by platform");
        try {
            HttpEntity<String> entity = new HttpEntity<>(null, httpHeaders);

            Map<String, String> params = new HashMap<>();
            if (platformIdFilter != null)
                params.put("platformId", platformIdFilter);
            if (singleSearchOriginPlatformFilter != null)
                params.put("searchOriginPlatformId", singleSearchOriginPlatformFilter);

            ResponseEntity<String> stringResponseEntity = this.restTemplate.exchange(this.admUrl + SecurityConstants.ADM_GET_FEDERATED_MISDEEDS + "/bySearchOriginPlatform", HttpMethod.GET, entity, String.class, params);

            HttpHeaders headers = stripTransferEncoding(stringResponseEntity.getHeaders());

            return new ResponseEntity<>(stringResponseEntity.getBody(), headers, stringResponseEntity.getStatusCode());
        } catch (HttpStatusCodeException e) {
            log.info(ERROR_PROXY_STATUS_MSG + e.getStatusCode());
            log.debug(e);
            return new ResponseEntity<>(e.getResponseBodyAsString(), e.getStatusCode());
        }
    }

    /**
     * TODO
     */
    @ApiOperation(value = "TODO",
            notes = "TODO",
            response = String.class
    )
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 500, message = "Error on server side")})
    @RequestMapping(method = RequestMethod.GET,
            value = SecurityConstants.ADM_PREFIX + SecurityConstants.ADM_GET_FEDERATED_MISDEEDS + "/byFederation")
    public ResponseEntity getMisdeedsGroupedByFederation(@ApiParam(value = "Headers", required = true) @RequestHeader HttpHeaders httpHeaders,
                                                         @ApiParam(value = "Platform ID") @RequestParam(name = "platformId", required = false) String platformIdFilter,
                                                         @ApiParam(value = "Federation ID") @RequestParam(name = "federationId", required = false) String federationIdFilter) {
        log.debug("Get misdeeds group by federation");
        try {
            HttpEntity<String> entity = new HttpEntity<>(null, httpHeaders);
            Map<String, String> params = new HashMap<>();
            if (platformIdFilter != null)
                params.put("platformId", platformIdFilter);
            if (federationIdFilter != null)
                params.put("federationId", federationIdFilter);
            ResponseEntity<String> stringResponseEntity = this.restTemplate.exchange(this.admUrl + SecurityConstants.ADM_GET_FEDERATED_MISDEEDS + "/byFederation", HttpMethod.GET, entity, String.class, params);

            HttpHeaders headers = stripTransferEncoding(stringResponseEntity.getHeaders());

            return new ResponseEntity<>(stringResponseEntity.getBody(), headers, stringResponseEntity.getStatusCode());
        } catch (HttpStatusCodeException e) {
            log.info(ERROR_PROXY_STATUS_MSG + e.getStatusCode());
            log.debug(e);
            return new ResponseEntity<>(e.getResponseBodyAsString(), e.getStatusCode());
        }
    }

    /* -------------------------------------------- */
    /*        BARTERING AND TRADING MANAGER         */
    /* -------------------------------------------- */
    @RequestMapping(method = RequestMethod.POST,
            value = BTM_PREFIX + SecurityConstants.BTM_REGISTER_COUPON)
    public ResponseEntity registerCoupon(@RequestHeader HttpHeaders httpHeaders,
                                         @RequestHeader(SecurityConstants.COUPON_HEADER_NAME) String couponString) {
        log.debug("Register coupon");
        try {
            HttpEntity<String> entity = new HttpEntity<>(null, httpHeaders);

            ResponseEntity<String> stringResponseEntity = this.restTemplate.exchange(this.btmUrl + SecurityConstants.BTM_REGISTER_COUPON, HttpMethod.POST, entity, String.class);

            HttpHeaders headers = stripTransferEncoding(stringResponseEntity.getHeaders());

            return new ResponseEntity<>(stringResponseEntity.getBody(), headers, stringResponseEntity.getStatusCode());
        } catch (HttpStatusCodeException e) {
            log.info(ERROR_PROXY_STATUS_MSG + e.getStatusCode());
            log.debug(e);
            return new ResponseEntity<>(e.getResponseBodyAsString(), e.getStatusCode());
        }
    }

    @RequestMapping(method = RequestMethod.POST,
            value = BTM_PREFIX + SecurityConstants.BTM_IS_COUPON_VALID)
    public ResponseEntity isCouponValid(@RequestHeader HttpHeaders httpHeaders,
                                         @RequestHeader(SecurityConstants.COUPON_HEADER_NAME) String couponString) {
        log.debug("Is coupon valid");
        try {
            HttpEntity<String> entity = new HttpEntity<>(null, httpHeaders);

            ResponseEntity<String> stringResponseEntity = this.restTemplate.exchange(this.btmUrl + SecurityConstants.BTM_IS_COUPON_VALID, HttpMethod.POST, entity, String.class);

            HttpHeaders headers = stripTransferEncoding(stringResponseEntity.getHeaders());

            return new ResponseEntity<>(stringResponseEntity.getBody(), headers, stringResponseEntity.getStatusCode());
        } catch (HttpStatusCodeException e) {
            log.info(ERROR_PROXY_STATUS_MSG + e.getStatusCode());
            log.debug(e);
            return new ResponseEntity<>(e.getResponseBodyAsString(), e.getStatusCode());
        }
    }

    @RequestMapping(method = RequestMethod.POST,
            value = BTM_PREFIX + SecurityConstants.BTM_CONSUME_COUPON)
    public ResponseEntity consumeCoupon(@RequestHeader HttpHeaders httpHeaders,
                                         @RequestHeader(SecurityConstants.COUPON_HEADER_NAME) String couponString) {
        log.debug("Consume coupon");
        try {
            HttpEntity<String> entity = new HttpEntity<>(null, httpHeaders);

            ResponseEntity<String> stringResponseEntity = this.restTemplate.exchange(this.btmUrl + SecurityConstants.BTM_CONSUME_COUPON, HttpMethod.POST, entity, String.class);

            HttpHeaders headers = stripTransferEncoding(stringResponseEntity.getHeaders());

            return new ResponseEntity<>(stringResponseEntity.getBody(), headers, stringResponseEntity.getStatusCode());
        } catch (HttpStatusCodeException e) {
            log.info(ERROR_PROXY_STATUS_MSG + e.getStatusCode());
            log.debug(e);
            return new ResponseEntity<>(e.getResponseBodyAsString(), e.getStatusCode());
        }
    }

    @RequestMapping(method = RequestMethod.POST,
            value = BTM_PREFIX + SecurityConstants.BTM_CLEANUP_COUPONS)
    public ResponseEntity cleanupConsumedCoupons(@RequestBody String timestamp) {
        log.debug("Cleanup consumed coupons");
        try {
            HttpEntity<String> entity = new HttpEntity<>(timestamp, null);

            ResponseEntity<String> stringResponseEntity = this.restTemplate.exchange(this.btmUrl + SecurityConstants.BTM_CLEANUP_COUPONS, HttpMethod.POST, entity, String.class);

            HttpHeaders headers = stripTransferEncoding(stringResponseEntity.getHeaders());

            return new ResponseEntity<>(stringResponseEntity.getBody(), headers, stringResponseEntity.getStatusCode());
        } catch (HttpStatusCodeException e) {
            log.info(ERROR_PROXY_STATUS_MSG + e.getStatusCode());
            log.debug(e);
            return new ResponseEntity<>(e.getResponseBodyAsString(), e.getStatusCode());
        }
    }

    @RequestMapping(method = RequestMethod.POST,
            value = BTM_PREFIX + "/couponusage")
    public ResponseEntity couponUsage(@RequestBody String filter) {
        log.debug("Cleanup consumed coupons");
        try {
            HttpEntity<String> entity = new HttpEntity<>(filter, null);

            ResponseEntity<String> stringResponseEntity = this.restTemplate.exchange(this.btmUrl + "/couponusage", HttpMethod.POST, entity, String.class);

            HttpHeaders headers = stripTransferEncoding(stringResponseEntity.getHeaders());

            return new ResponseEntity<>(stringResponseEntity.getBody(), headers, stringResponseEntity.getStatusCode());
        } catch (HttpStatusCodeException e) {
            log.info(ERROR_PROXY_STATUS_MSG + e.getStatusCode());
            log.debug(e);
            return new ResponseEntity<>(e.getResponseBodyAsString(), e.getStatusCode());
        }
    }


    /**
     * Method used to strip 'Transfer-encoding' header and use 'Content-length' instead.
     *
     * @param headers headers to strip 'Transfer-encoding' from
     * @return headers without 'Transfer-encoding' field
     */
    private HttpHeaders stripTransferEncoding(HttpHeaders headers) {
        if (headers == null)
            return null;

        HttpHeaders newHeaders = new HttpHeaders();

        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (!entry.getKey().equals(HttpHeaders.TRANSFER_ENCODING)) {
                for (String value : entry.getValue())
                    newHeaders.add(entry.getKey(), value);
            }

        }

        return newHeaders;
    }

    /**
     * Used to override RestTemplate used in request proxying with a mocked version for unit testing.
     *
     * @param restTemplate
     */
    public void setRestTemplate(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    private String decodeUrlParameters(String s) {
        String result = "";
        try {
            result = URLDecoder.decode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            log.error("Error in decoding: " + e.getMessage(), e);
        }
        return result;
    }


}
