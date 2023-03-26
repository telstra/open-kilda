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

package org.openkilda.messaging.model;

import static com.google.common.collect.Sets.immutableEnumSet;

import java.util.Set;

public enum ValidationFilter {
    GROUPS, LOGICAL_PORTS, METERS, RULES, FLOW_INFO,

    // Needed only for v1 validate/sync API. In this API meters info contains data about cookie and
    // flowId. Must be removed with v1 validate/sync API.
    @Deprecated
    METER_FLOW_INFO;

    public static final Set<ValidationFilter> ALL = immutableEnumSet(GROUPS, LOGICAL_PORTS, METERS, RULES, FLOW_INFO);
    public static final Set<ValidationFilter> ALL_WITHOUT_FLOW_INFO = immutableEnumSet(
            GROUPS, LOGICAL_PORTS, METERS, RULES);
    @Deprecated
    public static final Set<ValidationFilter> ALL_WITH_METER_FLOW_INFO = immutableEnumSet(
            GROUPS, LOGICAL_PORTS, METERS, RULES, METER_FLOW_INFO); // See `METER_FLOW_INFO` comment

}
