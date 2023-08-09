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

import static java.util.stream.Collectors.toList;

import org.openkilda.testing.service.tsdb.model.LegacyQueryRequestDto;
import org.openkilda.testing.service.tsdb.model.LegacyStatsResult;
import org.openkilda.testing.service.tsdb.model.StatsMetric;
import org.openkilda.testing.service.tsdb.model.StatsResult;

import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Service("legacyTsdbService")
@Slf4j
public class LegacyTsdbQueryServiceImpl implements TsdbQueryService {

    @Autowired
    @Qualifier("legacyTsdbRestTemplate")
    private RestTemplate restTemplate;

    @Value("${tsdb.metric.prefix}")
    String metricPrefix;

    @Override
    public List<StatsResult> queryDataPointsForLastFiveMinutes(List<StatsMetric> metrics, String idTag, String id) {
        return queryDataPointsForLastMinutes(metrics, idTag, id, 5);
    }

    @Override
    public List<StatsResult> queryDataPointsForLastMinutes(List<StatsMetric> metrics,
                                                           String idTag,
                                                           String id,
                                                           int minutes) {
        List<LegacyQueryRequestDto.QueryDto> queryDtoS = metrics.stream()
                .map(metric -> LegacyQueryRequestDto.QueryDto.builder()
                        .metric(metricPrefix + metric.getValue())
                        .aggregator("none")
                        .tags(ImmutableMap.of(idTag, id))
                        .rate(false)
                        .build())
                .collect(toList());
        LegacyQueryRequestDto body = LegacyQueryRequestDto.builder()
                .start(new Date().getTime() - minutes * 60 * 1000)
                .end(new Date().getTime())
                .queries(queryDtoS)
                .build();
        LegacyStatsResult[] response = restTemplate.exchange("/api/query",
                        HttpMethod.POST,
                        new HttpEntity<>(body, new HttpHeaders()), LegacyStatsResult[].class)
                .getBody();
        return Arrays.asList(response).stream().map(LegacyStatsResult::toStatsResult).collect(toList());
    }
}
