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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.openkilda.messaging.info.event.SwitchChangeType;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.model.SpeakerSwitchDescription;
import org.openkilda.messaging.model.SpeakerSwitchPortView;
import org.openkilda.messaging.model.SpeakerSwitchPortView.State;
import org.openkilda.messaging.model.SpeakerSwitchView;
import org.openkilda.model.SwitchId;
import org.openkilda.stubs.ManualClock;
import org.openkilda.wfm.topology.floodlightrouter.model.RegionMappingAdd;
import org.openkilda.wfm.topology.floodlightrouter.model.RegionMappingSet;
import org.openkilda.wfm.topology.floodlightrouter.service.SwitchMonitorCarrier;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Collections;

@RunWith(MockitoJUnitRunner.class)
public class SwitchMonitorServiceTest {
    private static final String REGION_ALPHA = "alpha";
    private static final SwitchId SWITCH_ALPHA = new SwitchId(1);

    private final ManualClock clock = new ManualClock();

    @Mock
    private SwitchMonitorCarrier carrier;

    @Test
    public void regularSwitchConnectSequence() {
        SwitchMonitorService subject = makeSubject();

        SwitchInfoData swAdd = new SwitchInfoData(SWITCH_ALPHA, SwitchChangeType.ADDED);
        subject.handleStatusUpdateNotification(swAdd, REGION_ALPHA);
        verify(carrier).regionUpdateNotification(
                eq(new RegionMappingAdd(swAdd.getSwitchId(), REGION_ALPHA, false)));
        verifyNoMoreInteractions(carrier);

        SwitchInfoData swActivate = makeSwitchActivateNotification(swAdd.getSwitchId());
        subject.handleStatusUpdateNotification(swActivate, REGION_ALPHA);
        verify(carrier).switchStatusUpdateNotification(eq(swActivate.getSwitchId()), eq(swActivate));
        verify(carrier).regionUpdateNotification(
                eq(new RegionMappingSet(swActivate.getSwitchId(), REGION_ALPHA, true)));
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void testAutomaticCleanUpStaleInactiveMonitors() {
        SwitchMonitorService subject = makeSubject();

        SwitchInfoData activate = makeSwitchActivateNotification(SWITCH_ALPHA);
        Assert.assertFalse(subject.isMonitorExists(activate.getSwitchId()));
        subject.handleStatusUpdateNotification(activate, REGION_ALPHA);

        clock.adjust(Duration.ofMillis(1));
        SwitchInfoData deactivate = new SwitchInfoData(activate.getSwitchId(), SwitchChangeType.DEACTIVATED);
        subject.handleStatusUpdateNotification(deactivate, REGION_ALPHA);

        verifyAutomaticCleanUp(subject, activate.getSwitchId(), true);
    }

    @Test
    public void testPreserveFromCleanUpStaleActiveMonitors() {
        SwitchMonitorService subject = makeSubject();

        SwitchInfoData activate = makeSwitchActivateNotification(SWITCH_ALPHA);
        Assert.assertFalse(subject.isMonitorExists(activate.getSwitchId()));
        subject.handleStatusUpdateNotification(activate, REGION_ALPHA);

        verifyAutomaticCleanUp(subject, activate.getSwitchId(), false);
    }

    private void verifyAutomaticCleanUp(SwitchMonitorService subject, SwitchId switchId, boolean expectRemoval) {
        Assert.assertTrue(subject.isMonitorExists(switchId));

        clock.adjust(subject.getGarbageDelay().minus(Duration.ofSeconds(1)));
        subject.handleTimerTick();
        Assert.assertTrue(subject.isMonitorExists(switchId));

        clock.adjust(Duration.ofSeconds(2));
        subject.handleTimerTick();
        if (expectRemoval) {
            Assert.assertFalse(subject.isMonitorExists(switchId));
        } else {
            Assert.assertTrue(subject.isMonitorExists(switchId));
        }
    }

    private SwitchMonitorService makeSubject() {
        return new SwitchMonitorService(clock, carrier);
    }

    private SwitchInfoData makeSwitchActivateNotification(SwitchId switchId) {
        SpeakerSwitchView speakerView = SpeakerSwitchView.builder()
                .datapath(switchId)
                .switchSocketAddress(new InetSocketAddress("127.0.1.2", 32769))
                .speakerSocketAddress(new InetSocketAddress("127.0.1.1", 6653))
                .ofVersion("OF_13")
                .description(SpeakerSwitchDescription.builder()
                        .manufacturer("manufacturer")
                        .hardware("hardware")
                        .software("software")
                        .serialNumber("1")
                        .datapath(switchId.toString())
                        .build())
                .features(Collections.emptySet())
                .port(SpeakerSwitchPortView.builder().number(1).state(State.UP).build())
                .port(SpeakerSwitchPortView.builder().number(2).state(State.DOWN).build())
                .build();
        return new SwitchInfoData(
                SWITCH_ALPHA, SwitchChangeType.ACTIVATED,
                speakerView.getSwitchSocketAddress().getAddress().toString(),
                speakerView.getSwitchSocketAddress().getHostName(),
                String.format("%s %s %s",
                        speakerView.getDescription().getManufacturer(),
                        speakerView.getOfVersion(),
                        speakerView.getDescription().getSoftware()), "controller", false, speakerView);
    }
}
