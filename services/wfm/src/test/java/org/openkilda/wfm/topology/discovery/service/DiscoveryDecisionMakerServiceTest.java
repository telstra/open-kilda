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
import static org.mockito.Mockito.reset;
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
        DiscoveryDecisionMakerService w = new DiscoveryDecisionMakerService(10, 5);

        w.discovered(carrier, endpointBeta, islAlphaBeta, 1L);
        w.discovered(carrier, endpointAlpha, islBetaAlpha, 2L);
        verify(carrier).linkDiscovered(eq(islAlphaBeta));
        verify(carrier).linkDiscovered(eq(islBetaAlpha));
    }

    @Test
    public void failed() {
        DiscoveryDecisionMakerService w = new DiscoveryDecisionMakerService(10, 5);
        w.failed(carrier, endpointAlpha, 0);
        w.failed(carrier, endpointAlpha, 1);
        w.failed(carrier, endpointAlpha, 4);
        verify(carrier, never()).linkDestroyed(any(Endpoint.class));
        w.failed(carrier, endpointAlpha, 5);
        verify(carrier).linkDestroyed(eq(endpointAlpha));

        w.discovered(carrier, endpointAlpha, islBetaAlpha, 20);
        verify(carrier).linkDiscovered(islBetaAlpha);

        reset(carrier);
        w.failed(carrier, endpointAlpha, 21);
        w.failed(carrier, endpointAlpha, 23);
        w.failed(carrier, endpointAlpha, 24);
        verify(carrier, never()).linkDestroyed(any(Endpoint.class));
        w.failed(carrier, endpointAlpha, 31);
        verify(carrier).linkDestroyed(eq(endpointAlpha));
    }

    @Test
    public void tickerTest() {
        DiscoveryDecisionMakerService w = new DiscoveryDecisionMakerService(10, 5);
        w.failed(carrier, endpointAlpha, 0);
        w.tick(carrier, 1);
        w.tick(carrier, 4);
        verify(carrier, never()).linkDestroyed(any(Endpoint.class));
        w.tick(carrier, 5);
        verify(carrier).linkDestroyed(eq(endpointAlpha));

        w.discovered(carrier, endpointAlpha, islBetaAlpha, 20);
        verify(carrier).linkDiscovered(islBetaAlpha);

        reset(carrier);
        w.failed(carrier, endpointAlpha, 21);
        w.tick(carrier, 23);
        w.failed(carrier, endpointAlpha, 24);
        verify(carrier, never()).linkDestroyed(any(Endpoint.class));
        w.tick(carrier, 31);
        verify(carrier).linkDestroyed(eq(endpointAlpha));
    }
}
