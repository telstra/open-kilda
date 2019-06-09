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

package org.openkilda.wfm.topology.isllatency.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import static org.openkilda.wfm.topology.isllatency.LatencyAction.COPY_REVERSE_ROUND_TRIP_LATENCY;
import static org.openkilda.wfm.topology.isllatency.LatencyAction.DO_NOTHING;
import static org.openkilda.wfm.topology.isllatency.LatencyAction.USE_ONE_WAY_LATENCY;

import org.openkilda.messaging.info.event.IslOneWayLatency;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.Neo4jBasedTest;
import org.openkilda.wfm.error.SwitchNotFoundException;

import org.junit.BeforeClass;
import org.junit.Test;

public class DecisionMakerServiceTest extends Neo4jBasedTest {
    private static final SwitchId CENTEC_SWITCH_ID = new SwitchId("00:00:00:00:00:00:00:01");
    private static final SwitchId ACTON_SWITCH_ID = new SwitchId("00:00:00:00:00:00:00:02");
    private static final SwitchId NOVIFLOW_SWITCH_ID = new SwitchId("00:00:00:00:00:00:00:03");
    private static final SwitchId E_NOVI_FLOW_SWITCH_ID = new SwitchId("00:00:00:00:00:00:00:04");

    private static final String CENTEC_MANUFACTURER_DESCRIPTION = "Centec";
    private static final String NOVIFLOW_MANUFACTURER_DESCRIPTION = "NoviFlow Inc";
    private static final String E_NOVIFLOW_MANUFACTURER_DESCRIPTION = "E";
    private static final String ACTON_MANUFACTURER_DESCRIPTION =
            "Sonus Networks Inc, 4 Technology Park Dr, Westford, MA 01886, USA";

    private static SwitchRepository switchRepository;
    private static DecisionMakerService dms;


    @BeforeClass
    public static void setUpOnce() {
        dms = new DecisionMakerService(persistenceManager.getRepositoryFactory());
        switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
        createSwitchWithManufacturerDescription(CENTEC_SWITCH_ID, CENTEC_MANUFACTURER_DESCRIPTION);
        createSwitchWithManufacturerDescription(ACTON_SWITCH_ID, ACTON_MANUFACTURER_DESCRIPTION);
        createSwitchWithManufacturerDescription(NOVIFLOW_SWITCH_ID, NOVIFLOW_MANUFACTURER_DESCRIPTION);
        createSwitchWithManufacturerDescription(E_NOVI_FLOW_SWITCH_ID, E_NOVIFLOW_MANUFACTURER_DESCRIPTION);
    }

    @Test
    public void isSwitchSupportsGroupPacketOutTest() throws SwitchNotFoundException {
        assertFalse(dms.isSwitchSupportsGroupPacketOut(CENTEC_SWITCH_ID));
        assertFalse(dms.isSwitchSupportsGroupPacketOut(ACTON_SWITCH_ID));
        // only NoviFlow switches support group packet out actions
        assertTrue(dms.isSwitchSupportsGroupPacketOut(E_NOVI_FLOW_SWITCH_ID));
        assertTrue(dms.isSwitchSupportsGroupPacketOut(NOVIFLOW_SWITCH_ID));
    }

    @Test
    public void handleOneWayIslLatency() {
        // Expected behaviour. For more information look at round trip latency design:
        // docs/design/round-trip-latency/round-trip-latency.md
        //
        // SC | SG | DC | DG | Action
        // ---|----|----|----|--------------------------
        //  + | +  | ?  | +  | Do nothing
        //  - | +  | +  | +  | Copy RTL from reverse ISL
        //  - | -  | +  | +  | Use one way latency
        //  + | +  | -  | -  | Use one way latency
        //  - | ?  | -  | ?  | Use one way latency

        assertEquals(DO_NOTHING, dms.handleOneWayIslLatency(createOneWayData(true, true, true, true)));
        assertEquals(DO_NOTHING, dms.handleOneWayIslLatency(createOneWayData(true, true, false, true)));
        assertEquals(COPY_REVERSE_ROUND_TRIP_LATENCY,
                dms.handleOneWayIslLatency(createOneWayData(false, true, true, true)));
        assertEquals(USE_ONE_WAY_LATENCY, dms.handleOneWayIslLatency(createOneWayData(false, false, true, true)));
        assertEquals(USE_ONE_WAY_LATENCY, dms.handleOneWayIslLatency(createOneWayData(true, true, false, false)));
        assertEquals(USE_ONE_WAY_LATENCY, dms.handleOneWayIslLatency(createOneWayData(false, true, false, true)));
        assertEquals(USE_ONE_WAY_LATENCY, dms.handleOneWayIslLatency(createOneWayData(false, false, false, true)));
        assertEquals(USE_ONE_WAY_LATENCY, dms.handleOneWayIslLatency(createOneWayData(false, true, false, false)));
        assertEquals(USE_ONE_WAY_LATENCY, dms.handleOneWayIslLatency(createOneWayData(false, false, false, false)));

    }


    private static void createSwitchWithManufacturerDescription(SwitchId switchId, String description) {
        Switch sw = new Switch();
        sw.setSwitchId(switchId);
        sw.setOfDescriptionManufacturer(description);
        if (!switchRepository.exists(sw.getSwitchId())) {
            switchRepository.createOrUpdate(sw);
        }
    }

    private static IslOneWayLatency createOneWayData(
            boolean srcCopy, boolean srcGroup, boolean dstCopy, boolean dstGroup) {
        if ((srcCopy && !srcGroup) || (dstCopy && !dstGroup)) {
            throw new IllegalArgumentException(
                    "If switch supports copy field action it must support group packet out actions");
        }

        return new IslOneWayLatency(
                srcGroup ? NOVIFLOW_SWITCH_ID : CENTEC_SWITCH_ID,
                0,
                dstGroup ? NOVIFLOW_SWITCH_ID : CENTEC_SWITCH_ID,
                0,
                1L,
                0L,
                srcCopy,
                dstCopy,
                dstGroup);
    }
}
