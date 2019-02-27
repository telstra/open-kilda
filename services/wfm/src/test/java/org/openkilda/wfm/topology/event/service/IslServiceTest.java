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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.openkilda.model.Isl;
import org.openkilda.model.IslStatus;
import org.openkilda.model.LinkProps;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.LinkPropsRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.Neo4jBasedTest;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class IslServiceTest extends Neo4jBasedTest {
    private static LinkPropsRepository linkPropsRepository;
    private static IslRepository islRepository;
    private static IslService islService;
    private static SwitchRepository switchRepository;

    private static final SwitchId TEST_SWITCH_A_ID = new SwitchId(1);
    private static final int TEST_SWITCH_A_PORT = 1;
    private static final SwitchId TEST_SWITCH_B_ID = new SwitchId(2);
    private static final int TEST_SWITCH_B_PORT = 1;

    private static int islCostWhenUnderMaintenance = 10000;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    public static void setUpOnce() {
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        linkPropsRepository = repositoryFactory.createLinkPropsRepository();
        islRepository = repositoryFactory.createIslRepository();
        switchRepository = repositoryFactory.createSwitchRepository();

        islService = new IslService(txManager, repositoryFactory, islCostWhenUnderMaintenance);
    }

    @Test
    public void shouldCreateAndThenUpdateIsl() {
        int cost = 10;
        createLinkProps(cost);

        Isl isl = createIsl();
        islService.createOrUpdateIsl(isl);

        Isl foundIsl = islRepository.findByEndpoints(TEST_SWITCH_A_ID, TEST_SWITCH_A_PORT,
                TEST_SWITCH_B_ID, TEST_SWITCH_B_PORT).get();
        assertNotNull(foundIsl);
        assertEquals(IslStatus.INACTIVE, foundIsl.getStatus());
        assertEquals(IslStatus.ACTIVE, foundIsl.getActualStatus());
        assertEquals(cost, foundIsl.getCost());

        Isl reverseIsl = createReverseIsl(isl);
        islService.createOrUpdateIsl(reverseIsl);

        foundIsl = islRepository.findByEndpoints(TEST_SWITCH_A_ID, TEST_SWITCH_A_PORT,
                TEST_SWITCH_B_ID, TEST_SWITCH_B_PORT).get();
        assertEquals(IslStatus.ACTIVE, foundIsl.getStatus());
        assertEquals(IslStatus.ACTIVE, foundIsl.getActualStatus());

        Isl foundReverseIsl = islRepository.findByEndpoints(TEST_SWITCH_A_ID, TEST_SWITCH_A_PORT,
                TEST_SWITCH_B_ID, TEST_SWITCH_B_PORT).get();
        assertEquals(IslStatus.ACTIVE, foundReverseIsl.getStatus());
        assertEquals(IslStatus.ACTIVE, foundReverseIsl.getActualStatus());
    }

    @Test
    public void shouldPutIslInInactiveStatus() {
        Isl isl = createIsl();
        islService.createOrUpdateIsl(isl);

        Isl reverseIsl = createReverseIsl(isl);
        islService.createOrUpdateIsl(reverseIsl);

        Isl foundIsl = islRepository.findByEndpoints(TEST_SWITCH_A_ID, TEST_SWITCH_A_PORT,
                TEST_SWITCH_B_ID, TEST_SWITCH_B_PORT).get();
        assertNotNull(foundIsl);
        assertEquals(IslStatus.ACTIVE, foundIsl.getStatus());

        boolean updated = islService.islDiscoveryFailed(isl);
        assertTrue(updated);

        foundIsl = islRepository.findByEndpoints(TEST_SWITCH_A_ID, TEST_SWITCH_A_PORT,
                TEST_SWITCH_B_ID, TEST_SWITCH_B_PORT).get();
        assertEquals(IslStatus.INACTIVE, foundIsl.getStatus());
    }

    @Test
    public void shouldPutIslInMovedStatus() {
        Isl isl = createIsl();
        islService.createOrUpdateIsl(isl);

        Isl foundIsl = islRepository.findByEndpoints(TEST_SWITCH_A_ID, TEST_SWITCH_A_PORT,
                TEST_SWITCH_B_ID, TEST_SWITCH_B_PORT).get();
        foundIsl.setActualStatus(IslStatus.INACTIVE);
        foundIsl.setStatus(IslStatus.INACTIVE);
        islRepository.createOrUpdate(foundIsl);

        Isl reverseIsl = createReverseIsl(isl);
        islService.createOrUpdateIsl(reverseIsl);

        isl.setStatus(IslStatus.MOVED);
        boolean updated = islService.islDiscoveryFailed(isl);
        assertTrue(updated);

        foundIsl = islRepository.findByEndpoints(TEST_SWITCH_A_ID, TEST_SWITCH_A_PORT,
                TEST_SWITCH_B_ID, TEST_SWITCH_B_PORT).get();
        assertEquals(IslStatus.MOVED, foundIsl.getStatus());
    }

    @Test
    public void shouldNotFailIslDiscoverOnNotActiveIsl() {
        Isl isl = createIsl();
        isl.setStatus(IslStatus.INACTIVE);
        islService.createOrUpdateIsl(isl);

        Isl foundIsl = islRepository.findByEndpoints(TEST_SWITCH_A_ID, TEST_SWITCH_A_PORT,
                TEST_SWITCH_B_ID, TEST_SWITCH_B_PORT).get();
        assertNotNull(foundIsl);
        assertEquals(IslStatus.INACTIVE, foundIsl.getStatus());

        boolean updated = islService.islDiscoveryFailed(isl);
        assertFalse(updated);
    }

    private Isl createIsl() {
        Isl isl = new Isl();
        isl.setSrcSwitch(createSwitchIfNotExist(TEST_SWITCH_A_ID));
        isl.setSrcPort(TEST_SWITCH_A_PORT);
        isl.setDestSwitch(createSwitchIfNotExist(TEST_SWITCH_B_ID));
        isl.setDestPort(TEST_SWITCH_B_PORT);

        return isl;
    }

    private Isl createReverseIsl(Isl isl) {
        return isl.toBuilder()
                .srcSwitch(isl.getDestSwitch())
                .srcPort(isl.getDestPort())
                .destSwitch(isl.getSrcSwitch())
                .destPort(isl.getSrcPort())
                .build();
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
