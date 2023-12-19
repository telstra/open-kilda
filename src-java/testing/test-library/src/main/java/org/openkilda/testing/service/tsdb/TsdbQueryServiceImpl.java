/* Copyright 2023 Telstra Open Source
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

package org.openkilda.testing.service.tsdb;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

import org.openkilda.testing.service.tsdb.model.RawDataPoints;
import org.openkilda.testing.service.tsdb.model.StatsResult;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class TsdbQueryServiceImpl implements TsdbQueryService {

    @Autowired
    @Qualifier("tsdbRestTemplate")
    private RestTemplate restTemplate;
    @Value("${tsdb.metric.prefix}")
    String metricPrefix;

    @Override
    public List<StatsResult> queryDataPointsForLastMinutes(String query, int minutes) {
        query = String.format(query, metricPrefix);
        final ObjectMapper objectMapper = new ObjectMapper();
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString("/api/v1/export");
        uriBuilder.queryParam("start", (new Date().getTime() - minutes * 60 * 1000) / 1000);
        uriBuilder.queryParam("match[]", format("{%s}", query));
        String response = restTemplate.exchange(uriBuilder.build().toString(),
                HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()), String.class, "{" + query + "}")
                .getBody();
        if (response == null) {
            return new ArrayList<>();
        }
        return Arrays.stream(response.split(System.lineSeparator()))
                .map(line -> {
                    try {
                        return objectMapper.readValue(line, RawDataPoints.class);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                })
                .map(RawDataPoints::toStatsResult)
                .collect(toList());
    }

    @Override
    public List<StatsResult> queryDataPointsForLastFiveMinutes(String query) {
        return queryDataPointsForLastMinutes(query, 5);
    }
}
