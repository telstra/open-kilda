/* Copyright 2018 Telstra Open Source
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

package org.openkilda.persistence;

import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.TransitVlan;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.repositories.TransitVlanRepository;
import org.openkilda.persistence.repositories.impl.Neo4jSwitchRepository;
import org.openkilda.persistence.repositories.impl.Neo4jTransitVlanRepository;

import org.junit.BeforeClass;
import org.junit.Test;

public class Neo4jConstraintsTest extends Neo4jBasedTest {
    static final int TEST_VLAN = 1;
    static final String TEST_FLOW_1_ID = "test_flow_1";
    static final String TEST_FLOW_2_ID = "test_flow_2";

    static SwitchRepository switchRepository;
    static TransitVlanRepository transitVlanRepository;

    @BeforeClass
    public static void setUp() {
        switchRepository = new Neo4jSwitchRepository(neo4jSessionFactory, txManager);
        transitVlanRepository = new Neo4jTransitVlanRepository(neo4jSessionFactory, txManager);
    }

    @Test(expected = ConstraintViolationException.class)
    public void throwConstraintErrorOnCreateForAnotherFlow() {
        // given
        Switch srcSwitch = buildTestSwitch(1);
        switchRepository.createOrUpdate(srcSwitch);
        Switch dstSwitch = buildTestSwitch(2);
        switchRepository.createOrUpdate(dstSwitch);

        TransitVlan transitVlan1 = TransitVlan.builder()
                .vlan(TEST_VLAN)
                .flowId(TEST_FLOW_1_ID)
                .pathId(new PathId(TEST_FLOW_1_ID + "_path"))
                .build();
        transitVlanRepository.createOrUpdate(transitVlan1);

        // when
        TransitVlan transitVlan2 = TransitVlan.builder()
                .vlan(TEST_VLAN)
                .flowId(TEST_FLOW_2_ID)
                .pathId(new PathId(TEST_FLOW_2_ID + "_path"))
                .build();
        transitVlanRepository.createOrUpdate(transitVlan2);
    }

    @Test(expected = ConstraintViolationException.class)
    public void throwConstraintErrorOnCreateForSamePathAndVlan() {
        // given
        Switch srcSwitch = buildTestSwitch(1);
        switchRepository.createOrUpdate(srcSwitch);
        Switch dstSwitch = buildTestSwitch(2);
        switchRepository.createOrUpdate(dstSwitch);

        TransitVlan transitVlan1 = TransitVlan.builder()
                .vlan(TEST_VLAN)
                .flowId(TEST_FLOW_1_ID)
                .pathId(new PathId(TEST_FLOW_1_ID + "_path"))
                .build();
        transitVlanRepository.createOrUpdate(transitVlan1);

        // when
        TransitVlan transitVlan2 = TransitVlan.builder()
                .vlan(TEST_VLAN)
                .flowId(TEST_FLOW_1_ID)
                .pathId(new PathId(TEST_FLOW_1_ID + "_path"))
                .build();
        transitVlanRepository.createOrUpdate(transitVlan2);
    }
}

