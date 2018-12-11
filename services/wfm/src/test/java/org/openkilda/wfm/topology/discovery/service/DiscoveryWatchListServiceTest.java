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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.discovery.model.Endpoint;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class DiscoveryWatchListServiceTest {

    @Mock
    IWatchListCarrier carrier;

    @Before
    public void setup() {
        reset(carrier);
    }

    @org.junit.Test
    public void addWatch() {

        DiscoveryWatchListService s = new DiscoveryWatchListService(10);

        s.addWatch(carrier, Endpoint.of(new SwitchId(1), 1), 1);
        s.addWatch(carrier, Endpoint.of(new SwitchId(1), 2), 1);
        s.addWatch(carrier, Endpoint.of(new SwitchId(2), 1), 2);
        s.addWatch(carrier, Endpoint.of(new SwitchId(2), 1), 2);
        s.addWatch(carrier, Endpoint.of(new SwitchId(2), 2), 3);

        assertThat(s.getEndpoints().size(), is(4));
        assertThat(s.getTimeouts().size(), is(3));

        verify(carrier, times(4)).discoveryRequest(any(Endpoint.class), anyLong());
    }

    @org.junit.Test
    public void removeWatch() {
        DiscoveryWatchListService s = new DiscoveryWatchListService(10);

        s.addWatch(carrier, Endpoint.of(new SwitchId(1), 1), 1);
        s.addWatch(carrier, Endpoint.of(new SwitchId(1), 2), 1);
        s.addWatch(carrier, Endpoint.of(new SwitchId(2), 1), 11);

        assertThat(s.getEndpoints().size(), is(3));

        s.removeWatch(carrier, Endpoint.of(new SwitchId(1), 1));
        s.removeWatch(carrier, Endpoint.of(new SwitchId(1), 2));
        s.removeWatch(carrier, Endpoint.of(new SwitchId(2), 1));

        assertThat(s.getEndpoints().size(), is(0));
        assertThat(s.getTimeouts().size(), is(2));

        s.tick(carrier, 100);

        assertThat(s.getTimeouts().size(), is(0));

        verify(carrier, times(3)).discoveryRequest(any(Endpoint.class), anyLong());
    }

    @org.junit.Test
    public void tick() {
        DiscoveryWatchListService s = new DiscoveryWatchListService(10);

        s.addWatch(carrier, Endpoint.of(new SwitchId(1), 1), 1);
        s.addWatch(carrier, Endpoint.of(new SwitchId(1), 2), 1);
        s.addWatch(carrier, Endpoint.of(new SwitchId(2), 1), 5);
        s.addWatch(carrier, Endpoint.of(new SwitchId(2), 2), 10);

        for (int i = 0; i <= 100; i++) {
            s.tick(carrier, i);
        }
        verify(carrier, times(10)).discoveryRequest(eq(Endpoint.of(new SwitchId(1), 1)), anyLong());
        verify(carrier, times(10)).discoveryRequest(eq(Endpoint.of(new SwitchId(1), 2)), anyLong());
        verify(carrier, times(10)).discoveryRequest(eq(Endpoint.of(new SwitchId(2), 1)), anyLong());
        verify(carrier, times(10)).discoveryRequest(eq(Endpoint.of(new SwitchId(2), 2)), anyLong());
    }
}
