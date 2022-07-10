/* Copyright 2022 Telstra Open Source
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

package org.openkilda.testing.service.stats;

import org.openkilda.testing.service.stats.model.EmptyStatsResult;
import org.openkilda.testing.service.stats.model.StatsResult;
import org.openkilda.testing.service.stats.model.victoriametrics.MatrixPayloadWrapper;
import org.openkilda.testing.service.stats.model.victoriametrics.MatrixResponseEntry;
import org.openkilda.testing.service.stats.model.victoriametrics.MatrixResult;
import org.openkilda.testing.service.stats.model.victoriametrics.MatrixValueEntry;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class VictoriaMetricsQueryService implements StatsQueryService {
    private RestTemplate restTemplate;

    private final String queryRangeUri;

    public VictoriaMetricsQueryService(
            @Autowired @Qualifier("statsTsdbRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;

        queryRangeUri = UriComponentsBuilder.fromPath("/api/v1/query_range")
                .queryParam("start", "{start}")
                .queryParam("end", "{end}")
                .queryParam("query", "{query}")
                .build().toUriString();
    }

    @Override
    public StatsResult query(Date start, Date end, String metric, Map<String, Object> tags) {
        if (start.after(end)) {
            throw new IllegalArgumentException("\"start\" argument value must be before \"end\" argument value");
        }

        StringBuilder query = new StringBuilder();
        query.append(escape(metric));
        if (tags != null && !tags.isEmpty()) {
            query.append('{');
            query.append(
                    tags.entrySet().stream()
                            .map(entry -> escape(entry.getKey()) + "=\"" + entry.getValue() + '"')
                            .collect(Collectors.joining(",")));
            query.append('}');
        }

        Map<String, String> arguments = new HashMap<>();
        arguments.put("query", query.toString());
        arguments.put("start", start.toInstant().atOffset(ZoneOffset.UTC).toString());
        arguments.put("end", end.toInstant().atOffset(ZoneOffset.UTC).toString());

        MatrixResult queryResult = restTemplate.exchange(
                queryRangeUri, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()),
                MatrixResult.class, arguments).getBody();

        if (queryResult == null) {
            return new EmptyStatsResult();
        }

        MatrixPayloadWrapper payload = queryResult.getData();
        if (payload == null) {
            throw new IllegalStateException();
        }

        Map<String, StatsResult> result = mapResults(payload, metric);
        StatsResult goal = result.get(metric);
        if (result.size() != 1) {
            throw new IllegalStateException(String.format(
                    "Stats response contain %d metrics, while expecting exact 1 metrics", result.size()));
        }
        if (goal == null) {
            return new EmptyStatsResult();
        }
        return goal;
    }

    @Override
    public StatsResult query(Date start, String metric, Map<String, Object> tags) {
        return query(start, new Date(), metric, tags);
    }

    private Map<String, StatsResult> mapResults(MatrixPayloadWrapper payload, String nameFallback) {
        Map<String, MappingIntermediateEntry> entriesByMetricName = new HashMap<>();

        for (MatrixResponseEntry entry : payload.getResult()) {
            String metricName = entry.getMetric().getMetricName();
            if (metricName == null) {
                metricName = nameFallback;
            }
            entriesByMetricName
                    .computeIfAbsent(metricName, key -> new MappingIntermediateEntry())
                    .accumulate(entry);
        }

        Map<String, StatsResult> results = new HashMap<>();
        for (Map.Entry<String, MappingIntermediateEntry> entry : entriesByMetricName.entrySet()) {
            results.put(entry.getKey(), mapResultEntry(entry.getKey(), entry.getValue()));
        }
        return results;
    }

    private StatsResult mapResultEntry(String name, MappingIntermediateEntry source) {
        StatsResult result = new StatsResult();
        result.setMetric(name);

        result.setTags(source.tags);
        result.setAggregateTags(new ArrayList<>(source.aggregateTags));

        Map<Long, Float> dps = new LinkedHashMap<>();
        for (MatrixValueEntry valueEntry : source.values) {
            dps.put(valueEntry.getTimestamp().getEpochSecond(), valueEntry.getValue());
        }
        result.setDps(dps);

        return result;
    }

    private static String escape(String source) {
        return source.replace("-", "\\-");
    }

    private static class MappingIntermediateEntry {
        private final Map<String, String> tags = new HashMap<>();

        private final Set<String> aggregateTags = new HashSet<>();

        private final List<MatrixValueEntry> values = new ArrayList<>();

        void accumulate(MatrixResponseEntry source) {
            Map<String, String> sourceTags = source.getMetric().getTags();
            this.tags.putAll(sourceTags);
            if (aggregateTags.isEmpty()) {
                aggregateTags.addAll(sourceTags.keySet());
            } else {
                aggregateTags.retainAll(sourceTags.keySet());
            }

            values.addAll(source.getValues());
        }
    }
}
