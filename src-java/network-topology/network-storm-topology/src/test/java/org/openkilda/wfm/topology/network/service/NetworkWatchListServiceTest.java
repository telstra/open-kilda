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

package org.openkilda.wfm.topology.network.service;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.openkilda.model.SwitchId;
import org.openkilda.wfm.share.model.Endpoint;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;

@RunWith(MockitoJUnitRunner.class)
public class NetworkWatchListServiceTest {

    @Mock
    IWatchListCarrier carrier;

    @Before
    public void setup() {
        reset(carrier);
    }

    @org.junit.Test
    public void addWatch() {

        NetworkWatchListService s = new NetworkWatchListService(carrier, 10, 20, 30);

        s.addWatch(Endpoint.of(new SwitchId(1), 1), 1);
        s.addWatch(Endpoint.of(new SwitchId(1), 2), 1);
        s.addWatch(Endpoint.of(new SwitchId(2), 1), 2);
        s.addWatch(Endpoint.of(new SwitchId(2), 1), 2);
        s.addWatch(Endpoint.of(new SwitchId(2), 2), 3);

        assertThat(s.getEndpoints().size(), is(4));
        assertThat(s.getTimeouts().size(), is(3));

        verify(carrier, times(4)).discoveryRequest(any(Endpoint.class), anyLong());
    }

    @org.junit.Test
    public void removeWatch() {
        NetworkWatchListService s = new NetworkWatchListService(carrier, 10, 20, 30);

        s.addWatch(Endpoint.of(new SwitchId(1), 1), 1);
        s.addWatch(Endpoint.of(new SwitchId(1), 2), 1);
        s.addWatch(Endpoint.of(new SwitchId(2), 1), 11);

        assertThat(s.getEndpoints().size(), is(3));

        s.removeWatch(Endpoint.of(new SwitchId(1), 1));
        s.removeWatch(Endpoint.of(new SwitchId(1), 2));
        s.removeWatch(Endpoint.of(new SwitchId(2), 1));

        assertThat(s.getEndpoints().size(), is(0));
        assertThat(s.getTimeouts().size(), is(2));

        s.tick(100);

        assertThat(s.getTimeouts().size(), is(0));

        verify(carrier, times(3)).discoveryRequest(any(Endpoint.class), anyLong());
    }

    @org.junit.Test
    public void tick() {
        NetworkWatchListService s = new NetworkWatchListService(carrier, 10, 20, 30);

        s.addWatch(Endpoint.of(new SwitchId(1), 1), 1);
        s.addWatch(Endpoint.of(new SwitchId(1), 2), 1);
        s.addWatch(Endpoint.of(new SwitchId(2), 1), 5);
        s.addWatch(Endpoint.of(new SwitchId(2), 2), 10);

        for (int i = 0; i <= 100; i++) {
            s.tick(i);
        }
        verify(carrier, times(10)).discoveryRequest(eq(Endpoint.of(new SwitchId(1), 1)), anyLong());
        verify(carrier, times(10)).discoveryRequest(eq(Endpoint.of(new SwitchId(1), 2)), anyLong());
        verify(carrier, times(10)).discoveryRequest(eq(Endpoint.of(new SwitchId(2), 1)), anyLong());
        verify(carrier, times(10)).discoveryRequest(eq(Endpoint.of(new SwitchId(2), 2)), anyLong());
    }

