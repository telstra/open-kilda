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

package org.openkilda.persistence.ferma.repositories;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.openkilda.model.ApplicationRule;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.ExclusionCookie;
import org.openkilda.persistence.inmemory.InMemoryGraphBasedTest;
import org.openkilda.persistence.repositories.ApplicationRepository;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;

public class FermaApplicationRepositoryTest extends InMemoryGraphBasedTest {
    private static final String TEST_FLOW_ID = "test_flow_a";
    private static final SwitchId TEST_SWITCH_ID = new SwitchId(1);

    private static ApplicationRepository applicationRepository;

    @BeforeAll
    public static void setUp() {
        applicationRepository = repositoryFactory.createApplicationRepository();
    }

    @BeforeEach
    public void init() {
        applicationRepository.add(buildRuleA());
        applicationRepository.add(buildRuleB());
        applicationRepository.add(buildRuleC());
    }

    @Test
    public void shouldLookupRuleByMatchAndFlow() {
        ApplicationRule ruleA = buildRuleA();
        ApplicationRule foundRule = applicationRepository.lookupRuleByMatchAndFlow(ruleA.getSwitchId(),
                ruleA.getFlowId(), ruleA.getSrcIp(), ruleA.getSrcPort(), ruleA.getDstIp(), ruleA.getDstPort(),
                ruleA.getProto(), ruleA.getEthType(), ruleA.getMetadata()).get();

        assertEquals(ruleA, foundRule);
    }

    @Test
    public void shouldLookupRuleByMatchAndCookie() {
        ApplicationRule ruleA = buildRuleA();
        ApplicationRule foundRule = applicationRepository.lookupRuleByMatchAndCookie(ruleA.getSwitchId(),
                ruleA.getCookie(), ruleA.getSrcIp(), ruleA.getSrcPort(), ruleA.getDstIp(), ruleA.getDstPort(),
                ruleA.getProto(), ruleA.getEthType(), ruleA.getMetadata()).get();

        assertEquals(ruleA, foundRule);
    }

    @Test
    public void shouldFindBySwitchId() {
        Collection<ApplicationRule> foundRules = applicationRepository.findBySwitchId(TEST_SWITCH_ID);

        assertEquals(2, foundRules.size());
        assertTrue(foundRules.contains(buildRuleA()));
        assertTrue(foundRules.contains(buildRuleC()));
    }

    @Test
    public void shouldFindByFlowId() {
        Collection<ApplicationRule> foundRules = applicationRepository.findByFlowId(TEST_FLOW_ID);

        assertEquals(2, foundRules.size());
        assertTrue(foundRules.contains(buildRuleA()));
        assertTrue(foundRules.contains(buildRuleB()));
    }

    private ApplicationRule buildRuleA() {
        return ApplicationRule.builder()
                .flowId(TEST_FLOW_ID)
                .switchId(TEST_SWITCH_ID)
                .srcIp("127.0.1.2")
                .srcPort(2)
                .dstIp("127.0.1.3")
                .dstPort(3)
                .proto("UDP")
                .ethType("IPv4")
                .cookie(ExclusionCookie.builder().direction(FlowPathDirection.FORWARD).exclusionId(1).build())
                .metadata(6L)
                .expirationTimeout(7)
                .build();
    }

    private ApplicationRule buildRuleB() {
        return ApplicationRule.builder()
                .flowId(TEST_FLOW_ID)
                .switchId(new SwitchId(8))
                .srcIp("127.0.1.4")
                .srcPort(9)
                .dstIp("127.0.1.5")
                .dstPort(10)
                .proto("TCP")
                .ethType("IPv4")
                .cookie(ExclusionCookie.builder().direction(FlowPathDirection.FORWARD).exclusionId(1).build())
                .metadata(6L)
                .expirationTimeout(14)
                .build();
    }

    private ApplicationRule buildRuleC() {
        return ApplicationRule.builder()
                .flowId("test_flow_b")
                .switchId(TEST_SWITCH_ID)
                .srcIp("127.0.1.6")
                .srcPort(15)
                .dstIp("127.0.1.7")
                .dstPort(16)
                .proto("TCP")
                .ethType("IPv4")
                .cookie(ExclusionCookie.builder().direction(FlowPathDirection.FORWARD).exclusionId(1).build())
                .metadata(6L)
                .expirationTimeout(20)
                .build();
    }
}
