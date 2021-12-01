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

package org.openkilda.rulemanager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.openkilda.rulemanager.Utils.buildSwitch;
import static org.openkilda.rulemanager.Utils.buildSwitchProperties;

import org.openkilda.model.Switch;
import org.openkilda.model.SwitchProperties;
import org.openkilda.rulemanager.factory.RuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.BfdCatchRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.BroadCastDiscoveryRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.DropDiscoveryLoopRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.TableDefaultRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.TablePassThroughDefaultRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.UniCastDiscoveryRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.UnicastVerificationVxlanRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.arp.ArpIngressRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.arp.ArpInputPreDropRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.arp.ArpPostIngressOneSwitchRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.arp.ArpPostIngressRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.arp.ArpPostIngressVxlanRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.arp.ArpTransitRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.lldp.LldpIngressRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.lldp.LldpInputPreDropRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.lldp.LldpPostIngressOneSwitchRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.lldp.LldpPostIngressRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.lldp.LldpPostIngressVxlanRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.lldp.LldpTransitRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.noviflow.RoundTripLatencyRuleGenerator;

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class RuleManagerServiceRulesTest {

    private RuleManagerImpl ruleManager;

    @Before
    public void setup() {
        RuleManagerConfig config = mock(RuleManagerConfig.class);
        when(config.getBroadcastRateLimit()).thenReturn(200);
        when(config.getSystemMeterBurstSizeInPackets()).thenReturn(4096L);
        when(config.getDiscoPacketSize()).thenReturn(250);
        when(config.getFlowPingMagicSrcMacAddress()).thenReturn("00:26:E1:FF:FF:FE");
        when(config.getDiscoveryBcastPacketDst()).thenReturn("00:26:E1:FF:FF:FF");

        ruleManager = new RuleManagerImpl(config);
    }

    @Test
    public void shouldUseCorrectServiceRuleGeneratorsForSwitchInSingleTableMode() {
        Switch sw = buildSwitch("OF_13", Collections.emptySet());
        SwitchProperties switchProperties = buildSwitchProperties(sw, false);

        List<RuleGenerator> generators = ruleManager.getServiceRuleGenerators(switchProperties);

        assertEquals(7, generators.size());
        assertTrue(generators.stream().anyMatch(g -> g instanceof TableDefaultRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof BroadCastDiscoveryRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof UniCastDiscoveryRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof DropDiscoveryLoopRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof BfdCatchRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof RoundTripLatencyRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof UnicastVerificationVxlanRuleGenerator));
    }

    @Test
    public void shouldUseCorrectServiceRuleGeneratorsForSwitchInMultiTableMode() {
        Switch sw = buildSwitch("OF_13", Collections.emptySet());
        SwitchProperties switchProperties = buildSwitchProperties(sw, true);

        List<RuleGenerator> generators = ruleManager.getServiceRuleGenerators(switchProperties);

        assertEquals(18, generators.size());
        assertTrue(generators.stream().anyMatch(g -> g instanceof BroadCastDiscoveryRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof UniCastDiscoveryRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof DropDiscoveryLoopRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof BfdCatchRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof RoundTripLatencyRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof UnicastVerificationVxlanRuleGenerator));

        assertEquals(4, generators.stream().filter(g -> g instanceof TableDefaultRuleGenerator).count());
        assertEquals(2, generators.stream().filter(g -> g instanceof TablePassThroughDefaultRuleGenerator).count());

        assertTrue(generators.stream().anyMatch(g -> g instanceof LldpPostIngressRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof LldpPostIngressVxlanRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof LldpPostIngressOneSwitchRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof ArpPostIngressRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof ArpPostIngressVxlanRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof ArpPostIngressOneSwitchRuleGenerator));
    }

    @Test
    public void shouldUseCorrectServiceRuleGeneratorsForSwitchInMultiTableModeWithSwitchArpAndLldp() {
        Switch sw = buildSwitch("OF_13", Collections.emptySet());
        SwitchProperties switchProperties = buildSwitchProperties(sw, true, true, true);

        List<RuleGenerator> generators = ruleManager.getServiceRuleGenerators(switchProperties);

        assertEquals(24, generators.size());
        assertTrue(generators.stream().anyMatch(g -> g instanceof BroadCastDiscoveryRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof UniCastDiscoveryRuleGenerator));

        assertEquals(4, generators.stream().filter(g -> g instanceof TableDefaultRuleGenerator).count());
        assertEquals(2, generators.stream().filter(g -> g instanceof TablePassThroughDefaultRuleGenerator).count());

        assertTrue(generators.stream().anyMatch(g -> g instanceof LldpPostIngressRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof LldpPostIngressVxlanRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof LldpPostIngressOneSwitchRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof ArpPostIngressRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof ArpPostIngressVxlanRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof ArpPostIngressOneSwitchRuleGenerator));

        assertTrue(generators.stream().anyMatch(g -> g instanceof LldpTransitRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof LldpInputPreDropRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof LldpIngressRuleGenerator));

        assertTrue(generators.stream().anyMatch(g -> g instanceof ArpTransitRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof ArpInputPreDropRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof ArpIngressRuleGenerator));
    }
}
