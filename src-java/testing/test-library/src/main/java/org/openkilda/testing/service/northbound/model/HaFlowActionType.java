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

package org.openkilda.testing.service.northbound.model;

public enum HaFlowActionType {
    CREATE("HA-Flow create", "HA-flow has been created successfully"),
    DELETE("HA-Flow delete", "HA-flow has been deleted successfully"),
    UPDATE("HA-Flow update", "HA-flow has been updated successfully"),
    PARTIAL_UPDATE_IN_DB("HA-Flow partial update", "HA-Flow partial update has been completed"),
    PARTIAL_UPDATE_FULL("HA-Flow partial update", "HA-flow has been updated successfully"),
    REROUTE("HA-Flow reroute", "HA-flow has been rerouted successfully"),
    REROUTE_FAIL("HA-Flow reroute", "Failed to reroute the HA-flow");

    final String value;
    final String payloadLastAction;

    public String getValue() {
        return value;
    }

    public String getPayloadLastAction() {
        return payloadLastAction;
    }

    HaFlowActionType(String value, String payloadLastAction) {
        this.value = value;
        this.payloadLastAction = payloadLastAction;
    }
}
