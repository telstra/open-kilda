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

package org.openkilda.testing.service.northbound.payloads;


public enum PathRequestParameter {
    SRC_SWITCH("src_switch"),
    DST_SWITCH("dst_switch"),
    PATH_COMPUTATION_STRATEGY("path_computation_strategy"),
    FLOW_ENCAPSULATION_TYPE("encapsulation_type"),
    MAX_LATENCY("max_latency"),
    MAX_LATENCY_TIER2("max_latency_tier2"),
    MAX_PATH_COUNT("max_path_count"),
    PROTECTED("includeProtectedPath");

    public String getQueryKey() {
        return queryKey;
    }

    private final String queryKey;

    PathRequestParameter(String queryKey) {
        this.queryKey = queryKey;
    }
}
