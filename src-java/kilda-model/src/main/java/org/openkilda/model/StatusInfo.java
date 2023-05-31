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

package org.openkilda.model;

public final class StatusInfo {
    private StatusInfo() {
    }

    public static final String OVERLAPPING_PROTECTED_PATH = "Couldn't find non overlapping protected path";
    public static final String BACK_UP_STRATEGY_USED = "An alternative way (back up strategy or max_latency_tier2 "
            + "value) of building the path was used";
    public static final String NOT_ENOUGH_BANDWIDTH_AND_BACK_UP = "Couldn't find path with required bandwidth and "
            + "backup way was used to build the path";
    public static final String NOT_ENOUGH_BANDWIDTH = "Couldn't find path with required bandwidth";
}
