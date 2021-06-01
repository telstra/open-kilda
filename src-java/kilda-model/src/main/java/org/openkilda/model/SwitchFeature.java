/* Copyright 2019 Telstra Open Source
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

public enum SwitchFeature {
    METERS, INACCURATE_METER,
    BFD,
    BFD_REVIEW,
    GROUP_PACKET_OUT_CONTROLLER,
    RESET_COUNTS_FLAG,
    LIMITED_BURST_SIZE,
    NOVIFLOW_COPY_FIELD,
    PKTPS_FLAG,
    MATCH_UDP_PORT,
    MAX_BURST_COEFFICIENT_LIMITATION,
    MULTI_TABLE,
    INACCURATE_SET_VLAN_VID_ACTION,
    NOVIFLOW_PUSH_POP_VXLAN,
    HALF_SIZE_METADATA,
    NOVIFLOW_SWAP_ETH_SRC_ETH_DST,
    GROUPS
}
