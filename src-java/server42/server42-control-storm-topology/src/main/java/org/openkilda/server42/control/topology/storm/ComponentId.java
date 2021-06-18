/* Copyright 2020 Telstra Open Source
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

package org.openkilda.server42.control.topology.storm;

public enum ComponentId {
    INPUT_SERVER42_CONTROL("input.server42.control"),
    INPUT_FLOW_HS("input.flowhs"),
    INPUT_NB("input.nb"),

    OUTPUT_SERVER42_CONTROL("output.server42.control"),

    FLOW_HANDLER("flow.handler"),
    ISL_HANDLER("isl.handler"),

    ROUTER("router"),

    TICK_BOLT("tick.bolt");

    private final String value;

    ComponentId(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }
}
