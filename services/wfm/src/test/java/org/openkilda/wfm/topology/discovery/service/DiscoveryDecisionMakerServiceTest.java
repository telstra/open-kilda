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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.openkilda.messaging.info.event.IslChangeType;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.discovery.model.Endpoint;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class DiscoveryDecisionMakerServiceTest {
    private static final Endpoint endpointAlpha = Endpoint.of(new SwitchId(1), 1);
    private static final Endpoint endpointBeta = Endpoint.of(new SwitchId(2), 1);
    private static final IslInfoData islAlphaBeta = new IslInfoData(
            new PathNode(endpointAlpha.getDatapath(), endpointAlpha.getPortNumber(), 0),
            new PathNode(endpointAlpha.getDatapath(), endpointAlpha.getPortNumber(), 0),
            IslChangeType.DISCOVERED, false);
    private static final IslInfoData islBetaAlpha = new IslInfoData(
            new PathNode(endpointBeta.getDatapath(), endpointBeta.getPortNumber(), 0),
            new PathNode(endpointAlpha.getDatapath(), endpointAlpha.getPortNumber(), 0),
            IslChangeType.DISCOVERED, false);

    @Mock
    IDecisionMakerCarrier carrier;

    @Before
    public void setup() {
        reset(carrier);
    }

    @Test
    public void discovered() {
        DiscoveryDecisionMakerService w = new DiscoveryDecisionMakerService(carrier, 10, 5);

        w.discovered(endpointBeta, 1L, islAlphaBeta, 1L);
        w.discovered(endpointAlpha, 2L, islBetaAlpha, 2L);
        verify(carrier).linkDiscovered(eq(islAlphaBeta));
        verify(carrier).linkDiscovered(eq(islBetaAlpha));
    }

    @Test
    public void failed() {
        DiscoveryDecisionMakerService w = new DiscoveryDecisionMakerService(carrier, 10, 5);
        w.failed(endpointAlpha, 0L, 0);
        w.failed(endpointAlpha, 1L, 1);
        w.failed(endpointAlpha, 2L, 4);
        verify(carrier, never()).linkDestroyed(any(Endpoint.class));
        w.failed(endpointAlpha, 3L, 5);
        verify(carrier).linkDestroyed(eq(endpointAlpha));

        w.discovered(endpointAlpha, 4L, islBetaAlpha, 20);
        verify(carrier).linkDiscovered(islBetaAlpha);

        reset(carrier);
        w.failed(endpointAlpha, 5L, 21);
        w.failed(endpointAlpha, 6L, 23);
        w.failed(endpointAlpha, 7L, 24);
        verify(carrier, never()).linkDestroyed(any(Endpoint.class));
        w.failed(endpointAlpha, 8L, 31);
        verify(carrier).linkDestroyed(eq(endpointAlpha));
    }

    @Test
    public void tickerTest() {
        DiscoveryDecisionMakerService w = new DiscoveryDecisionMakerService(carrier, 10, 5);
        w.failed(endpointAlpha, 0L, 0);
        w.tick(1);
        w.tick(4);
        verify(carrier, never()).linkDestroyed(any(Endpoint.class));
        w.tick(5);
        verify(carrier).linkDestroyed(eq(endpointAlpha));

        w.discovered(endpointAlpha, 1L, islBetaAlpha, 20);
        verify(carrier).linkDiscovered(islBetaAlpha);

        reset(carrier);
        w.failed(endpointAlpha, 2L, 21);
        w.tick(23);
        w.failed(endpointAlpha, 3L, 24);
        verify(carrier, never()).linkDestroyed(any(Endpoint.class));
        w.tick(31);
        verify(carrier).linkDestroyed(eq(endpointAlpha));
    }

    @Test
    public void removeWatchTest() {
        DiscoveryDecisionMakerService w = new DiscoveryDecisionMakerService(carrier, 10, 5);

        w.discovered(endpointAlpha, 0L, islBetaAlpha, 0);
        w.discovered(endpointAlpha, 1L, islBetaAlpha, 0);
        verify(carrier, times(2)).linkDiscovered(islBetaAlpha);

        reset(carrier);
        w.clear(endpointAlpha);
        w.discovered(endpointAlpha, 2L, islBetaAlpha, 0);
        verify(carrier, only()).linkDiscovered(islBetaAlpha);
    }

    @Test
    public void ensureDiscoveryResetFailTime() {
        DiscoveryDecisionMakerService w = new DiscoveryDecisionMakerService(carrier, 10, 5);

        // we have discovered port (discovery discovery.interval is 3, and discovery.packet.ttl is 5)
        w.discovered(endpointAlpha, 1L, islBetaAlpha, 0);
        // send packet at 3
        // send packet at 6
        w.failed(endpointAlpha, 2L, 8); // 3 + 5
        // send packet at 9
        w.discovered(endpointAlpha, 4L, islBetaAlpha, 9); // 9 + 0
        w.failed(endpointAlpha, 3L, 11); // 6 + 3 (should be ignored)
        // send packet at 12
        w.failed(endpointAlpha, 5L, 17); // 12 + 3

        // we should not fail until 9 + 10 (discovery.timeout)
        w.tick(21);
        verify(carrier, never()).linkDestroyed(any(Endpoint.class));

        // but we should fail at 9 + 10
        w.tick(22);
        verify(carrier).linkDestroyed(endpointAlpha);
    }
}
