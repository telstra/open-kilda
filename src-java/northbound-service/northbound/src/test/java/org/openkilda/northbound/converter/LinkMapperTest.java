/* Copyright 2020 Telstra Open Source
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

package org.openkilda.northbound.converter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.openkilda.messaging.info.event.IslChangeType;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.model.SwitchId;
import org.openkilda.northbound.dto.v1.links.LinkDto;
import org.openkilda.northbound.dto.v1.links.LinkStatus;
import org.openkilda.northbound.dto.v1.links.PathDto;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
public class LinkMapperTest {
    private static final SwitchId SWITCH_ID_1 = new SwitchId(1);
    private static final SwitchId SWITCH_ID_2 = new SwitchId(2);
    public static final int PORT_NUMBER_1 = 1;
    public static final int PORT_NUMBER_2 = 2;
    public static final int LATENCY = 1;
    public static final int SPEED = 3;
    public static final int AVAILABLE_BANDWIDTH = 4;
    public static final int MAX_BANDWIDTH = 5;
    public static final int DEFAULT_MAX_BANDWIDTH = 6;
    public static final int COST = 7;
    public static final long TIME_CREATE_MILLIS = 8L;
    public static final long TIME_MODIFY_MILLIS = 9L;
    public static final String BFD_SESSION_STATUS = "UP";
    public static final long PACKET_ID = 10L;

    @Autowired
    private LinkMapper linkMapper;

    @Test
    public void testMapResponse() {
        PathNode source = new PathNode(SWITCH_ID_1, PORT_NUMBER_1, 0);
        PathNode destination = new PathNode(SWITCH_ID_2, PORT_NUMBER_2, 0);

        IslInfoData islInfoData = new IslInfoData(
                LATENCY,
                source,
                destination,
                SPEED,
                AVAILABLE_BANDWIDTH,
                MAX_BANDWIDTH,
                DEFAULT_MAX_BANDWIDTH,
                IslChangeType.DISCOVERED,
                IslChangeType.FAILED,
                IslChangeType.MOVED,
                COST,
                TIME_CREATE_MILLIS,
                TIME_MODIFY_MILLIS,
                false,
                true,
                BFD_SESSION_STATUS,
                PACKET_ID);

        LinkDto response = linkMapper.mapResponse(islInfoData);

        assertEquals(2, response.getPath().size());
        assertPathNode(source, response.getPath().get(0));
        assertPathNode(destination, response.getPath().get(1));

        assertEquals(LATENCY, response.getLatency());
        assertEquals(SPEED, response.getSpeed());
        assertEquals(AVAILABLE_BANDWIDTH, response.getAvailableBandwidth());
        assertEquals(MAX_BANDWIDTH, response.getMaxBandwidth());
        assertEquals(DEFAULT_MAX_BANDWIDTH, response.getDefaultMaxBandwidth());
        assertEquals(LinkStatus.DISCOVERED, response.getState());
        assertEquals(LinkStatus.FAILED, response.getActualState());
        assertEquals(LinkStatus.MOVED, response.getRoundTripStatus());
        assertEquals(COST, response.getCost());
        assertFalse(response.isUnderMaintenance());
        assertTrue(response.isEnableBfd());
        assertEquals(BFD_SESSION_STATUS, response.getBfdSessionStatus());
    }

    private void assertPathNode(PathNode expected, PathDto actual) {
        assertEquals(expected.getSwitchId().toString(), actual.getSwitchId());
        assertEquals(expected.getPortNo(), actual.getPortNo());
    }

    @TestConfiguration
    @ComponentScan({"org.openkilda.northbound.converter"})
    static class Config {
        // nothing to define here
    }
}
