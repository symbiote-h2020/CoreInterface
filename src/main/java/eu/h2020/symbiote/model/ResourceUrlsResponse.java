package eu.h2020.symbiote.model;

import java.util.Map;

/**
 * Created by jawora on 25.01.17.
 */
public class ResourceUrlsResponse {
    private Map<String, String> idMap;

    public ResourceUrlsResponse() {
    }

    public Map<String, String> getIdMap() {
        return idMap;
    }

    public void setIdMap(Map<String, String> idMap) {
        this.idMap = idMap;
    }
}
