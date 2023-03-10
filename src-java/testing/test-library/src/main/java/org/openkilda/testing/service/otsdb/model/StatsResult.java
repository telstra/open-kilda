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

package org.openkilda.testing.service.otsdb.model;

import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class StatsResult {
    String metric;
    Map tags;
    List<String> aggregateTags;
    Map<String, Long> dps;

    /**
     * Returns true if statistics only grow during the time.
     * @return boolean is statistics growing on all data points (time -> value)
     */
    public boolean isGrowingMonotonically() {
        assertThat("We need at least 3 points to check if stats are growing monotonically",
                dps.values().size(), greaterThanOrEqualTo(3));
        List<Long> sortedByTimestampValues =
                dps.keySet().stream()
                .map(Long::parseLong)
                .sorted()
                .map(key -> dps.get(Long.toString(key)))
                .collect(toList());
        return sortedByTimestampValues.equals(dps.values().stream().sorted().collect(toList()));
    }
}
