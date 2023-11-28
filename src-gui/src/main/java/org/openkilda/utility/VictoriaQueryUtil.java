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

package org.openkilda.utility;

import org.openkilda.model.victoria.RangeQueryParams;

import java.util.Map;
import java.util.stream.Collectors;

public final class VictoriaQueryUtil {

    private VictoriaQueryUtil() {

    }

    /**
     * Builds and returns RangeQueryParams for a given time range, metric, and optional query parameters.
     *
     * @param startTimeStamp         The start timestamp of the time range.
     * @param endTimeStamp           The end timestamp of the time range.
     * @param step                   The time step interval for data points.
     * @param metricName             The name of the metric to query.
     * @param queryParamLabelFilters A map of label filters for the metric query.
     * @param useRate                A flag indicating whether to calculate the rate.
     * @param useSum                 A flag indicating whether to calculate the sum.
     * @return RangeQueryParams object representing the parameters for the range query.
     */
    public static RangeQueryParams buildRangeQueryParams(Long startTimeStamp, Long endTimeStamp,
                                                         String step, String metricName,
                                                         Map<String, String> queryParamLabelFilters,
                                                         boolean useRate, boolean useSum) {
        return RangeQueryParams.builder()
                .start(startTimeStamp)
                .end(endTimeStamp)
                .step(step)
                .query(buildVictoriaRequestRangeQueryFormParam(metricName, queryParamLabelFilters, useRate, useSum))
                .build();
    }

    private static String buildVictoriaRequestRangeQueryFormParam(String metricName,
                                                                  Map<String, String> queryParamLabelFilters,
                                                                  boolean useRate, boolean useSum) {
        String lableFilterString = queryParamLabelFilters.entrySet().stream()
                .filter(keyValue -> !keyValue.getValue().equals("*"))
                .map(entry -> String.format("%s='%s'", entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(", "));
        String labelList = String.join(",", queryParamLabelFilters.keySet());
        String query = String.format("%s{%s}", metricName.replace("-", "\\-"), lableFilterString);
        if (useSum) {
            query = addSumToQuery(query, labelList);
        }
        if (useRate) {
            query = addRateToQuery(query);
        }
        return query;
    }

    private static String addRateToQuery(String query) {
        return "rate(" + query + ")";
    }

    private static String addSumToQuery(String query, String groupByLabels) {
        return String.format("sum(" + query + ") by (%s)", groupByLabels);
    }

}
