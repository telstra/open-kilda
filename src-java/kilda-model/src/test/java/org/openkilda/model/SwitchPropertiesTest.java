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

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class SwitchPropertiesTest {

    @Test(expected = IllegalArgumentException.class)
    public void validatePropRaiseTest() {
        Switch sw = Switch.builder()
                .switchId(new SwitchId(1))
                .build();
        SwitchProperties sp = SwitchProperties.builder()
                .switchObj(sw)
                .build();
        sp.validateProp(SwitchFeature.BFD);
    }

    @Test
    public void validatePropPassesTest() {
        Set<SwitchFeature> features = new HashSet<>();
        features.add(SwitchFeature.MULTI_TABLE);
        Switch sw = Switch.builder()
                .switchId(new SwitchId(1))
                .features(features)
                .build();
        SwitchProperties sp = SwitchProperties.builder()
                .switchObj(sw)
                .build();
        assertTrue(sp.validateProp(SwitchFeature.MULTI_TABLE));
    }

    @Test(expected = IllegalArgumentException.class)
    public void setUnsupportedMultiTableFlagTest() {
        Switch sw = Switch.builder()
                .switchId(new SwitchId(1))
                .build();
        SwitchProperties sp = SwitchProperties.builder()
                .switchObj(sw)
                .build();
        sp.setMultiTable(true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setUnsupportedTransitEncapsulationTest() {
        Switch sw = Switch.builder()
                .switchId(new SwitchId(1))
                .build();
        SwitchProperties sp = SwitchProperties.builder()
                .switchObj(sw)
                .build();
        Set<FlowEncapsulationType> flowEncapsulationTypes = new HashSet<>();
        flowEncapsulationTypes.add(FlowEncapsulationType.VXLAN);
        sp.setSupportedTransitEncapsulation(flowEncapsulationTypes);
    }
}
