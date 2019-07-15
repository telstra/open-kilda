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

package org.openkilda.floodlight.command;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.getCurrentArguments;

import org.openkilda.floodlight.command.meter.MeterInstallCommand;
import org.openkilda.floodlight.command.meter.MeterInstallDryRunCommand;
import org.openkilda.floodlight.command.meter.MeterInstallReport;
import org.openkilda.floodlight.command.meter.MeterRemoveCommand;
import org.openkilda.floodlight.command.meter.MeterRemoveReport;
import org.openkilda.floodlight.service.FeatureDetectorService;
import org.openkilda.floodlight.service.session.Session;
import org.openkilda.floodlight.service.session.SessionService;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.MeterConfig;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import lombok.AllArgsConstructor;
import lombok.Value;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.SwitchDescription;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.internal.OFSwitchManager;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.easymock.EasyMockSupport;
import org.easymock.IAnswer;
import org.easymock.Mock;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.ver13.OFFactoryVer13;
import org.projectfloodlight.openflow.types.DatapathId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public abstract class AbstractSpeakerCommandTest extends EasyMockSupport {
    protected final FloodlightModuleContext moduleContext = new FloodlightModuleContext();

    protected static final OFFactory of = new OFFactoryVer13();
    protected static final DatapathId dpId = DatapathId.of(1);
    protected static final DatapathId dpIdNext = DatapathId.of(dpId.getLong() + 1);

    protected static final MeterConfig meterConfig = new MeterConfig(new MeterId(32), 1000);

    protected static final FlowTransitEncapsulation encapsulationVlan = new FlowTransitEncapsulation(
            51, FlowEncapsulationType.TRANSIT_VLAN);
    protected static final FlowTransitEncapsulation encapsulationVxLan = new FlowTransitEncapsulation(
            75, FlowEncapsulationType.VXLAN);

    protected static final FlowEndpoint endpointEgressZeroVlan = new FlowEndpoint(mapSwitchId(dpIdNext), 11, 0);
    protected static final FlowEndpoint endpointEgressOneVlan = new FlowEndpoint(mapSwitchId(dpIdNext), 12, 60);

    protected static final FlowEndpoint endpointIngressZeroVlan = new FlowEndpoint(
            mapSwitchId(dpId), 21, 0);
    protected static final FlowEndpoint endpointIngressOneVlan = new FlowEndpoint(
            mapSwitchId(dpId), 22, 70);

    private final SwitchDescription swDesc = SwitchDescription.builder()
            .setManufacturerDescription("manufacturer")
            .setSoftwareDescription("software")
            .build();

    protected final Map<DatapathId, Iterator<Session>> switchSessionProducePlan = new HashMap<>();

    @Mock
    protected SessionService sessionService;

    @Mock
    protected OFSwitchManager ofSwitchManager;

    @Mock
    protected FeatureDetectorService featureDetectorService;

    @Mock
    protected SpeakerCommandProcessor commandProcessor;

    @Mock
    protected Session session;

    @Mock
    protected IOFSwitch sw;

    @Mock
    protected IOFSwitch swNext;

    protected final List<SessionWriteRecord> writeHistory = new ArrayList<>();

    @Before
    public void setUp() throws Exception {
        injectMocks(this);

        moduleContext.addService(SessionService.class, sessionService);
        moduleContext.addService(IOFSwitchService.class, ofSwitchManager);

        expect(commandProcessor.getModuleContext()).andReturn(moduleContext).anyTimes();

        // sw
        prepareSwitch(sw, dpId);
        prepareSwitch(swNext, dpIdNext);

        moduleContext.addService(FeatureDetectorService.class, featureDetectorService);

        prepareSessionService();
        switchSessionProducePlan.put(dpId, ImmutableList.of(session).iterator());
        switchSessionProducePlan.put(dpIdNext, ImmutableList.of(session).iterator());
    }

    @After
    public void tearDown() {
        verifyAll();
    }

    protected void prepareSwitch(IOFSwitch mock, DatapathId id) {
        expect(mock.getId()).andReturn(id).anyTimes();
        expect(mock.getOFFactory()).andReturn(of).anyTimes();
        expect(ofSwitchManager.getActiveSwitch(id)).andReturn(mock).anyTimes();
    }

    protected void prepareSessionService() {
        expect(sessionService.open(anyObject(MessageContext.class), anyObject(IOFSwitch.class)))
                .andAnswer(new IAnswer<Session>() {
                    @Override
                    public Session answer() throws Throwable {
                        IOFSwitch target = (IOFSwitch) getCurrentArguments()[1];
                        Iterator<Session> producePlan = switchSessionProducePlan.get(target.getId());
                        return producePlan.next();
                    }
                });

        expect(session.write(anyObject(OFMessage.class)))
                .andAnswer(new IAnswer<CompletableFuture<Optional<OFMessage>>>() {
                    @Override
                    public CompletableFuture<Optional<OFMessage>> answer() throws Throwable {
                        SessionWriteRecord historyEntry = new SessionWriteRecord(
                                (OFMessage) (getCurrentArguments()[0]));
                        writeHistory.add(historyEntry);
                        return historyEntry.getFuture();
                    }
                })
                .anyTimes();

        session.close();
        expectLastCall();
    }

    protected void expectSwitchDescription() {
        expect(sw.getSwitchDescription()).andReturn(swDesc).anyTimes();
    }

    protected void expectMeterInstall() {
        expectMeterInstall(null);
    }

    protected void expectMeterInstall(Exception error) {
        MeterInstallAnswer answer = new MeterInstallAnswer(error);
        expect(commandProcessor.chain(anyObject(MeterInstallCommand.class)))
                .andAnswer(answer);
    }

    protected void expectMeterRemove() {
        expectMeterRemove(null);
    }

    protected void expectMeterRemove(Exception error) {
        MeterRemoveAnswer answer = new MeterRemoveAnswer(error);
        expect(commandProcessor.chain(anyObject(MeterRemoveCommand.class)))
                .andAnswer(answer);
    }

    protected void expectMeterDryRun() {
        expectMeterDryRun(null);
    }

    protected void expectMeterDryRun(Exception error) {
        MeterInstallAnswer answer = new MeterInstallAnswer(error);
        expect(commandProcessor.chain(anyObject(MeterInstallDryRunCommand.class)))
                .andAnswer(answer);
    }

    protected void verifyOfMessageEquals(OFMessage expected, OFMessage actual) {
        if (! expected.equalsIgnoreXid(actual)) {
            Assert.assertEquals(expected, actual);
        }
    }

    protected void verifySuccessCompletion(CompletableFuture<? extends SpeakerCommandReport> future) throws Exception {
        completeAllSessionWriteRequests();
        Assert.assertTrue(future.isDone());
        future.get(1, TimeUnit.SECONDS).raiseError();
    }

    protected void verifyErrorCompletion(
            CompletableFuture<? extends SpeakerCommandReport> result, Class<? extends Throwable> errorType) {
        completeAllSessionWriteRequests();
        try {
            result.get().raiseError();
            Assert.fail("must never reach this line");
        } catch (Exception e) {
            Assert.assertTrue(errorType.isAssignableFrom(e.getClass()));
        }
    }

    protected void verifyWriteCount(int count) {
        Assert.assertEquals(count, writeHistory.size());
    }

    protected void switchFeaturesSetup(IOFSwitch target, boolean metersSupport) {
        Set<SwitchFeature> features = new HashSet<>();

        if (metersSupport) {
            features.add(SwitchFeature.METERS);
        }

        switchFeaturesSetup(target, features);
    }

    protected void switchFeaturesSetup(IOFSwitch target, Set<SwitchFeature> features) {
        expect(featureDetectorService.detectSwitch(target))
                .andReturn(ImmutableSet.copyOf(features))
                .anyTimes();
    }

    protected void completeAllSessionWriteRequests() {
        for (SessionWriteRecord record : writeHistory) {
            CompletableFuture<Optional<OFMessage>> future = record.getFuture();
            if (future.isDone()) {
                continue;
            }
            future.complete(Optional.empty());
        }
    }

    protected SessionWriteRecord getWriteRecord(int idx) {
        Assert.assertTrue(idx < writeHistory.size());
        return writeHistory.get(idx);
    }

    protected static SwitchId mapSwitchId(DatapathId target) {
        return new SwitchId(target.getLong());
    }

    @Value
    @AllArgsConstructor
    protected static class SessionWriteRecord {
        private final OFMessage request;
        private final CompletableFuture<Optional<OFMessage>> future;

        SessionWriteRecord(OFMessage request) {
            this(request, new CompletableFuture<Optional<OFMessage>>());
        }
    }

    private static class MeterInstallAnswer implements IAnswer<CompletableFuture<MeterInstallReport>> {
        private final Exception error;

        MeterInstallAnswer(Exception error) {
            this.error = error;
        }

        @Override
        public CompletableFuture<MeterInstallReport> answer() throws Throwable {
            MeterInstallCommand command = (MeterInstallCommand) getCurrentArguments()[0];

            MeterInstallReport report;
            if (error == null) {
                report = new MeterInstallReport(command);
            } else {
                report = new MeterInstallReport(command, error);
            }
            return CompletableFuture.completedFuture(report);
        }
    }

    private static class MeterRemoveAnswer implements IAnswer<CompletableFuture<MeterRemoveReport>> {
        private final Exception error;

        MeterRemoveAnswer(Exception error) {
            this.error = error;
        }

        @Override
        public CompletableFuture<MeterRemoveReport> answer() throws Throwable {
            MeterRemoveCommand command = (MeterRemoveCommand) getCurrentArguments()[0];

            MeterRemoveReport report;
            if (error == null) {
                report = new MeterRemoveReport(command);
            } else {
                report = new MeterRemoveReport(command, error);
            }
            return CompletableFuture.completedFuture(report);
        }
    }
}
