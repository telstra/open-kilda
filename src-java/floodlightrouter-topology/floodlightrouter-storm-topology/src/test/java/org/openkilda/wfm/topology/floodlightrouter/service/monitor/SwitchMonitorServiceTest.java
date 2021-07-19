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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.openkilda.messaging.info.discovery.NetworkDumpSwitchData;
import org.openkilda.messaging.info.event.SwitchChangeType;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.model.SpeakerSwitchDescription;
import org.openkilda.messaging.model.SpeakerSwitchPortView;
import org.openkilda.messaging.model.SpeakerSwitchPortView.State;
import org.openkilda.messaging.model.SpeakerSwitchView;
import org.openkilda.messaging.model.SwitchAvailabilityData;
import org.openkilda.messaging.model.SwitchAvailabilityEntry;
import org.openkilda.model.IpSocketAddress;
import org.openkilda.model.SwitchConnectMode;
import org.openkilda.model.SwitchId;
import org.openkilda.stubs.ManualClock;
import org.openkilda.wfm.topology.floodlightrouter.model.RegionMappingAdd;
import org.openkilda.wfm.topology.floodlightrouter.model.RegionMappingRemove;
import org.openkilda.wfm.topology.floodlightrouter.model.RegionMappingSet;
import org.openkilda.wfm.topology.floodlightrouter.service.SwitchMonitorCarrier;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Collectors;

@RunWith(MockitoJUnitRunner.class)
public class SwitchMonitorServiceTest {
    private static final String REGION_ALPHA = "alpha";
    private static final String REGION_BETA = "beta";
    private static final SwitchId SWITCH_ALPHA = new SwitchId(1);
    private static final String NETWORK_DUMP_CORRELATION_ID = "dump-correlation-id";

    private final ManualClock clock = new ManualClock();

    @Mock
    private SwitchMonitorCarrier carrier;

