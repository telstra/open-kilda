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

package org.openkilda.northbound.dto.utils;

public final class Constraints {
    private Constraints() {
    }

    public static final String NEGATIVE_MAXIMUM_BANDWIDTH_MESSAGE = "maximumBandwidth can't be negative";
    public static final String NEGATIVE_MAX_LATENCY_MESSAGE = "maxLatencyTier2 can't be negative";
    public static final String NEGATIVE_MAX_LATENCY_TIER_2_MESSAGE = "maxLatencyTier2 can't be negative";
    public static final String BLANK_PATH_COMPUTATION_STRATEGY_MESSAGE = "pathComputationStrategy should be provided";
    public static final String BLANK_ENCAPSULATION_TYPE_MESSAGE = "encapsulationType should be provided";
    public static final String BLANK_SUBFLOW_FLOW_ID_TYPE_MESSAGE = "sub flow flowId must be provided";
}
