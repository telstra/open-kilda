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

package org.openkilda.wfm.topology.discovery.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.discovery.controller.AntiFlapFsm;
import org.openkilda.wfm.topology.discovery.model.Endpoint;
import org.openkilda.wfm.topology.discovery.model.LinkStatus;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class DiscoveryAntiFlapServiceTest {

    @Mock
    private IAntiFlapCarrier carrier;

    private final SwitchId alphaDatapath = new SwitchId(1);
    private final Endpoint endpoint1 = Endpoint.of(alphaDatapath, 1);


    @Before
    public void setup() {
        resetMocks();
    }

    private void resetMocks() {
        reset(carrier);
    }

    @Test
    public void happyPath() {

        AntiFlapFsm.Config config = AntiFlapFsm.Config.builder()
                .endpoint(endpoint1)
                .delayMin(1000)
                .delayWarmUp(5000)
                .delayCoolingDown(5000)
                .build();

        DiscoveryAntiFlapService service = new DiscoveryAntiFlapService(carrier, config);

        service.filterLinkStatus(endpoint1, LinkStatus.DOWN, 100);
        service.filterLinkStatus(endpoint1, LinkStatus.UP, 110);
        // delayCoolingDown + firs event + 1
        service.tick(5000 + 100 + 1);

        verify(carrier, never()).filteredLinkStatus(endpoint1, LinkStatus.UP);
        verify(carrier, never()).filteredLinkStatus(endpoint1, LinkStatus.DOWN);

        resetMocks();

        service.filterLinkStatus(endpoint1, LinkStatus.UP, 6000);

        verify(carrier).filteredLinkStatus(endpoint1, LinkStatus.UP);

        //System.out.println(mockingDetails(carrier).printInvocations());
    }


    @Test
    public void fastPortDown() {

        AntiFlapFsm.Config config = AntiFlapFsm.Config.builder()
                .endpoint(endpoint1)
                .delayMin(1000)
                .delayWarmUp(5000)
                .delayCoolingDown(5000)
                .build();

        DiscoveryAntiFlapService service = new DiscoveryAntiFlapService(carrier, config);

        service.filterLinkStatus(endpoint1, LinkStatus.DOWN, 100);
        // now - last_down > delay_min
        service.tick(1000 + 100 + 1);

        verify(carrier, never()).filteredLinkStatus(endpoint1, LinkStatus.UP);
        verify(carrier).filteredLinkStatus(endpoint1, LinkStatus.DOWN);

    }

    @Test
    public void fromFlappingToNothingWithPortUp() {

        AntiFlapFsm.Config config = AntiFlapFsm.Config.builder()
                .endpoint(endpoint1)
                .delayMin(1000)
                .delayWarmUp(5000)
                .delayCoolingDown(5000)
                .build();

        DiscoveryAntiFlapService service = new DiscoveryAntiFlapService(carrier, config);

        service.filterLinkStatus(endpoint1, LinkStatus.DOWN, 100);
        // now - last_down > delay_min
        service.tick(1000 + 100 + 1);

        verify(carrier, never()).filteredLinkStatus(endpoint1, LinkStatus.UP);
        verify(carrier).filteredLinkStatus(endpoint1, LinkStatus.DOWN);

        resetMocks();

        service.filterLinkStatus(endpoint1, LinkStatus.UP, 2000);

        service.tick(3000);

        // Port Up
        service.tick(2000 + 5000 + 1);

        verify(carrier).filteredLinkStatus(endpoint1, LinkStatus.UP);

        // PortUp
        service.filterLinkStatus(endpoint1, LinkStatus.UP, 10000);

        verify(carrier, times(2)).filteredLinkStatus(endpoint1, LinkStatus.UP);
    }


    @Test
    public void endWarmingUp() {

        AntiFlapFsm.Config config = AntiFlapFsm.Config.builder()
                .endpoint(endpoint1)
                .delayMin(1000)
                .delayWarmUp(5000)
                .delayCoolingDown(5000)
                .build();

        DiscoveryAntiFlapService service = new DiscoveryAntiFlapService(carrier, config);

        service.filterLinkStatus(endpoint1, LinkStatus.DOWN, 100);
        service.filterLinkStatus(endpoint1, LinkStatus.UP, 100 + 5000 - 500);
        // now - last_down > delay_min
        service.tick(5101);

        verify(carrier, never()).filteredLinkStatus(endpoint1, LinkStatus.UP);
        verify(carrier).filteredLinkStatus(endpoint1, LinkStatus.DOWN);

    }
}