    @Test
    public void regularTwoSpeakersSwitchConnectSequence() {
        SwitchMonitorService subject = makeSubject();

        final Instant t0 = clock.instant();

        // read-only connect ALPHA
        SwitchInfoData swAdd = new SwitchInfoData(SWITCH_ALPHA, SwitchChangeType.ADDED);
        subject.handleStatusUpdateNotification(swAdd, REGION_ALPHA);
        verify(carrier).regionUpdateNotification(
                eq(new RegionMappingAdd(swAdd.getSwitchId(), REGION_ALPHA, false)));
        verify(carrier).sendSwitchAvailabilityUpdateNotification(
                eq(swAdd.getSwitchId()), argThat(arg -> matchAvailabilityData(
                        SwitchAvailabilityData.builder()
                                .connection(SwitchAvailabilityEntry.builder()
                                        .regionName(REGION_ALPHA).connectMode(SwitchConnectMode.READ_ONLY)
                                        .master(false).connectedAt(t0)
                                        .build())
                                .build(),
                        arg)));
        verifyNoMoreInteractions(carrier);

        // read-write connect ALPHA
        clock.adjust(Duration.ofSeconds(1));
        final Instant t1 = clock.instant();

        SwitchInfoData swActivateAlpha = makeSwitchActivateNotification(swAdd.getSwitchId(), 1);
        subject.handleStatusUpdateNotification(swActivateAlpha, REGION_ALPHA);
        verify(carrier).sendSwitchConnectNotification(
                eq(swActivateAlpha.getSwitchId()), eq(swActivateAlpha.getSwitchView()),
                argThat(arg -> matchAvailabilityData(
                        SwitchAvailabilityData.builder()
                                .connection(SwitchAvailabilityEntry.builder()
                                        .regionName(REGION_ALPHA).connectMode(SwitchConnectMode.READ_WRITE)
                                        .master(true).connectedAt(t1)
                                        .switchAddress(swActivateAlpha.getSwitchView().getSwitchSocketAddress())
                                        .speakerAddress(swActivateAlpha.getSwitchView().getSpeakerSocketAddress())
                                        .build()).build(), arg)));
        verify(carrier).regionUpdateNotification(
                eq(new RegionMappingSet(swActivateAlpha.getSwitchId(), REGION_ALPHA, true)));
        verifyNoMoreInteractions(carrier);
        reset(carrier);

        clock.adjust(Duration.ofSeconds(1));
        final Instant t2 = clock.instant();

        // read-only connect BETA
        subject.handleStatusUpdateNotification(swAdd, REGION_BETA);
        verify(carrier).regionUpdateNotification(
                eq(new RegionMappingAdd(swAdd.getSwitchId(), REGION_BETA, false)));
        verify(carrier).sendSwitchAvailabilityUpdateNotification(
                eq(swActivateAlpha.getSwitchId()), argThat(arg -> matchAvailabilityData(
                        SwitchAvailabilityData.builder()
                                .connection(SwitchAvailabilityEntry.builder()
                                        .regionName(REGION_ALPHA).connectMode(SwitchConnectMode.READ_WRITE)
                                        .master(true).connectedAt(t1)
                                        .switchAddress(
                                                swActivateAlpha.getSwitchView().getSwitchSocketAddress())
                                        .speakerAddress(
                                                swActivateAlpha.getSwitchView().getSpeakerSocketAddress())
                                        .build())
                                .connection(SwitchAvailabilityEntry.builder()
                                        .regionName(REGION_BETA).connectMode(SwitchConnectMode.READ_ONLY)
                                        .master(false).connectedAt(t2)
                                        .build())
                                .build(), arg)));
        verifyNoMoreInteractions(carrier);
        reset(carrier);

        clock.adjust(Duration.ofSeconds(1));
        final Instant t3 = clock.instant();

        // read-write connect BETA
        SwitchInfoData swActivateBeta = makeSwitchActivateNotification(swAdd.getSwitchId(), 2);
        subject.handleStatusUpdateNotification(swActivateBeta, REGION_BETA);
        verify(carrier).sendSwitchAvailabilityUpdateNotification(
                eq(swActivateAlpha.getSwitchId()), argThat(arg -> matchAvailabilityData(
                        SwitchAvailabilityData.builder()
                                .connection(SwitchAvailabilityEntry.builder()
                                        .regionName(REGION_ALPHA).connectMode(SwitchConnectMode.READ_WRITE)
                                        .master(true).connectedAt(t1)
                                        .switchAddress(
                                                swActivateAlpha.getSwitchView().getSwitchSocketAddress())
                                        .speakerAddress(
                                                swActivateAlpha.getSwitchView().getSpeakerSocketAddress())
                                        .build())
                                .connection(SwitchAvailabilityEntry.builder()
                                        .regionName(REGION_BETA).connectMode(SwitchConnectMode.READ_WRITE)
                                        .switchAddress(swActivateBeta.getSwitchView().getSwitchSocketAddress())
                                        .speakerAddress(
                                                swActivateBeta.getSwitchView().getSpeakerSocketAddress())
                                        .master(false).connectedAt(t3)
                                        .build())
                                .build(),
                        arg)));
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void testAutomaticCleanUpStaleInactiveMonitors() {
        SwitchMonitorService subject = makeSubject();

        SwitchInfoData activate = makeSwitchActivateNotification(SWITCH_ALPHA, 1);
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

        SwitchInfoData activate = makeSwitchActivateNotification(SWITCH_ALPHA, 1);
        Assert.assertFalse(subject.isMonitorExists(activate.getSwitchId()));
        subject.handleStatusUpdateNotification(activate, REGION_ALPHA);

        verifyAutomaticCleanUp(subject, activate.getSwitchId(), false);
    }

    @Test
    public void testDisconnectTheOnlyConnection() {
        SwitchMonitorService subject = makeSubject();

        SwitchInfoData swActivate = makeSwitchActivateNotification(SWITCH_ALPHA, 1);
        subject.handleStatusUpdateNotification(swActivate, REGION_ALPHA);
        verify(carrier).regionUpdateNotification(
                eq(new RegionMappingSet(swActivate.getSwitchId(), REGION_ALPHA, true)));
        verify(carrier).sendSwitchConnectNotification(
                eq(swActivate.getSwitchId()), eq(swActivate.getSwitchView()), any(SwitchAvailabilityData.class));
        verifyNoMoreInteractions(carrier);
        reset(carrier);

        SwitchInfoData swDeactivate = new SwitchInfoData(swActivate.getSwitchId(),  SwitchChangeType.DEACTIVATED);
        subject.handleStatusUpdateNotification(swDeactivate, REGION_ALPHA);
        verify(carrier).regionUpdateNotification(
                eq(new RegionMappingRemove(swActivate.getSwitchId(), REGION_ALPHA, true)));
        verify(carrier).sendSwitchDisconnectNotification(
                eq(swDeactivate.getSwitchId()), eq(SwitchAvailabilityData.builder().build()), eq(false));
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void testDisconnectPassiveActive() {
        SwitchMonitorService subject = makeSubject();

        Instant t0 = clock.instant();
        SwitchId targetSw = SWITCH_ALPHA;
        makeConnectInRegions(subject, targetSw, REGION_ALPHA, REGION_BETA);

        // disconnect beta
        SwitchInfoData swDisconnect = new SwitchInfoData(targetSw, SwitchChangeType.DEACTIVATED);
        subject.handleStatusUpdateNotification(swDisconnect, REGION_BETA);
        SpeakerSwitchView suggestedSpeakerData = makeSwitchActivateNotification(swDisconnect.getSwitchId(), 1)
                .getSwitchView();
        verify(carrier).sendSwitchAvailabilityUpdateNotification(
                eq(swDisconnect.getSwitchId()), argThat(arg -> matchAvailabilityData(
                        SwitchAvailabilityData.builder()
                                .connection(SwitchAvailabilityEntry.builder()
                                        .regionName(REGION_ALPHA).connectMode(SwitchConnectMode.READ_WRITE)
                                        .master(true).connectedAt(t0)
                                        .switchAddress(suggestedSpeakerData.getSwitchSocketAddress())
                                        .speakerAddress(suggestedSpeakerData.getSpeakerSocketAddress())
                                        .build())
                                .build(), arg)));
        verifyNoMoreInteractions(carrier);
        reset(carrier);

        // disconnect alpha (the only RW region)
        subject.handleStatusUpdateNotification(swDisconnect, REGION_ALPHA);
        verify(carrier).regionUpdateNotification(
                eq(new RegionMappingRemove(swDisconnect.getSwitchId(), REGION_ALPHA, true)));
        verify(carrier).sendSwitchDisconnectNotification(
                eq(swDisconnect.getSwitchId()), eq(SwitchAvailabilityData.builder().build()), eq(false));
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void testDisconnectActivePassive() {
        SwitchMonitorService subject = makeSubject();

        Instant t0 = clock.instant();
        SwitchId targetSw = SWITCH_ALPHA;
        makeConnectInRegions(subject, targetSw, REGION_ALPHA, REGION_BETA);

        clock.adjust(Duration.ofSeconds(1));

        SwitchInfoData swDisconnect = new SwitchInfoData(targetSw, SwitchChangeType.DEACTIVATED);

        // disconnect active region (so it must be swapped)
        subject.handleStatusUpdateNotification(swDisconnect, REGION_ALPHA);
        verify(carrier).regionUpdateNotification(
                eq(new RegionMappingSet(swDisconnect.getSwitchId(), REGION_BETA, true)));
        SpeakerSwitchView suggestedSpeakerData = makeSwitchActivateNotification(swDisconnect.getSwitchId(), 2)
                .getSwitchView();
        verify(carrier).sendSwitchAvailabilityUpdateNotification(
                eq(swDisconnect.getSwitchId()), argThat(arg -> matchAvailabilityData(
                        SwitchAvailabilityData.builder()
                                .connection(SwitchAvailabilityEntry.builder()
                                        .regionName(REGION_BETA).connectMode(SwitchConnectMode.READ_WRITE)
                                        .master(true).connectedAt(t0)
                                        .switchAddress(suggestedSpeakerData.getSwitchSocketAddress())
                                        .speakerAddress(suggestedSpeakerData.getSpeakerSocketAddress())
                                        .build())
                                .build(), arg)));
        verifyNoMoreInteractions(carrier);
        reset(carrier);

        // disconnect last (active) region
        subject.handleStatusUpdateNotification(swDisconnect, REGION_BETA);
        verify(carrier).regionUpdateNotification(
                eq(new RegionMappingRemove(swDisconnect.getSwitchId(), REGION_BETA, true)));
        verify(carrier).sendSwitchDisconnectNotification(
                eq(swDisconnect.getSwitchId()), eq(SwitchAvailabilityData.builder().build()), eq(false));
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void testTheOnlyRegionLost() {
        SwitchMonitorService subject = makeSubject();

        SwitchInfoData swActivate = makeSwitchActivateNotification(SWITCH_ALPHA, 1);
        subject.handleStatusUpdateNotification(swActivate, REGION_ALPHA);
        verify(carrier).regionUpdateNotification(
                eq(new RegionMappingSet(swActivate.getSwitchId(), REGION_ALPHA, true)));
        verify(carrier).sendSwitchConnectNotification(
                eq(swActivate.getSwitchId()), eq(swActivate.getSwitchView()), any(SwitchAvailabilityData.class));
        verifyNoMoreInteractions(carrier);
        reset(carrier);

        clock.adjust(Duration.ofSeconds(1));

        subject.handleRegionOfflineNotification(REGION_ALPHA);
        verify(carrier).regionUpdateNotification(
                eq(new RegionMappingRemove(swActivate.getSwitchId(), REGION_ALPHA, true)));
        verify(carrier).sendSwitchDisconnectNotification(
                eq(swActivate.getSwitchId()), eq(SwitchAvailabilityData.builder().build()), eq(true));
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void testLosePassiveRegion() {
        SwitchMonitorService subject = makeSubject();

        Instant t0 = clock.instant();
        SwitchId targetSw = SWITCH_ALPHA;
        makeConnectInRegions(subject, targetSw, REGION_ALPHA, REGION_BETA);

        clock.adjust(Duration.ofSeconds(1));

        subject.handleRegionOfflineNotification(REGION_BETA);
        SpeakerSwitchView suggestedAlphaSpeakerData = makeSwitchActivateNotification(targetSw, 1)
                .getSwitchView();
        verify(carrier).sendSwitchAvailabilityUpdateNotification(
                eq(targetSw), argThat(arg -> matchAvailabilityData(
                        SwitchAvailabilityData.builder()
                                .connection(SwitchAvailabilityEntry.builder()
                                        .regionName(REGION_ALPHA).connectMode(SwitchConnectMode.READ_WRITE)
                                        .master(true).connectedAt(t0)
                                        .switchAddress(suggestedAlphaSpeakerData.getSwitchSocketAddress())
                                        .speakerAddress(suggestedAlphaSpeakerData.getSpeakerSocketAddress())
                                        .build())
                                .build(), arg)));
        verifyNoMoreInteractions(carrier);
        reset(carrier);

        clock.adjust(Duration.ofSeconds(1));
        Instant t1 = clock.instant();
        SpeakerSwitchView suggestedBetaSpeakerData = makeSwitchActivateNotification(targetSw, 2)
                .getSwitchView();
        NetworkDumpSwitchData dumpBeta = new NetworkDumpSwitchData(
                suggestedBetaSpeakerData, NETWORK_DUMP_CORRELATION_ID, true);
        subject.handleNetworkDumpResponse(dumpBeta, REGION_BETA);
        verify(carrier).sendSwitchAvailabilityUpdateNotification(
                eq(targetSw), argThat(arg -> matchAvailabilityData(
                        SwitchAvailabilityData.builder()
                                .connection(SwitchAvailabilityEntry.builder()
                                        .regionName(REGION_ALPHA).connectMode(SwitchConnectMode.READ_WRITE)
                                        .master(true).connectedAt(t0)
                                        .switchAddress(suggestedAlphaSpeakerData.getSwitchSocketAddress())
                                        .speakerAddress(suggestedAlphaSpeakerData.getSpeakerSocketAddress())
                                        .build())
                                .connection(SwitchAvailabilityEntry.builder()
                                        .regionName(REGION_BETA).connectMode(SwitchConnectMode.READ_WRITE)
                                        .master(false).connectedAt(t1)
                                        .switchAddress(suggestedBetaSpeakerData.getSwitchSocketAddress())
                                        .speakerAddress(suggestedBetaSpeakerData.getSpeakerSocketAddress())
                                        .build())
                                .build(), arg)));
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void testLoseActiveRegion() {
        SwitchMonitorService subject = makeSubject();

        Instant t0 = clock.instant();
        SwitchId targetSw = SWITCH_ALPHA;
        makeConnectInRegions(subject, targetSw, REGION_ALPHA, REGION_BETA);

        clock.adjust(Duration.ofSeconds(1));

        subject.handleRegionOfflineNotification(REGION_ALPHA);
        verify(carrier).regionUpdateNotification(
                eq(new RegionMappingSet(targetSw, REGION_BETA, true)));
        SpeakerSwitchView suggestedBetaSpeakerData = makeSwitchActivateNotification(targetSw, 2)
                .getSwitchView();
        verify(carrier).sendSwitchAvailabilityUpdateNotification(
                eq(targetSw), argThat(arg -> matchAvailabilityData(
                        SwitchAvailabilityData.builder()
                                .connection(SwitchAvailabilityEntry.builder()
                                        .regionName(REGION_BETA).connectMode(SwitchConnectMode.READ_WRITE)
                                        .master(true).connectedAt(t0)
                                        .switchAddress(suggestedBetaSpeakerData.getSwitchSocketAddress())
                                        .speakerAddress(suggestedBetaSpeakerData.getSpeakerSocketAddress())
                                        .build())
                                .build(), arg)));
        verifyNoMoreInteractions(carrier);
        reset(carrier);

        clock.adjust(Duration.ofSeconds(1));
        Instant t1 = clock.instant();

        SpeakerSwitchView suggestedAlphaSpeakerData = makeSwitchActivateNotification(targetSw, 1)
                .getSwitchView();
        NetworkDumpSwitchData dumpBeta = new NetworkDumpSwitchData(
                suggestedAlphaSpeakerData, NETWORK_DUMP_CORRELATION_ID, true);
        subject.handleNetworkDumpResponse(dumpBeta, REGION_ALPHA);
        verify(carrier).sendSwitchAvailabilityUpdateNotification(
                eq(targetSw), argThat(arg -> matchAvailabilityData(
                        SwitchAvailabilityData.builder()
                                .connection(SwitchAvailabilityEntry.builder()
                                        .regionName(REGION_ALPHA).connectMode(SwitchConnectMode.READ_WRITE)
                                        .master(false).connectedAt(t1)
                                        .switchAddress(suggestedAlphaSpeakerData.getSwitchSocketAddress())
                                        .speakerAddress(suggestedAlphaSpeakerData.getSpeakerSocketAddress())
                                        .build())
                                .connection(SwitchAvailabilityEntry.builder()
                                        .regionName(REGION_BETA).connectMode(SwitchConnectMode.READ_WRITE)
                                        .master(true).connectedAt(t0)
                                        .switchAddress(suggestedBetaSpeakerData.getSwitchSocketAddress())
                                        .speakerAddress(suggestedBetaSpeakerData.getSpeakerSocketAddress())
                                        .build())
                                .build(), arg)));
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void testSerialUpdate() {
        SwitchMonitorService subject = makeSubject();

        Instant t0 = clock.instant();
        SwitchInfoData swAlphaActivate = makeSwitchActivateNotification(SWITCH_ALPHA, 1);
        subject.handleStatusUpdateNotification(swAlphaActivate, REGION_ALPHA);

        verify(carrier).regionUpdateNotification(
                eq(new RegionMappingSet(swAlphaActivate.getSwitchId(), REGION_ALPHA, true)));
        verify(carrier).sendSwitchConnectNotification(
                eq(swAlphaActivate.getSwitchId()), eq(swAlphaActivate.getSwitchView()),
                any(SwitchAvailabilityData.class));
        verifyNoMoreInteractions(carrier);
        reset(carrier);

        NetworkDumpSwitchData dump = new NetworkDumpSwitchData(
                swAlphaActivate.getSwitchView(), NETWORK_DUMP_CORRELATION_ID, true);
        for (int i = 0; i < 5; i++) {
            clock.adjust(Duration.ofSeconds(60));
            subject.handleNetworkDumpResponse(dump, REGION_ALPHA);
            verify(carrier, times(i + 1)).sendOtherNotification(eq(dump.getSwitchId()), eq(dump));
            verifyNoMoreInteractions(carrier);
        }
        reset(carrier);

        // to ensure correct value of connectedAt field for REGION_ALPHA force sending of
        // SwitchAvailabilityUpdateNotification
        Instant t1 = clock.instant();
        SwitchInfoData swBetaActivate = makeSwitchActivateNotification(swAlphaActivate.getSwitchId(), 2);
        subject.handleStatusUpdateNotification(swBetaActivate, REGION_BETA);
        verify(carrier).sendSwitchAvailabilityUpdateNotification(
                eq(swBetaActivate.getSwitchId()), argThat(arg -> matchAvailabilityData(
                        SwitchAvailabilityData.builder()
                                .connection(SwitchAvailabilityEntry.builder()
                                        .regionName(REGION_ALPHA).connectMode(SwitchConnectMode.READ_WRITE)
                                        .master(true).connectedAt(t0)
                                        .switchAddress(swAlphaActivate.getSwitchView().getSwitchSocketAddress())
                                        .speakerAddress(swAlphaActivate.getSwitchView().getSpeakerSocketAddress())
                                        .build())
                                .connection(SwitchAvailabilityEntry.builder()
                                        .regionName(REGION_BETA).connectMode(SwitchConnectMode.READ_WRITE)
                                        .master(false).connectedAt(t1)
                                        .switchAddress(swBetaActivate.getSwitchView().getSwitchSocketAddress())
                                        .speakerAddress(swBetaActivate.getSwitchView().getSpeakerSocketAddress())
                                        .build())
                                .build(), arg)));
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

    private void makeConnectInRegions(SwitchMonitorService subject, SwitchId switchId, String... regions) {
        for (int i = 0; i < regions.length; i++) {
            SwitchInfoData activate = makeSwitchActivateNotification(switchId, i + 1);
            subject.handleStatusUpdateNotification(activate, regions[i]);
            if (i == 0) {
                verify(carrier).regionUpdateNotification(
                        eq(new RegionMappingSet(activate.getSwitchId(), regions[i], true)));
                verify(carrier).sendSwitchConnectNotification(
                        eq(activate.getSwitchId()), eq(activate.getSwitchView()),
                        any(SwitchAvailabilityData.class));
            } else {
                verify(carrier).sendSwitchAvailabilityUpdateNotification(
                        eq(switchId), any(SwitchAvailabilityData.class));
            }
            verifyNoMoreInteractions(carrier);
            reset(carrier);
        }
    }

    private SwitchMonitorService makeSubject() {
        return new SwitchMonitorService(clock, carrier);
    }

    private SwitchInfoData makeSwitchActivateNotification(SwitchId switchId, int network) {
        String swAddress = String.format("127.0.%d.2", network);
        SpeakerSwitchView speakerView = SpeakerSwitchView.builder()
                .datapath(switchId)
                .switchSocketAddress(new IpSocketAddress(swAddress, 32769))
                .speakerSocketAddress(new IpSocketAddress(String.format("127.0.%d.1", network), 6653))
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
                swAddress,
                String.format("%s %s %s",
                        speakerView.getDescription().getManufacturer(),
                        speakerView.getOfVersion(),
                        speakerView.getDescription().getSoftware()), "controller", false, speakerView);
    }

    private boolean matchAvailabilityData(SwitchAvailabilityData expect, SwitchAvailabilityData actual) {
        SwitchAvailabilityData expectSorted = sortSwitchAvailabilityData(expect);
        SwitchAvailabilityData actualSorted = sortSwitchAvailabilityData(actual);
        return Objects.equals(expectSorted, actualSorted);
    }

    private SwitchAvailabilityData sortSwitchAvailabilityData(SwitchAvailabilityData source) {
        return SwitchAvailabilityData.builder()
                .connections(source.getConnections().stream()
                        .sorted(new AvailabilityEntryComparator()).collect(Collectors.toList()))
                .build();
    }

    private static class AvailabilityEntryComparator implements Comparator<SwitchAvailabilityEntry> {
        @Override
        public int compare(SwitchAvailabilityEntry left, SwitchAvailabilityEntry right) {
            if (Objects.equals(left.getRegionName(), right.getRegionName())) {
                return left.getConnectMode().compareTo(right.getConnectMode());
            }
            return left.getRegionName().compareTo(right.getRegionName());
        }
    }
}
