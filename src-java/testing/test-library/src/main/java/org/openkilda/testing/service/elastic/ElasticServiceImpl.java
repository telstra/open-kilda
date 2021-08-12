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

import org.openkilda.testing.service.elastic.model.ElasticResponseDto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;


/**
 * An interface to the Elastic Search. Set following variables in the kilda.properties for it to work:
 * elasticsearch.endpoint = URL to Elastic elasticsearch.username = User with read access elasticsearch.password =
 * User's password
 */

@Component
@Slf4j
public class ElasticServiceImpl implements ElasticService {

    @Autowired
    @Qualifier("elasticSearchRestTemplate")
    private RestTemplate restTemplate;

    /**
     * Searches the Elastic Search database for a specific log entries. In case you need to lookup multiple application
     * IDs, tags or log levels, pass parameters as: "VALUE1 OR VALUE2 OR ... OR VALUE_N"
     *
     * @param query - ElasticQuery instance (use ElasticQueryBuilder, be sure to specify either appId or tags)
     * @return ElasticResponseDto
     */
    public ElasticResponseDto getLogs(ElasticQuery query) {
        if (("".equals(query.appId) && "".equals(query.tags)) && (null == query.additionalFields)) {
            throw new IllegalArgumentException("Either app_id or tags or arbitrary fields should be specified");
        }
        StringBuilder queryString = new StringBuilder();

        if (query.additionalFields != null) {
            for (Map.Entry<String, String> entry : query.additionalFields.entrySet()) {
                if (queryString.length() > 0) {
                    queryString.append(" AND ");
                }
                queryString.append(entry.getKey()).append(": (").append(entry.getValue()).append(")");
            }
        }
        if (query.appId != null && !"".equals(query.appId)) {
            if (queryString.length() > 0) {
                queryString.append(" AND ");
            }
            queryString.append("app_id: (").append(query.appId).append(")");
        }

        if (query.tags != null && !"".equals(query.tags)) {
            if (queryString.length() > 0) {
                queryString.append(" AND ");
            }
            queryString.append("tags: (").append(query.tags).append(")");
        }

        if (query.level != null && !"".equals(query.level)) {
            queryString.append(" AND level: (").append(query.level).append(")");
        }

        ObjectMapper mapper = new ObjectMapper();
        ArrayNode combinedQuery = mapper.createArrayNode();

        //first criteria - multi-filter on _source field
        ObjectNode fieldValueFilter = mapper.createObjectNode();
        fieldValueFilter.set("query_string", mapper.createObjectNode().put("query", queryString.toString())
                .put("default_field", query.defaultField));
        combinedQuery.add(fieldValueFilter);

        if (query.timeRange > 0) {
            //second criteria - time range (if present)
            //TODO: consider using timestamps instead of "now-time" approach, as it would simplify post-mortem analysis.
            ObjectNode timeRangeFilter = mapper.createObjectNode();
            timeRangeFilter.set("range", mapper.createObjectNode().set("@timestamp", mapper.createObjectNode()
                    .put("gt", "now-" + query.timeRange + "s")));
            combinedQuery.add(timeRangeFilter);
        }

        //wrapping up criterial search into bool "must" query where all conditions must be satisfied
        ObjectNode topLevelRequest = mapper.createObjectNode();
        topLevelRequest.set("query", mapper.createObjectNode().set("bool", mapper.createObjectNode().set("must",
                combinedQuery)));

        //limiting results count to a given size
        String uri = "/" + query.index + "/_search/?size=" + query.resultCount;

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        try {
            log.debug("Issuing ElasticSearch query: " + topLevelRequest.toString());
            log.debug("Using ElasticSearch request URI: " + uri);
            HttpEntity rawQuery = new HttpEntity<>(topLevelRequest.toString(), headers);
            return restTemplate.exchange(uri, HttpMethod.POST, rawQuery, ElasticResponseDto.class).getBody();

        } catch (Exception e) {
            log.error("Exception occured during communication with ElasticSearch", e);
            throw e;
        }
    }
}
