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

import lombok.Value;

@Value
public final class MeterId {
    /**
     * Minimum meter id value for flows.
     * This value is used to allocate meter IDs for flows. But also we need to allocate meter IDs for default rules.
     * To do it special mask 'PACKET_IN_RULES_METERS_MASK' is used. With the help of this mask we take last 5 bits of
     * default rule cookie to create meter ID. That means we have range 1..31 for default rule meter IDs.
     * MIN_FLOW_METER_ID is used to do not intersect flow meter IDs with this range.
     */
    public static final int MIN_FLOW_METER_ID = 32;

    /**
     * Maximum meter id value for flows.
     * NB: Should be the same as VLAN range at the least, could be more. The formula ensures we have a sufficient range.
     * As centecs have limit of max value equals to 2560 we set it to 2500.
     */
    public static final int MAX_FLOW_METER_ID = 2500;

    /**
     * Minimum meter id value for system rules.
     */
    public static final int MIN_SYSTEM_RULE_METER_ID = 1;

    /**
     * Maximum meter id value for system rules.
     */
    public static final int MAX_SYSTEM_RULE_METER_ID = 31;

    private final long value;
}
