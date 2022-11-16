/* Copyright 2021 Telstra Open Source
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

package org.openkilda.messaging.info.switches.v2.action;

public enum ActionType {

    GROUP,
    PORT_OUT,
    POP_VLAN,
    PUSH_VLAN,
    POP_VXLAN_NOVIFLOW,
    POP_VXLAN_OVS,
    PUSH_VXLAN_NOVIFLOW,
    PUSH_VXLAN_OVS,
    SET_FIELD,
    METER,
    NOVI_COPY_FIELD,
    NOVI_SWAP_FIELD,
    KILDA_SWAP_FIELD
}
