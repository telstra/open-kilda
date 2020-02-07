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
        Switch sw = new Switch();
        sw.setSwitchId(new SwitchId(1));
        sw.setFeatures(new HashSet<>());
        SwitchProperties sp = new SwitchProperties();
        sp.setSwitchObj(sw);
        sp.validateProp(SwitchFeature.BFD);
    }

    @Test
    public void validatePropPassesTest() {
        Switch sw = new Switch();
        sw.setSwitchId(new SwitchId(1));
        Set<SwitchFeature> features = new HashSet<>();
        features.add(SwitchFeature.MULTI_TABLE);
        sw.setFeatures(features);
        SwitchProperties sp = new SwitchProperties();
        sp.setSwitchObj(sw);
        assertTrue(sp.validateProp(SwitchFeature.MULTI_TABLE));
    }

    @Test(expected = IllegalArgumentException.class)
    public void setUnsupportedMultiTableFlagTest() {
        Switch sw = new Switch();
        sw.setSwitchId(new SwitchId(1));
        sw.setFeatures(new HashSet<>());
        SwitchProperties sp = new SwitchProperties();
        sp.setSwitchObj(sw);
        sp.setMultiTable(true);


    }

    @Test(expected = IllegalArgumentException.class)
    public void setUnsupportedTransitEncapsulationTest() {
        Switch sw = new Switch();
        sw.setSwitchId(new SwitchId(1));
        sw.setFeatures(new HashSet<>());
        SwitchProperties sp = new SwitchProperties();
        sp.setSwitchObj(sw);
        Set<FlowEncapsulationType> flowEncapsulationTypes = new HashSet<>();
        flowEncapsulationTypes.add(FlowEncapsulationType.VXLAN);
        sp.setSupportedTransitEncapsulation(flowEncapsulationTypes);


    }
}
