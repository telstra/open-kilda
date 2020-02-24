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

package org.openkilda.testing.service.otsdb;

import static java.util.stream.Collectors.toList;

import org.openkilda.testing.service.otsdb.model.Aggregator;
import org.openkilda.testing.service.otsdb.model.StatsResult;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
public class OtsdbQueryServiceImpl implements OtsdbQueryService {

    @Autowired
    @Qualifier("otsdbRestTemplate")
    private RestTemplate restTemplate;

    @Override
    public StatsResult query(Date start, Date end, Aggregator aggregator, String metric, Map<String, Object> tags) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString("/api/query");
        uriBuilder.queryParam("start", start.getTime());
        uriBuilder.queryParam("end", end.getTime());
        List<String> tagParts = tags.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(toList());
        String tagsString = "{" + String.join(",", tagParts) + "}";
        uriBuilder.queryParam("m", String.format("%s:%s{tags}", aggregator.toString(), metric));
        return restTemplate.exchange(uriBuilder.build().toString(), HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()), StatsResult[].class, tagsString).getBody()[0];
    }

    @Override
    public StatsResult query(Date start, Aggregator aggregator, String metric, Map<String, Object> tags) {
        return query(start, new Date(), aggregator, metric, tags);
    }

    @Override
    public StatsResult query(Date start, String metric, Map<String, Object> tags) {
        return query(start, new Date(), Aggregator.SUM, metric, tags);
    }

    @Override
    public StatsResult query(Date start, Date end, String metric, Map<String, Object> tags) {
        return query(start, end, Aggregator.SUM, metric, tags);
    }
}
