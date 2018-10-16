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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
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
@Slf4j
public class ElasticServiceImpl implements ElasticService {

    @Autowired
    @Qualifier("elasticSearchRestTemplate")
    private RestTemplate restTemplate;

    /**
     * Searches the Elastic Search database for a specific log entries.
     * In case you need to lookup multiple application IDs, tags or log levels, pass parameters as:
     * "VALUE1 OR VALUE2 OR ... OR VALUE_N"
     *
     * @param appId - application ID to lookup (either app_id or tags should be speficied)
     * @param tags - one or more tag, delimited by OR operator, to lookup (either app_id or tags should be specified)
     * @param level - log level (can be null)
     * @param timeRange - search depth (in seconds, set to 0 to search all).
     * @param resultCount - max number of returned documents (100 default)
     * @param defaultField - field in log entry to search at (_source by default)
     * @param index - Elastic Search index to lookup (_all by default)
     *
     * @return HashMap with deserialized JSON otherwise.
     */
    public Map getLogs(String appId, String tags, String level, long timeRange,
                long resultCount, String defaultField, String index) {
        if (("".equals(appId) && "".equals(tags))) {
            throw new IllegalArgumentException("Either app_id or tags should be specified");
        }
        Long time = System.currentTimeMillis();
        String queryString = "";

        if (!"".equals(appId)) {
            queryString = "app_id: (" + appId + ")";
        }

        if (!"".equals(tags)) {
            if (!"".equals(queryString)) {
                queryString += " AND ";
            }
            queryString += "tags: (" + tags + ")";
        }

        if (!"".equals(level)) {
            queryString += " AND level: (" + level + ")";
        }

        if (timeRange > 0) {
            queryString += " AND timeMillis: [" + (time - timeRange * 1000) + " TO " + time + "]";
        }

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode query = mapper.createObjectNode();
        query.put("query", queryString);
        query.put("default_field", defaultField);

        ObjectNode topLevelQuery = mapper.createObjectNode();
        topLevelQuery.set("query", mapper.createObjectNode().set("query_string", query));

        String uri = "/" + index + "/_search/?size=" + resultCount;
        HttpHeaders headers = new HttpHeaders();
        headers.add("content-type", "application/json");
        try {
            log.debug("Issuing ElasticSearch query: " + topLevelQuery.toString());
            log.debug("Using ElasticSearch URI: " + uri);
            HttpEntity rawQuery = new HttpEntity<>(topLevelQuery.toString(), headers);
            return restTemplate.exchange(uri, HttpMethod.POST, rawQuery, HashMap.class).getBody();

        } catch (Exception e) {
            log.error("Exception occured during communication with ElasticSearch", e);
            throw e;
        }
    }
}
