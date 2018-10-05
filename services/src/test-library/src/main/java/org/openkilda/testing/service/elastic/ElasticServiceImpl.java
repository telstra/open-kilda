/* Copyright 2018 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.testing.service.elastic;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;


/**
 * An interface to the Elastic Search. Set following variables in the kilda.properties to work:
 * elasticsearch.endpoint = URL to Elastic
 * elasticsearch.username = User with read access
 * elasticsearch.password = User's password
 */

@Component
public class ElasticServiceImpl implements ElasticService {

    @Autowired
    @Qualifier("elasticSearchRestTemplate")
    private RestTemplate restTemplate;

    /**
     * Inquiry ElasticSearch for logs. Not intended to be used directly.
     * @param uri - Search address and parame
     * @param query - Search query (with headers and ES/Lucene JSON query body)
     * @return HTTP response from ES
     */
    public Map getLogs(String uri, HttpEntity query) {
        return restTemplate.exchange(uri, HttpMethod.POST, query, HashMap.class).getBody();
    }
}
