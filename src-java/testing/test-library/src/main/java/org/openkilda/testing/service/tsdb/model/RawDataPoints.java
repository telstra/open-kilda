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

package org.openkilda.testing.service.tsdb.model;


import static java.util.stream.Collectors.averagingLong;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

import lombok.Data;

import java.util.ArrayList;
import java.util.Map;
import java.util.stream.IntStream;

@Data
public class RawDataPoints {
    private RawMetric metric;
    private ArrayList<Long> values;
    private ArrayList<Long> timestamps;

    /**
     * Converts this object to StatsResult object. If several datapoints have the same timestamp,
     * average value is stored into resulting StatsResult
     * @return StatsResult object
     */
    public StatsResult toStatsResult() {
        Map<Long, Long> dataPoints = IntStream.range(0, timestamps.size())
                .boxed()
                .collect(groupingBy(
                        timestamps::get,
                        averagingLong(i -> values.get(i))
                )).entrySet().stream()
                .collect(toMap(Map.Entry::getKey, e -> e.getValue().longValue()));
        return new StatsResult(this.metric.getName(), this.metric.getTags(), dataPoints);
    }
}
