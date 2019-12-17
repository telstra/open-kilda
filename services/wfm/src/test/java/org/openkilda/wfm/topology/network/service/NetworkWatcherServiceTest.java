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
import static org.junit.Assert.assertThat;
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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class NetworkWatcherServiceTest {
    private final Integer taskId = 0;

    @Mock
    IWatcherCarrier carrier;

    @Before
    public void setup() {
        reset(carrier);
    }

    @Test
    public void addWatch() {
        NetworkWatcherService w = new NetworkWatcherService(carrier, 10, taskId);
        w.addWatch(Endpoint.of(new SwitchId(1), 1), 1);
        w.addWatch(Endpoint.of(new SwitchId(1), 2), 1);
        w.addWatch(Endpoint.of(new SwitchId(2), 1), 2);
        w.addWatch(Endpoint.of(new SwitchId(2), 1), 2);
        w.addWatch(Endpoint.of(new SwitchId(2), 2), 3);

        assertThat(w.getConfirmedPackets().size(), is(0));
        assertThat(w.getTimeouts().size(), is(3));

        verify(carrier, times(5)).sendDiscovery(any(DiscoverIslCommandData.class));
    }

    @Test
    public void removeWatch() {
        NetworkWatcherService w = new NetworkWatcherService(carrier, 10, taskId);
        w.addWatch(Endpoint.of(new SwitchId(1), 1), 1);
        w.addWatch(Endpoint.of(new SwitchId(1), 2), 2);
        w.addWatch(Endpoint.of(new SwitchId(2), 1), 3);
        w.addWatch(Endpoint.of(new SwitchId(2), 2), 4);
        w.addWatch(Endpoint.of(new SwitchId(3), 1), 5);

        verify(carrier, times(5)).sendDiscovery(any(DiscoverIslCommandData.class));

        w.processConfirmation(Endpoint.of(new SwitchId(1), 2), 1, true);
        w.processConfirmation(Endpoint.of(new SwitchId(2), 1), 2, true);

        assertThat(w.getConfirmedPackets().size(), is(2));
        assertThat(w.getTimeouts().size(), is(5));
        assertThat(w.getProducedPackets().size(), is(3));

        w.removeWatch(Endpoint.of(new SwitchId(1), 2));
        w.removeWatch(Endpoint.of(new SwitchId(2), 2));

        verify(carrier).clearDiscovery(Endpoint.of(new SwitchId(1), 2));
        verify(carrier).clearDiscovery(Endpoint.of(new SwitchId(2), 2));

        assertThat(w.getConfirmedPackets().size(), is(1));
        assertThat(w.getProducedPackets().size(), is(2));

        w.tick(100);
        assertThat(w.getTimeouts().size(), is(0));
    }

    @Test
    public void tick() {
        NetworkWatcherService w = new NetworkWatcherService(carrier, 10, taskId);
        w.addWatch(Endpoint.of(new SwitchId(1), 1), 1);
        w.addWatch(Endpoint.of(new SwitchId(1), 2), 1);
        w.addWatch(Endpoint.of(new SwitchId(2), 1), 2);
        w.addWatch(Endpoint.of(new SwitchId(2), 1), 2);
        w.addWatch(Endpoint.of(new SwitchId(2), 2), 3);

        assertThat(w.getConfirmedPackets().size(), is(0));
        assertThat(w.getTimeouts().size(), is(3));
        verify(carrier, times(5)).sendDiscovery(any(DiscoverIslCommandData.class));

        w.processConfirmation(Endpoint.of(new SwitchId(1), 1), 0, true);
        w.processConfirmation(Endpoint.of(new SwitchId(2), 1), 2, true);

        assertThat(w.getConfirmedPackets().size(), is(2));

        w.tick(100);

        assertThat(w.getConfirmedPackets().size(), is(0));

        verify(carrier).discoveryFailed(eq(Endpoint.of(new SwitchId(1), 1)), eq(0L), anyLong());
        verify(carrier).discoveryFailed(eq(Endpoint.of(new SwitchId(2), 1)), eq(2L), anyLong());
        verify(carrier, times(2)).discoveryFailed(any(Endpoint.class), anyLong(), anyLong());

        assertThat(w.getTimeouts().size(), is(0));
    }

    @Test
    public void discovery() {
        NetworkWatcherService w = new NetworkWatcherService(carrier, 10, taskId);
        w.addWatch(Endpoint.of(new SwitchId(1), 1), 1);
        w.addWatch(Endpoint.of(new SwitchId(1), 2), 1);
        w.addWatch(Endpoint.of(new SwitchId(2), 1), 2);
        w.addWatch(Endpoint.of(new SwitchId(2), 1), 2);
        w.addWatch(Endpoint.of(new SwitchId(2), 2), 3);

        assertThat(w.getConfirmedPackets().size(), is(0));
        assertThat(w.getTimeouts().size(), is(3));
        verify(carrier, times(5)).sendDiscovery(any(DiscoverIslCommandData.class));

        w.processConfirmation(Endpoint.of(new SwitchId(1), 1), 0, true);
        w.processConfirmation(Endpoint.of(new SwitchId(2), 1), 2, true);
        assertThat(w.getConfirmedPackets().size(), is(2));

        PathNode source = new PathNode(new SwitchId(1), 1, 0);
        PathNode destination = new PathNode(new SwitchId(2), 1, 0);

        IslInfoData islAlphaBeta = IslInfoData.builder().source(source).destination(destination).packetId(0L).build();
        IslInfoData islBetaAlpha = IslInfoData.builder().source(destination).destination(source).packetId(2L).build();
        w.discovery(islAlphaBeta);
        w.discovery(islBetaAlpha);

        w.tick(100);

        assertThat(w.getConfirmedPackets().size(), is(0));

        verify(carrier).discoveryReceived(eq(new Endpoint(islAlphaBeta.getSource())), eq(0L), eq(islAlphaBeta),
                                          anyLong());
        verify(carrier).discoveryReceived(eq(new Endpoint(islBetaAlpha.getSource())), eq(2L), eq(islBetaAlpha),
                                          anyLong());
        verify(carrier, times(2)).discoveryReceived(any(Endpoint.class), anyLong(), any(IslInfoData.class), anyLong());

        assertThat(w.getTimeouts().size(), is(0));
    }

    @Test
    public void discoveryBeforeConfirmation() {
        final int awaitTime = 10;
        PathNode source = new PathNode(new SwitchId(1), 1, 0);
        PathNode destination = new PathNode(new SwitchId(2), 1, 0);

        NetworkWatcherService w = new NetworkWatcherService(carrier, awaitTime, taskId);
        w.addWatch(Endpoint.of(source.getSwitchId(), source.getPortNo()), 1);

        verify(carrier, times(1)).sendDiscovery(any(DiscoverIslCommandData.class));

        IslInfoData islAlphaBeta = IslInfoData.builder().source(source).destination(destination).packetId(0L).build();

        w.discovery(islAlphaBeta);
        w.processConfirmation(new Endpoint(source), 0, true);
        w.tick(awaitTime + 1);

        verify(carrier).discoveryReceived(eq(new Endpoint(source)), eq(0L), eq(islAlphaBeta), anyLong());
        verify(carrier, never()).discoveryFailed(eq(new Endpoint(source)), anyLong(), anyLong());

        assertThat(w.getConfirmedPackets().size(), is(0));
    }

    @Test
    public void packetIsNotConfirmed() {
        final int awaitTime = 1000;

        NetworkWatcherService w = new NetworkWatcherService(carrier, awaitTime, taskId);
        w.addWatch(Endpoint.of(new SwitchId(1), 1), 1);

        verify(carrier, times(1)).sendDiscovery(any(DiscoverIslCommandData.class));

        w.processConfirmation(Endpoint.of(new SwitchId(1), 1), 0, false);

        assertThat(w.getConfirmedPackets().size(), is(0));
        assertThat(w.getProducedPackets().size(), is(0));
    }
}