    @org.junit.Test
    public void enableSlowPollFlags() {
        NetworkWatchListService s = new NetworkWatchListService(carrier, 10, 20, 30);

        s.addWatch(Endpoint.of(new SwitchId(1), 1), 1);
        s.addWatch(Endpoint.of(new SwitchId(2), 2), 1);
        s.addWatch(Endpoint.of(new SwitchId(3), 3), 1);

        s.updateExhaustedPollMode(Endpoint.of(new SwitchId(2), 2), true);
        s.updateAuxiliaryPollMode(Endpoint.of(new SwitchId(3), 3), true);

        for (int i = 0; i <= 100; i++) {
            s.tick(i);
        }

        verify(carrier, times(10)).discoveryRequest(eq(Endpoint.of(new SwitchId(1), 1)),
                longThat(time -> Arrays.asList(1L, 11L, 21L, 31L, 41L, 51L, 61L, 71L, 81L, 91L).contains(time)));
        verify(carrier, times(6)).discoveryRequest(eq(Endpoint.of(new SwitchId(2), 2)),
                longThat(time -> Arrays.asList(1L, 11L, 31L, 51L, 71L, 91L).contains(time)));
        verify(carrier, times(4)).discoveryRequest(eq(Endpoint.of(new SwitchId(3), 3)),
                longThat(time -> Arrays.asList(1L, 11L, 41L, 71L).contains(time)));
    }

    @org.junit.Test
    public void disableSlowPollFlags() {
        NetworkWatchListService s = new NetworkWatchListService(carrier, 10, 15, 30);

        s.addWatch(Endpoint.of(new SwitchId(1), 1), 1);
        s.addWatch(Endpoint.of(new SwitchId(2), 2), 1);
        s.addWatch(Endpoint.of(new SwitchId(3), 3), 1);

        s.updateExhaustedPollMode(Endpoint.of(new SwitchId(2), 2), true);
        s.updateExhaustedPollMode(Endpoint.of(new SwitchId(3), 3), true);
        s.updateAuxiliaryPollMode(Endpoint.of(new SwitchId(3), 3), true);

        int i = 0;
        for (; i <= 65; i++) {
            s.tick(i);
        }

        s.updateExhaustedPollMode(Endpoint.of(new SwitchId(2), 2), false, i);
        s.updateAuxiliaryPollMode(Endpoint.of(new SwitchId(3), 3), false, i);

        for (; i <= 100; i++) {
            s.tick(i);
        }

        verify(carrier, times(10)).discoveryRequest(eq(Endpoint.of(new SwitchId(1), 1)),
                longThat(time -> Arrays.asList(1L, 11L, 21L, 31L, 41L, 51L, 61L, 71L, 81L, 91L).contains(time)));
        verify(carrier, times(9)).discoveryRequest(eq(Endpoint.of(new SwitchId(2), 2)),
                longThat(time -> Arrays.asList(1L, 11L, 26L, 41L, 56L, 66L, 76L, 86L, 96L).contains(time)));
        verify(carrier, times(6)).discoveryRequest(eq(Endpoint.of(new SwitchId(3), 3)),
                longThat(time -> Arrays.asList(1L, 11L, 41L, 66L, 81L, 96L).contains(time)));
    }

    @org.junit.Test
    public void calculateTimeout() {
        long genericTimeout = 10L;
        long exhaustedTimeout = 15L;
        long auxiliaryTimeout = 15L;
        NetworkWatchListService s = new NetworkWatchListService(carrier, genericTimeout,
                exhaustedTimeout, auxiliaryTimeout);

        s.addWatch(Endpoint.of(new SwitchId(1), 1), 1);
        s.addWatch(Endpoint.of(new SwitchId(2), 2), 1);
        s.addWatch(Endpoint.of(new SwitchId(3), 3), 1);

        s.updateExhaustedPollMode(Endpoint.of(new SwitchId(2), 2), true);
        s.updateExhaustedPollMode(Endpoint.of(new SwitchId(3), 3), true);
        s.updateAuxiliaryPollMode(Endpoint.of(new SwitchId(3), 3), true);

        assertEquals(genericTimeout, s.calculateTimeout(Endpoint.of(new SwitchId(1), 1)));
        assertEquals(exhaustedTimeout, s.calculateTimeout(Endpoint.of(new SwitchId(2), 2)));
        assertEquals(auxiliaryTimeout, s.calculateTimeout(Endpoint.of(new SwitchId(3), 3)));
    }
}
