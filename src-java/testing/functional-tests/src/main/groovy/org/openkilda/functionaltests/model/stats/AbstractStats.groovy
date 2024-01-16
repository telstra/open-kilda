package org.openkilda.functionaltests.model.stats

import org.openkilda.testing.service.tsdb.TsdbQueryService
import org.openkilda.testing.service.tsdb.model.StatsMetric
import org.openkilda.testing.service.tsdb.model.StatsResult

abstract class AbstractStats {
    protected List<StatsResult> stats
    protected static TsdbQueryService tsdbQueryService

    protected StatsResult getStats(StatsMetric metric, Closure<Boolean> filter = {true}) {
        def filteredResult = stats.findAll { it.metric.endsWith(metric.getValue()) }.findAll(filter)
        return !filteredResult.isEmpty() ? filteredResult.first() :
                new StatsResult("Empty stats", [:] as Map<String, String>, [:] as Map<Long, Long>)
    }
}
