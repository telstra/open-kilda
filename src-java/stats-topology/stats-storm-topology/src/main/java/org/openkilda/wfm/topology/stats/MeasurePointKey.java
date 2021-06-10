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

package org.openkilda.wfm.topology.stats;

import org.openkilda.model.SwitchId;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.apache.commons.lang3.builder.EqualsBuilder;

@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MeasurePointKey {
    SwitchId switchId;
    int inPort;
    Integer inVlan;
    Integer inInnerVlan;
    int outPort;
    Integer outVlan;
    Integer outInnerVlan;

    public static MeasurePointKey buildForSwitchRule(SwitchId switchId, int inPort, int outPort) {
        return new MeasurePointKey(switchId, inPort, null, null, outPort, null, null);
    }

    public static MeasurePointKey buildForIngressSwitchRule(SwitchId switchId, int inPort, int inVlan,
                                                            int inInnerVlan, int outPort) {
        return new MeasurePointKey(switchId, inPort, inVlan, inInnerVlan, outPort, null, null);
    }

    public static MeasurePointKey buildForEgressSwitchRule(SwitchId switchId, int inPort, int outPort,
                                                           int outVlan, int outInnerVlan) {
        return new MeasurePointKey(switchId, inPort, null, null, outPort, outVlan, outInnerVlan);
    }

    public static MeasurePointKey buildForOneSwitchRule(SwitchId switchId, int inPort, int inVlan,
                                                        int inInnerVlan, int outPort, int outVlan,
                                                        int outInnerVlan) {
        return new MeasurePointKey(switchId, inPort, inVlan, inInnerVlan, outPort, outVlan, outInnerVlan);
    }

    /**
     * Match the current and provided keys using only set (non-null) fields.
     *
     * @param key the key to compare with.
     * @return whether the keys equals each other by set (non-null) fields.
     */
    public boolean matches(MeasurePointKey key) {
        EqualsBuilder builder = new EqualsBuilder()
                .append(switchId, key.switchId)
                .append(inPort, key.inPort)
                .append(outPort, key.outPort);
        if (inVlan != null && key.inVlan != null) {
            builder.append(inVlan, key.inVlan);
        }
        if (inInnerVlan != null && key.inInnerVlan != null) {
            builder.append(inInnerVlan, key.inInnerVlan);
        }
        if (outVlan != null && key.outVlan != null) {
            builder.append(outVlan, key.outVlan);
        }
        if (outInnerVlan != null && key.outInnerVlan != null) {
            builder.append(outInnerVlan, key.outInnerVlan);
        }
        return builder.build();
    }
}
