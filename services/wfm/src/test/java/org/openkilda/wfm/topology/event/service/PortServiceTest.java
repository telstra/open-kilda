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

package org.openkilda.wfm.topology.event.service;

import static org.junit.Assert.assertEquals;

import org.openkilda.model.Isl;
import org.openkilda.model.IslStatus;
import org.openkilda.model.LinkProps;
import org.openkilda.model.Port;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.LinkPropsRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.Neo4jBasedTest;

import com.google.common.collect.Lists;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

public class PortServiceTest extends Neo4jBasedTest {
    private static SwitchRepository switchRepository;
    private static LinkPropsRepository linkPropsRepository;
    private static IslRepository islRepository;
    private static PortService portService;
    private static int islCostWhenPortDown;

    private static final SwitchId TEST_SWITCH_A_ID = new SwitchId(1);
    private static final int TEST_SWITCH_A_PORT = 1;
    private static final SwitchId TEST_SWITCH_B_ID = new SwitchId(2);
    private static final int TEST_SWITCH_B_PORT = 1;

    @BeforeClass
    public static void setUpOnce() {
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        switchRepository = repositoryFactory.createSwitchRepository();
        linkPropsRepository = repositoryFactory.createLinkPropsRepository();
        islRepository = repositoryFactory.createIslRepository();

        islCostWhenPortDown = 10000;
        portService = new PortService(txManager, repositoryFactory, islCostWhenPortDown);
    }

    @Test
    public void shouldSetUpIslInInactiveStatusAndSetUpNewCostWhenPortInDownStatus() {
        int cost = 10;
        createLinkProps(cost);
        createIsl(TEST_SWITCH_A_ID, TEST_SWITCH_A_PORT, TEST_SWITCH_B_ID, TEST_SWITCH_B_PORT, cost);
        createIsl(TEST_SWITCH_B_ID, TEST_SWITCH_B_PORT, TEST_SWITCH_A_ID, TEST_SWITCH_A_PORT, cost);

        Port port = new Port();
        port.setTheSwitch(Switch.builder().switchId(TEST_SWITCH_B_ID).build());
        port.setPortNo(TEST_SWITCH_A_PORT);

        portService.processWhenPortIsDown(port);

        Isl isl = islRepository.findByEndpoints(TEST_SWITCH_A_ID, TEST_SWITCH_A_PORT,
                TEST_SWITCH_B_ID, TEST_SWITCH_B_PORT).get();

        assertEquals(IslStatus.INACTIVE, isl.getStatus());
        assertEquals(islCostWhenPortDown + cost, isl.getCost());

        Isl reverseIsl = islRepository.findByEndpoints(TEST_SWITCH_B_ID, TEST_SWITCH_B_PORT,
                TEST_SWITCH_A_ID, TEST_SWITCH_A_PORT).get();

        assertEquals(IslStatus.INACTIVE, reverseIsl.getStatus());
        assertEquals(islCostWhenPortDown + cost, reverseIsl.getCost());

        List<LinkProps> linkProps = Lists.newArrayList(linkPropsRepository.findByEndpoints(
                TEST_SWITCH_A_ID, TEST_SWITCH_A_PORT, TEST_SWITCH_B_ID, TEST_SWITCH_B_PORT));

        assertEquals(islCostWhenPortDown + cost, (long) linkProps.get(0).getCost());
    }

    private void createIsl(SwitchId srcSwitch, int srcPort, SwitchId dstSwitch, int dstPort, int cost) {
        Isl isl = new Isl();
        isl.setSrcSwitch(createSwitchIfNotExist(srcSwitch));
        isl.setSrcPort(srcPort);
        isl.setDestSwitch(createSwitchIfNotExist(dstSwitch));
        isl.setDestPort(dstPort);
        isl.setCost(cost);
        isl.setActualStatus(IslStatus.ACTIVE);
        isl.setStatus(IslStatus.ACTIVE);

        islRepository.createOrUpdate(isl);
    }

    private Switch createSwitchIfNotExist(SwitchId switchId) {
        return switchRepository.findById(switchId).orElseGet(() -> {
            Switch sw = Switch.builder().switchId(switchId).status(SwitchStatus.ACTIVE).build();
            switchRepository.createOrUpdate(sw);
            return sw;
        });
    }

    private void createLinkProps(int cost) {
        LinkProps linkProps = new LinkProps();
        linkProps.setSrcSwitchId(TEST_SWITCH_A_ID);
        linkProps.setSrcPort(TEST_SWITCH_A_PORT);
        linkProps.setDstSwitchId(TEST_SWITCH_B_ID);
        linkProps.setDstPort(TEST_SWITCH_B_PORT);
        linkProps.setCost(cost);

        linkPropsRepository.createOrUpdate(linkProps);
    }
}
