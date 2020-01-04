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

import org.openkilda.testing.service.otsdb.model.Aggregator;
import org.openkilda.testing.service.otsdb.model.StatsResult;

import java.util.Date;
import java.util.Map;

public interface OtsdbQueryService {
    /**
     * Query OpenTsdb with given params.
     *
     * @param start gather stats 'after' this date
     * @param end gather stats 'before' this date
     * @param aggregator The name of an aggregation function to use. {@link Aggregator}
     * @param metric Metric name, e.g. "kilda.switch.state"
     * @param tags tags in form of key-value pairs
     * @return query results
     * @see <a href="http://opentsdb.net/docs/build/html/api_http/query/index.html:>OpenTsdb HTTP API</a>
     */
    StatsResult query(Date start, Date end, Aggregator aggregator, String metric, Map<String, Object> tags);

    StatsResult query(Date start, Aggregator aggregator, String metric, Map<String, Object> tags);

    StatsResult query(Date start, String metric, Map<String, Object> tags);

    StatsResult query(Date start, Date end, String metric, Map<String, Object> tags);
}
