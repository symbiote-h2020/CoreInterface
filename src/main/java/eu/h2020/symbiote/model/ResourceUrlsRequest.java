package eu.h2020.symbiote.model;

import java.util.List;

/**
 * Created by jawora on 25.01.17.
 */
public class ResourceUrlsRequest {
    private List<String> idList;

    public ResourceUrlsRequest() {
    }

    public List<String> getIdList() {
        return idList;
    }

    public void setIdList(List<String> idList) {
        this.idList = idList;
    }
}
