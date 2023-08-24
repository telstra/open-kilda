/* Copyright 2022 Telstra Open Source
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

package org.openkilda.pce.finder;

public enum FailReasonType {
    NO_CONNECTION("There is no connection"),
    ALLOWED_DEPTH_EXCEEDED("Allowed depth exceeded"),
    LATENCY_LIMIT("Latency limit"),
    MAX_WEIGHT_EXCEEDED("Max weight exceeded"),
    HARD_DIVERSITY_PENALTIES("There is no non-overlapped protected path"),
    MAX_BANDWIDTH("Failed to find path with requested bandwidth"),
    PERSISTENCE_ERROR("Cannot fetch required data from the persistence layer."),
    UNROUTABLE_FLOW("Unroutable flow exception occurred.");

    private final String value;

    FailReasonType(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }
}
