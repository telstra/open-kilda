/* Copyright 2023 Telstra Open Source
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.openkilda.model.LacpPartner;
import org.openkilda.model.MacAddress;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.inmemory.InMemoryGraphBasedTest;
import org.openkilda.persistence.repositories.LacpPartnerRepository;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collection;
import java.util.Optional;

public class FermaLacpPartnerRepositoryTest extends InMemoryGraphBasedTest {
    public static final SwitchId TEST_SWITCH_ID_1 = new SwitchId(1);
    public static final SwitchId TEST_SWITCH_ID_2 = new SwitchId(2);
    public static final int LOGICAL_PORT_NUMBER_1 = 1;
    public static final int LOGICAL_PORT_NUMBER_2 = 2;

    private static LacpPartnerRepository lacpPartnerRepository;

    @BeforeClass
    public static void setUp() {
        lacpPartnerRepository = repositoryFactory.createLacpPartnerRepository();
    }

    @Before
    public void init() {
        lacpPartnerRepository.add(buildLacpPartner(TEST_SWITCH_ID_1, LOGICAL_PORT_NUMBER_1));
        lacpPartnerRepository.add(buildLacpPartner(TEST_SWITCH_ID_1, LOGICAL_PORT_NUMBER_2));
        lacpPartnerRepository.add(buildLacpPartner(TEST_SWITCH_ID_2, LOGICAL_PORT_NUMBER_1));
    }

    @Test
    public void fildAllReturnsAllLacpPartnersSuccess() {
        Collection<LacpPartner> lacpPartners = lacpPartnerRepository.findAll();
        assertEquals(3, lacpPartners.size());
    }

    @Test
    public void fildBySwitchIdReturnsSwitchLacpPartnersSuccess() {
        Collection<LacpPartner> lacpPartnersBySwitch1 = lacpPartnerRepository.findBySwitchId(TEST_SWITCH_ID_1);
        assertEquals(2, lacpPartnersBySwitch1.size());

        Collection<LacpPartner> lacpPartnersBySwitch2 = lacpPartnerRepository.findBySwitchId(TEST_SWITCH_ID_2);
        assertEquals(1, lacpPartnersBySwitch2.size());
    }

    @Test
    public void fildBySwitchIdAndLogicalPortNumberReturnsLacpPartnersSuccess() {
        Optional<LacpPartner> lacpPartner = lacpPartnerRepository.findBySwitchIdAndLogicalPortNumber(TEST_SWITCH_ID_1,
                LOGICAL_PORT_NUMBER_1);
        assertTrue(lacpPartner.isPresent());

        lacpPartner = lacpPartnerRepository.findBySwitchIdAndLogicalPortNumber(TEST_SWITCH_ID_1, LOGICAL_PORT_NUMBER_2);
        assertTrue(lacpPartner.isPresent());

        lacpPartner = lacpPartnerRepository.findBySwitchIdAndLogicalPortNumber(TEST_SWITCH_ID_2, LOGICAL_PORT_NUMBER_1);
        assertTrue(lacpPartner.isPresent());
    }

    @Test
    public void deleteLacpPartner() {
        Optional<LacpPartner> lacpPartner = lacpPartnerRepository.findBySwitchIdAndLogicalPortNumber(TEST_SWITCH_ID_2,
                LOGICAL_PORT_NUMBER_1);
        assertTrue(lacpPartner.isPresent());

        Optional<LacpPartner> finalLacpPartner = lacpPartner;
        transactionManager.doInTransaction(() -> lacpPartnerRepository.remove(finalLacpPartner.get()));

        assertEquals(2, lacpPartnerRepository.findAll().size());

        lacpPartner = lacpPartnerRepository.findBySwitchIdAndLogicalPortNumber(TEST_SWITCH_ID_2, LOGICAL_PORT_NUMBER_1);
        assertFalse(lacpPartner.isPresent());
    }

    private LacpPartner buildLacpPartner(SwitchId switchId, int logicalPortNumber) {
        return LacpPartner.builder()
                .switchId(switchId)
                .logicalPortNumber(logicalPortNumber)
                .systemPriority(1)
                .systemId(new MacAddress("00:00:00:00:00:01"))
                .key(1)
                .portPriority(123)
                .portNumber(11)
                .stateActive(Boolean.TRUE)
                .stateShortTimeout(Boolean.TRUE)
                .stateAggregatable(Boolean.TRUE)
                .stateSynchronised(Boolean.TRUE)
                .stateCollecting(Boolean.TRUE)
                .stateDistributing(Boolean.TRUE)
                .stateDefaulted(Boolean.TRUE)
                .stateExpired(Boolean.FALSE)
                .build();
    }
}
