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

package org.openkilda.wfm.topology.network.service;

import static org.hamcrest.CoreMatchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.openkilda.messaging.command.discovery.DiscoverIslCommandData;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.share.model.Endpoint;

import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class NetworkWatcherServiceTest {
    private final Integer taskId = 0;

    @Mock
    IWatcherCarrier carrier;

    @BeforeEach
    public void setup() {
        reset(carrier);
    }

    @Test
    public void addWatch() {
        NetworkWatcherService w = makeService();
        w.addWatch(Endpoint.of(new SwitchId(1), 1), 1);
        w.addWatch(Endpoint.of(new SwitchId(1), 2), 1);
        w.addWatch(Endpoint.of(new SwitchId(2), 1), 2);
        w.addWatch(Endpoint.of(new SwitchId(2), 1), 2);
        w.addWatch(Endpoint.of(new SwitchId(2), 2), 3);

        MatcherAssert.assertThat(w.getConfirmedPackets().size(), is(0));
        MatcherAssert.assertThat(w.getTimeouts().size(), is(3));

        verify(carrier, times(5)).sendDiscovery(any(DiscoverIslCommandData.class));
    }

    @Test
    public void removeWatch() {
        NetworkWatcherService w = makeService();
        w.addWatch(Endpoint.of(new SwitchId(1), 1), 1);
        w.addWatch(Endpoint.of(new SwitchId(1), 2), 2);
        w.addWatch(Endpoint.of(new SwitchId(2), 1), 3);
        w.addWatch(Endpoint.of(new SwitchId(2), 2), 4);
        w.addWatch(Endpoint.of(new SwitchId(3), 1), 5);

        verify(carrier, times(5)).sendDiscovery(any(DiscoverIslCommandData.class));

        w.confirmation(Endpoint.of(new SwitchId(1), 2), 1);
        w.confirmation(Endpoint.of(new SwitchId(2), 1), 2);

        MatcherAssert.assertThat(w.getConfirmedPackets().size(), is(2));
        MatcherAssert.assertThat(w.getTimeouts().size(), is(5));
        MatcherAssert.assertThat(w.getDiscoveryPackets().size(), is(3));

        w.removeWatch(Endpoint.of(new SwitchId(1), 2));
        w.removeWatch(Endpoint.of(new SwitchId(2), 2));

        verify(carrier).clearDiscovery(Endpoint.of(new SwitchId(1), 2));
        verify(carrier).clearDiscovery(Endpoint.of(new SwitchId(2), 2));

        MatcherAssert.assertThat(w.getConfirmedPackets().size(), is(1));
        MatcherAssert.assertThat(w.getDiscoveryPackets().size(), is(2));

        w.tick(100);
        MatcherAssert.assertThat(w.getTimeouts().size(), is(0));
    }

    @Test
    public void tick() {
        NetworkWatcherService w = makeService();
        w.addWatch(Endpoint.of(new SwitchId(1), 1), 1);
        w.addWatch(Endpoint.of(new SwitchId(1), 2), 1);
        w.addWatch(Endpoint.of(new SwitchId(2), 1), 2);
        w.addWatch(Endpoint.of(new SwitchId(2), 1), 2);
        w.addWatch(Endpoint.of(new SwitchId(2), 2), 3);

        MatcherAssert.assertThat(w.getConfirmedPackets().size(), is(0));
        MatcherAssert.assertThat(w.getTimeouts().size(), is(3));
        verify(carrier, times(5)).sendDiscovery(any(DiscoverIslCommandData.class));

        w.confirmation(Endpoint.of(new SwitchId(1), 1), 0);
        w.confirmation(Endpoint.of(new SwitchId(2), 1), 2);

        MatcherAssert.assertThat(w.getConfirmedPackets().size(), is(2));

        w.tick(100);

        MatcherAssert.assertThat(w.getConfirmedPackets().size(), is(0));

        verify(carrier).discoveryFailed(eq(Endpoint.of(new SwitchId(1), 1)), eq(0L), anyLong());
        verify(carrier).discoveryFailed(eq(Endpoint.of(new SwitchId(2), 1)), eq(2L), anyLong());
        verify(carrier, times(2)).discoveryFailed(any(Endpoint.class), anyLong(), anyLong());

        MatcherAssert.assertThat(w.getTimeouts().size(), is(0));
    }

    @Test
    public void discovery() {
        NetworkWatcherService w = makeService();
        w.addWatch(Endpoint.of(new SwitchId(1), 1), 1);
        w.addWatch(Endpoint.of(new SwitchId(1), 2), 1);
        w.addWatch(Endpoint.of(new SwitchId(2), 1), 2);
        w.addWatch(Endpoint.of(new SwitchId(2), 1), 2);
        w.addWatch(Endpoint.of(new SwitchId(2), 2), 3);

        MatcherAssert.assertThat(w.getConfirmedPackets().size(), is(0));
        MatcherAssert.assertThat(w.getTimeouts().size(), is(3));
        verify(carrier, times(5)).sendDiscovery(any(DiscoverIslCommandData.class));

        w.confirmation(Endpoint.of(new SwitchId(1), 1), 0);
        w.confirmation(Endpoint.of(new SwitchId(2), 1), 2);
        MatcherAssert.assertThat(w.getConfirmedPackets().size(), is(2));

        PathNode source = new PathNode(new SwitchId(1), 1, 0);
        PathNode destination = new PathNode(new SwitchId(2), 1, 0);

        IslInfoData islAlphaBeta = IslInfoData.builder().source(source).destination(destination).packetId(0L).build();
        IslInfoData islBetaAlpha = IslInfoData.builder().source(destination).destination(source).packetId(2L).build();
        w.discovery(islAlphaBeta);
        w.discovery(islBetaAlpha);

        w.tick(100);

        MatcherAssert.assertThat(w.getConfirmedPackets().size(), is(0));

        verify(carrier).oneWayDiscoveryReceived(eq(new Endpoint(islAlphaBeta.getSource())), eq(0L), eq(islAlphaBeta),
                anyLong());
        verify(carrier).oneWayDiscoveryReceived(eq(new Endpoint(islBetaAlpha.getSource())), eq(2L), eq(islBetaAlpha),
                anyLong());
        verify(carrier, times(2))
                .oneWayDiscoveryReceived(any(Endpoint.class), anyLong(), any(IslInfoData.class), anyLong());

        MatcherAssert.assertThat(w.getTimeouts().size(), is(0));
    }

    @Test
    public void discoveryBeforeConfirmation() {
        final int awaitTime = 10;
        PathNode source = new PathNode(new SwitchId(1), 1, 0);
        PathNode destination = new PathNode(new SwitchId(2), 1, 0);

        NetworkWatcherService w = makeService(awaitTime);
        w.addWatch(Endpoint.of(source.getSwitchId(), source.getPortNo()), 1);

        verify(carrier, times(1)).sendDiscovery(any(DiscoverIslCommandData.class));

        IslInfoData islAlphaBeta = IslInfoData.builder().source(source).destination(destination).packetId(0L).build();

        w.discovery(islAlphaBeta);
        w.confirmation(new Endpoint(source), 0);
        w.tick(awaitTime + 1);

        verify(carrier).oneWayDiscoveryReceived(eq(new Endpoint(source)), eq(0L), eq(islAlphaBeta), anyLong());
        verify(carrier, never()).discoveryFailed(eq(new Endpoint(source)), anyLong(), anyLong());

        MatcherAssert.assertThat(w.getConfirmedPackets().size(), is(0));
    }

    private NetworkWatcherService makeService() {
        return makeService(10);
    }

    private NetworkWatcherService makeService(int awaitTime) {
        return new NetworkWatcherService(carrier, awaitTime, taskId);
    }
}
