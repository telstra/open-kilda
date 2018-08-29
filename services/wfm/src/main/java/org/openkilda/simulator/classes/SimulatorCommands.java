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

package org.openkilda.simulator.classes;

public final class SimulatorCommands {
    public static final String DO_PORT_MOD = "DO_PORT_MOD";
    public static final String DO_SWITCH_MOD = "DO_SWITCH_MOD";
    public static final String DO_ADD_SWITCH = "DO_ADD_SWITCH";
    public static final String DO_ADD_LINK = "DO_ADD_LINK";
    public static final String DO_REMOVE_SWITCH = "DO_REMOVE_SWITCH";
    public static final String DO_GET_FLOWS = "DO_GET_FLOWS";
    public static final String DO_GET_PORT_STATS = "DO_GET_PORT_STATS";
    public static final String DO_GET_PORT_STATUS = "DO_GET_PORT_STATUS";
    public static final String DO_GET_SWITCH_STATUS = "DO_GET_SWITCH_STATUS";
    public static final String TOPOLOGY = "TOPOLOGY";

    private SimulatorCommands() {}
}
