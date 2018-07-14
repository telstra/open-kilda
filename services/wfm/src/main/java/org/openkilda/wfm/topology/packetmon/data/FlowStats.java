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

package org.openkilda.wfm.topology.packetmon.data;

import static java.lang.Math.abs;

import org.openkilda.wfm.topology.packetmon.type.FlowDirection;
import org.openkilda.wfm.topology.packetmon.type.FlowType;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;

public class FlowStats {
    private static final int minimumNumOfDataPoints = 2;
    private static final Logger logger = LoggerFactory.getLogger(FlowStats.class);
    private String flowId;
    private long lastUpdated;
    private Table<FlowDirection, FlowType, TimeValue> datapoints;
    private double maxTimeDelta;
    private double maxFlowVariance;

    /**
     * Represents stats about a flow.
     *
     * @param flowId String flowId
     * @param windowSize int Number of time/value paris to keep in the series
     * @param maxTimeDelta int If time delta between ingress/egress is greater then not a valid test
     * @param maxFlowVariance double If delta between ingress/ergess rate is greater then flow in error
     */
    public FlowStats(String flowId, int windowSize, int maxTimeDelta, double maxFlowVariance) {
        this.flowId = flowId;
        lastUpdated = now();
        this.maxTimeDelta = maxTimeDelta;
        this.maxFlowVariance = maxFlowVariance;

        datapoints = HashBasedTable.create();
        EnumSet.allOf(FlowDirection.class)
                .forEach(direction -> {
                    EnumSet.allOf(FlowType.class)
                            .forEach(flowType -> {
                                datapoints.put(direction, flowType, new TimeValue(windowSize));
                            });
                });
    }

    /**
     * Remove all data from series.
     */
    public void clear() {
        EnumSet.allOf(FlowDirection.class)
                .forEach(direction -> {
                    EnumSet.allOf(FlowType.class)
                            .forEach(flowType -> {
                                datapoints.clear();
                            });
                });
    }

    public String getFlowId() {
        return flowId;
    }

    private long now() {
        return System.currentTimeMillis();
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public void add(FlowDirection direction, FlowType flowType, long timestamp, long value) {
        datapoints.get(direction, flowType).add(timestamp, value);
        lastUpdated = now();
    }

    public double getAvgSpeed(FlowDirection direction, FlowType flowType) {
        return datapoints.get(direction, flowType).getAvgSpeed();
    }

    public boolean haveEnoughData(FlowDirection direction) {
        return (datapoints.get(direction, FlowType.INGRESS).size() >= 2)
                        && (datapoints.get(direction, FlowType.EGRESS).size() >= 2);
    }

    /**
     * Check if time difference between last measurement for ingress and egress directions is less than
     * maxTimeDelta.  If it is, then we can do a test if not we have to pass on the test.
     *
     * @param direction Flow direction to test
     * @return boolean True means we are close enough to do a test
     */
    public boolean valuesWithinTimeDelta(FlowDirection direction) {
        double timeDelta = abs(datapoints.get(direction, FlowType.INGRESS).getMaxTime()
                - datapoints.get(direction, FlowType.EGRESS).getMaxTime());
        if (timeDelta > maxTimeDelta) {
            logger.debug("{} delta time between ingress and egress is {} while maxTimeDelta is {}",
                    flowId, timeDelta, maxTimeDelta);
            return false;
        }
        return true;
    }

    public double variance(FlowDirection direction) {
        return datapoints.get(direction, FlowType.INGRESS).getAvgSpeed()
                - datapoints.get(direction, FlowType.EGRESS).getAvgSpeed();
    }

    public double variancePercentage(FlowDirection direction) {
        return variance(direction) / datapoints.get(direction, FlowType.INGRESS).getAvgSpeed() * 100;
    }

    /**
     * Test to see if the speeds in the ingress and egress direction are outside of the tolerable range.  Rather
     * than look at a hard value, compare the percentage difference between Ingress and Egress average rates for the
     * window.  If rate is negative, means we have more packets for Egress than Ingress, thus can't really tell so
     * return true.
     *
     * @param direction Flow direction to test.
     * @return boolean
     */
    public boolean isWithinVariance(FlowDirection direction) {
        if (variancePercentage(direction) > maxFlowVariance) {
            logger.debug("{} variance is {} for {}% vs maxFlowVariance of {}", flowId, variance(direction),
                    variancePercentage(direction), maxFlowVariance);
            return false;
        }
        return true;
    }

    /**
     * Check if the flow is dropping packets based on comparing Ingress and Egress flow metrics.
     *
     * @param direction Flow direction
     * @return boolean
     */
    public boolean ok(FlowDirection direction) {
        // need at least 2 datapoints for each FlowType; else return true as we don't have enough info to decide
        if (!haveEnoughData(direction)) {
            return true;
        }

        // if timestamps on last entry within maxTimeDelta then compare values, else can't test
        // TODO: implement rolling counter to see if we are never testing because of timeDelta
        if (!valuesWithinTimeDelta(direction)) {
            return true;
        }

        // if ingress and egress average rates > maxFlowVariance then flow is deemed broken
        return isWithinVariance(direction);
    }
}
