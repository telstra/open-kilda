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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.openkilda.model.SwitchProperties.RttState;

import com.google.common.collect.Sets;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

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

    @Test
    public void shouldAutoEnableIslRttForNonCopyFieldSwitches() {
        Set<SwitchFeature> features = Arrays.stream(SwitchFeature.values())
                .filter(sf -> !sf.equals(SwitchFeature.NOVIFLOW_COPY_FIELD))
                .collect(Collectors.toSet());
        Switch sw = Switch.builder()
                .switchId(new SwitchId(1))
                .features(features)
                .build();
        SwitchProperties sp = SwitchProperties.builder()
                .switchObj(sw)
                .server42IslRtt(RttState.AUTO)
                .server42Port(1)
                .server42Vlan(2)
                .server42MacAddress(new MacAddress("00:00:00:00:00:01"))
                .build();
        assertTrue(sp.hasServer42IslRttEnabled());
    }

    @Test
    public void shouldnotAutoEnableIslRttForNoviCopyFieldSwitches() {
        Set<SwitchFeature> features = Sets.newHashSet(SwitchFeature.values());
        Switch sw = Switch.builder()
                .switchId(new SwitchId(1))
                .features(features)
                .build();
        SwitchProperties sp = SwitchProperties.builder()
                .switchObj(sw)
                .server42IslRtt(RttState.AUTO)
                .server42Port(1)
                .server42Vlan(2)
                .server42MacAddress(new MacAddress("00:00:00:00:00:01"))
                .build();
        assertFalse(sp.hasServer42IslRttEnabled());
    }

    @Test
    public void shouldEnableIslRttOverrideNoviCopyFieldSwitches() {
        Set<SwitchFeature> features = Sets.newHashSet(SwitchFeature.values());
        Switch sw = Switch.builder()
                .switchId(new SwitchId(1))
                .features(features)
                .build();
        SwitchProperties sp = SwitchProperties.builder()
                .switchObj(sw)
                .server42IslRtt(RttState.ENABLED)
                .server42Port(1)
                .server42Vlan(2)
                .server42MacAddress(new MacAddress("00:00:00:00:00:01"))
                .build();
        assertTrue(sp.hasServer42IslRttEnabled());
    }

    @Test
    public void shouldNotEnableIslRttIfServer42IsNotConfigured() {
        Set<SwitchFeature> features = Arrays.stream(SwitchFeature.values())
                .filter(sf -> !sf.equals(SwitchFeature.NOVIFLOW_COPY_FIELD))
                .collect(Collectors.toSet());
        Switch sw = Switch.builder()
                .switchId(new SwitchId(1))
                .features(features)
                .build();
        SwitchProperties sp1 = SwitchProperties.builder()
                .switchObj(sw)
                .server42IslRtt(RttState.AUTO)
                .build();
        assertFalse(sp1.hasServer42IslRttEnabled());

        SwitchProperties sp2 = SwitchProperties.builder()
                .switchObj(sw)
                .server42IslRtt(RttState.AUTO)
                .server42Port(1)
                .build();
        assertFalse(sp2.hasServer42IslRttEnabled());

        SwitchProperties sp3 = SwitchProperties.builder()
                .switchObj(sw)
                .server42IslRtt(RttState.AUTO)
                .server42Port(1)
                .server42MacAddress(new MacAddress("00:00:00:00:00:01"))
                .build();
        assertFalse(sp3.hasServer42IslRttEnabled());

        SwitchProperties sp4 = SwitchProperties.builder()
                .switchObj(sw)
                .server42IslRtt(RttState.AUTO)
                .server42Port(1)
                .server42Vlan(2)
                .build();
        assertFalse(sp4.hasServer42IslRttEnabled());
    }

    @Test
    public void shouldDisableIslRttIfServer42IsNotConfigured() {
        Set<SwitchFeature> features = Sets.newHashSet(SwitchFeature.values());
        Switch sw = Switch.builder()
                .switchId(new SwitchId(1))
                .features(features)
                .build();
        SwitchProperties sp1 = SwitchProperties.builder()
                .switchObj(sw)
                .server42IslRtt(RttState.ENABLED)
                .build();
        assertFalse(sp1.hasServer42IslRttEnabled());

        SwitchProperties sp2 = SwitchProperties.builder()
                .switchObj(sw)
                .server42IslRtt(RttState.ENABLED)
                .server42Port(1)
                .build();
        assertFalse(sp2.hasServer42IslRttEnabled());

        SwitchProperties sp3 = SwitchProperties.builder()
                .switchObj(sw)
                .server42IslRtt(RttState.ENABLED)
                .server42Port(1)
                .server42MacAddress(new MacAddress("00:00:00:00:00:01"))
                .build();
        assertFalse(sp3.hasServer42IslRttEnabled());

        SwitchProperties sp4 = SwitchProperties.builder()
                .switchObj(sw)
                .server42IslRtt(RttState.ENABLED)
                .server42Port(1)
                .server42Vlan(2)
                .build();
        assertFalse(sp4.hasServer42IslRttEnabled());
    }
}
