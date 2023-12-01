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

import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.iterableWithSize;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class StatsResult {
    String metric;
    Map<String, String> tags;
    Map<Long, Long> dataPoints;

    public StatsResult(String metric, Map<String, String> tags, Map<Long, Long> dataPoints) {
        this.metric = metric;
        this.tags = tags;
        this.dataPoints = dataPoints;
    }

    /**
     * Checks by at least three data points if the value grows monotonically.
     * @return boolean true if data points values grow with time
     */
    public boolean isGrowingMonotonically() {
        assertThat("We need at least 3 points to check if stats are growing monotonically", dataPoints.values(),
                allOf(
                        iterableWithSize(greaterThan(2)),
                        hasItem(greaterThan(0L))));
        List<Long> sortedByTimestampValues =
                dataPoints.keySet().stream()
                .sorted()
                .map(key -> dataPoints.get(key))
                .collect(toList());
        return sortedByTimestampValues.equals(dataPoints.values().stream().sorted().collect(toList()));
    }

    public boolean hasNonZeroValues() {
        return dataPoints.values().stream().anyMatch(datapoint -> datapoint != 0);
    }

    public boolean hasNonZeroValuesAfter(long timestamp) {
        return dataPoints.keySet().stream()
                .anyMatch(tstamp -> tstamp >= timestamp && dataPoints.get(tstamp) != 0);
    }

    public boolean hasValue(long expectedValue) {
        return dataPoints.values().contains(expectedValue);
    }

    public Long getNewestTimeStamp() {
        return dataPoints.keySet().stream().max(Long::compareTo).orElse(0L);
    }
}
