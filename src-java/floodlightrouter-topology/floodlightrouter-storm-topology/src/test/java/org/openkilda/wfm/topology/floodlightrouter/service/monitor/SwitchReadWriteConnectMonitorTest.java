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

package org.openkilda.wfm.topology.floodlightrouter.service.monitor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.openkilda.messaging.info.event.SwitchChangeType;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.model.SwitchId;
import org.openkilda.stubs.ManualClock;
import org.openkilda.wfm.topology.floodlightrouter.model.RegionMappingRemove;
import org.openkilda.wfm.topology.floodlightrouter.model.RegionMappingSet;
import org.openkilda.wfm.topology.floodlightrouter.service.SwitchMonitorCarrier;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Duration;
import java.time.Instant;

@RunWith(MockitoJUnitRunner.class)
public abstract class SwitchReadWriteConnectMonitorTest {
    protected final ManualClock clock = new ManualClock();

    protected static final SwitchId SWITCH_ALPHA = new SwitchId(1);

    protected static final String REGION_A = "region-A";
    protected static final String REGION_B = "region-B";

    @Mock
    protected SwitchMonitorCarrier carrier;

    @Test
    public void multipleConnections() {
        SwitchReadWriteConnectMonitor subject = makeSubject(SWITCH_ALPHA);
        SwitchInfoData connectEvent = makeConnectNotification(SWITCH_ALPHA);

        subject.handleSwitchStatusNotification(connectEvent, REGION_A);
        verify(carrier, times(1))
                .networkStatusUpdateNotification(eq(connectEvent.getSwitchId()), eq(connectEvent));
        verify(carrier).regionUpdateNotification(
                eq(new RegionMappingSet(connectEvent.getSwitchId(), REGION_A, subject.isReadWriteMode())));

        subject.handleSwitchStatusNotification(connectEvent, REGION_B);
        verify(carrier, times(1))
                .networkStatusUpdateNotification(eq(connectEvent.getSwitchId()), eq(connectEvent));
        verify(carrier, never()).regionUpdateNotification(
                eq(new RegionMappingSet(connectEvent.getSwitchId(), REGION_B, subject.isReadWriteMode())));
    }

    @Test
    public void disconnectOneOfOneConnections() {
        SwitchReadWriteConnectMonitor subject = makeSubject(SWITCH_ALPHA);
        SwitchInfoData connectEvent = makeConnectNotification(SWITCH_ALPHA);

        subject.handleSwitchStatusNotification(connectEvent, REGION_A);
        verify(carrier).networkStatusUpdateNotification(eq(connectEvent.getSwitchId()), eq(connectEvent));
        verify(carrier).regionUpdateNotification(
                eq(new RegionMappingSet(connectEvent.getSwitchId(), REGION_A, subject.isReadWriteMode())));

        Instant now = clock.adjust(Duration.ofSeconds(1));

        Assert.assertTrue(subject.isAvailable());

        SwitchInfoData disconnectEvent = makeDisconnectNotification(connectEvent.getSwitchId());
        subject.handleSwitchStatusNotification(disconnectEvent, REGION_A);
        verify(carrier).networkStatusUpdateNotification(
                eq(disconnectEvent.getSwitchId()), ArgumentMatchers.eq(disconnectEvent));
        Assert.assertFalse(subject.isAvailable());
        Assert.assertEquals(now, subject.getBecomeUnavailableAt());
    }

    @Test
    public void disconnectTwoOfTwoConnections() {
        SwitchReadWriteConnectMonitor subject = makeSubject(SWITCH_ALPHA);
        SwitchInfoData connectEvent = makeConnectNotification(SWITCH_ALPHA);

        subject.handleSwitchStatusNotification(connectEvent, REGION_A);
        subject.handleSwitchStatusNotification(connectEvent, REGION_B);

        Assert.assertTrue(subject.isAvailable());
        SwitchInfoData disconnectEvent = makeDisconnectNotification(connectEvent.getSwitchId());

        clock.adjust(Duration.ofSeconds(1));
        subject.handleSwitchStatusNotification(disconnectEvent, REGION_A);
        // change active region
        verify(carrier).regionUpdateNotification(
                eq(new RegionMappingSet(disconnectEvent.getSwitchId(), REGION_B, subject.isReadWriteMode())));
        reset(carrier);

        Instant failedAt = clock.adjust(Duration.ofSeconds(1));
        subject.handleSwitchStatusNotification(disconnectEvent, REGION_B);
        Assert.assertFalse(subject.isAvailable());
        Assert.assertEquals(failedAt, subject.getBecomeUnavailableAt());

        verify(carrier).networkStatusUpdateNotification(eq(disconnectEvent.getSwitchId()), eq(disconnectEvent));
        verify(carrier).regionUpdateNotification(
                eq(new RegionMappingRemove(disconnectEvent.getSwitchId(), null, subject.isReadWriteMode())));
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void noRegionUpdateOnSecondaryConnectionFlap() {
        SwitchConnectMonitor subject = makeSubject(SWITCH_ALPHA);
        SwitchInfoData connectEvent = makeConnectNotification(SWITCH_ALPHA);

        subject.handleSwitchStatusNotification(connectEvent, REGION_A);
        subject.handleSwitchStatusNotification(connectEvent, REGION_B);

        reset(carrier);
        SwitchInfoData disconnectEvent = makeDisconnectNotification(connectEvent.getSwitchId());
        subject.handleSwitchStatusNotification(disconnectEvent, REGION_B);
        verify(carrier, never()).regionUpdateNotification(any());

        subject.handleSwitchStatusNotification(connectEvent, REGION_B);
        verify(carrier, never()).regionUpdateNotification(any());
    }

    private SwitchReadWriteConnectMonitor makeSubject(SwitchId switchId) {
        return new SwitchReadWriteConnectMonitor(carrier, clock, switchId);
    }

    private SwitchInfoData makeConnectNotification(SwitchId switchId) {
        return new SwitchInfoData(switchId, SwitchChangeType.ADDED);
    }

    private SwitchInfoData makeDisconnectNotification(SwitchId switchId) {
        return new SwitchInfoData(switchId, SwitchChangeType.REMOVED);
    }
}
