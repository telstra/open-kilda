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

public enum Commands {
    DO_DISCOVER_ISL_COMMAND,
    DO_DISCOVER_ISL_P2_COMMAND,
    DO_DISCOVER_PATH_COMMAND,
    DO_INSTALL_INGRESS_FLOW,
    DO_INSTALL_EGRESS_FLOW,
    DO_INSTALL_TRANSIT_FLOW,
    DO_INSTALL_ONESWITCH_FLOW,
    DO_DELETE_FLOW;
}
