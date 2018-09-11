package org.openkilda.testing.service.elastic;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;


/**
 * An interface to the Elastic Search. Set following variables in the kilda.properties to work:
 * elasticsearch.endpoint = URL to Elastic
 * elasticsearch.username = User with read access
 * elasticsearch.password = User's password
 */

@Component
public class ElasticHelperImpl implements ElasticHelper {

    @Autowired
    @Qualifier("elasticSearchRestTemplate")
    private RestTemplate restTemplate;

    /**
     * Inquiry ElasticSearch for logs. Not intended to be used directly.
     * @param uri - Search address and parame
     * @param query - Search query (with headers and ES/Lucene JSON query body)
     * @return HTTP response from ES
     */
    public HashMap getLogs(String uri, HttpEntity query) {
        return restTemplate.exchange(uri, HttpMethod.POST, query, HashMap.class).getBody();
    }
}