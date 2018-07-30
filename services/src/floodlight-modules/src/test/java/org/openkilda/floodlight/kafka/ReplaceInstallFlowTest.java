/* Copyright 2017 Telstra Open Source
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

package org.openkilda.floodlight.kafka;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.newCapture;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;
import static org.openkilda.floodlight.test.standard.PushSchemeOutputCommands.ofFactory;
import static org.openkilda.messaging.Utils.MAPPER;

import org.openkilda.config.KafkaTopicsConfig;
import org.openkilda.floodlight.config.provider.ConfigurationProvider;
import org.openkilda.floodlight.pathverification.IPathVerificationService;
import org.openkilda.floodlight.pathverification.PathVerificationService;
import org.openkilda.floodlight.switchmanager.ISwitchManager;
import org.openkilda.floodlight.switchmanager.MeterPool;
import org.openkilda.floodlight.switchmanager.SwitchEventCollector;
import org.openkilda.floodlight.switchmanager.SwitchManager;
import org.openkilda.floodlight.test.standard.OutputCommands;
import org.openkilda.floodlight.test.standard.ReplaceSchemeOutputCommands;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.InstallEgressFlow;
import org.openkilda.messaging.command.flow.InstallIngressFlow;
import org.openkilda.messaging.command.flow.InstallOneSwitchFlow;
import org.openkilda.messaging.command.flow.InstallTransitFlow;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.SwitchDescription;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.restserver.IRestApiService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.easymock.Capture;
import org.easymock.CaptureType;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFBarrierReply;
import org.projectfloodlight.openflow.protocol.OFBarrierRequest;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFMeterMod;
import org.projectfloodlight.openflow.types.DatapathId;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class ReplaceInstallFlowTest {
    private static final FloodlightModuleContext context = new FloodlightModuleContext();
    private final ExecutorService parseRecordExecutor = MoreExecutors.sameThreadExecutor();
    protected SwitchDescription switchDescription;
    protected OutputCommands scheme;
    private IOFSwitchService ofSwitchService;
    private KafkaMessageCollector collector;
    private KafkaMessageProducer producer;

    /**
     * Returns CommandData entity constructed by data string from json resource file.
     *
     * @param value data string from json resource file
     * @return CommandData entity
     * @throws IOException if mapping fails
     */
    private static CommandData prepareData(String value) throws IOException {
        CommandMessage message = MAPPER.readValue(value, CommandMessage.class);
        return message.getData();
    }

    @Before
    public void setUp() throws FloodlightModuleException {
        final SwitchManager switchManager = new SwitchManager();
        final PathVerificationService pathVerificationService = new PathVerificationService();

        ofSwitchService = createMock(IOFSwitchService.class);
        producer = createMock(KafkaMessageProducer.class);

        context.addService(IOFSwitchService.class, ofSwitchService);
        context.addService(IRestApiService.class, null);
        context.addService(SwitchEventCollector.class, null);
        context.addService(KafkaMessageProducer.class, producer);
        context.addService(IPathVerificationService.class, pathVerificationService);
        context.addService(ISwitchManager.class, switchManager);

        switchManager.init(context);

        collector = new KafkaMessageCollector();
        context.addConfigParam(collector, "topic", "");
        context.addConfigParam(collector, "bootstrap-servers", "");
        collector.init(context);

        initScheme();
    }

    protected void initScheme() {
        scheme = new ReplaceSchemeOutputCommands();
        switchDescription = new SwitchDescription("", "", "", "", "");
    }

    @Test
    public void installOneSwitchNoneFlow() throws IOException, InterruptedException {
        String value = Resources.toString(getClass().getResource("/install_one_switch_none_flow.json"), Charsets.UTF_8);
        InstallOneSwitchFlow data = (InstallOneSwitchFlow) prepareData(value);
        OFMeterMod meterCommand = scheme.installMeter(data.getBandwidth(), 1024, data.getMeterId());
        OFFlowAdd flowCommand = scheme.oneSwitchNoneFlowMod(data.getInputPort(), data.getOutputPort(),
                data.getMeterId(), 123L);
        runTest(value, flowCommand, meterCommand, null, null);
    }

    @Test
    public void installOneSwitchReplaceFlow() throws IOException, InterruptedException {
        String value = Resources.toString(getClass().getResource("/install_one_switch_replace_flow.json"), Charsets.UTF_8);
        InstallOneSwitchFlow data = (InstallOneSwitchFlow) prepareData(value);
        OFMeterMod meterCommand = scheme.installMeter(data.getBandwidth(), 1024, data.getMeterId());
        OFFlowAdd flowCommand = scheme.oneSwitchReplaceFlowMod(data.getInputPort(), data.getOutputPort(),
                data.getInputVlanId(), data.getOutputVlanId(), data.getMeterId(), 123L);
        runTest(value, flowCommand, meterCommand, null, null);
    }

    @Test
    public void installOneSwitchPushFlow() throws IOException, InterruptedException {
        String value = Resources.toString(getClass().getResource("/install_one_switch_push_flow.json"), Charsets.UTF_8);
        InstallOneSwitchFlow data = (InstallOneSwitchFlow) prepareData(value);
        OFMeterMod meterCommand = scheme.installMeter(data.getBandwidth(), 1024, data.getMeterId());
        OFFlowAdd flowCommand = scheme.oneSwitchPushFlowMod(data.getInputPort(), data.getOutputPort(),
                data.getOutputVlanId(), data.getMeterId(), 123L);
        runTest(value, flowCommand, meterCommand, null, null);
    }

    @Test
    public void installOneSwitchPopFlow() throws IOException, InterruptedException {
        String value = Resources.toString(getClass().getResource("/install_one_switch_pop_flow.json"), Charsets.UTF_8);
        InstallOneSwitchFlow data = (InstallOneSwitchFlow) prepareData(value);
        OFMeterMod meterCommand = scheme.installMeter(data.getBandwidth(), 1024, data.getMeterId());
        OFFlowAdd flowCommand = scheme.oneSwitchPopFlowMod(data.getInputPort(), data.getOutputPort(),
                data.getInputVlanId(), data.getMeterId(), 123L);
        runTest(value, flowCommand, meterCommand, null, null);
    }

    @Test
    public void installIngressNoneFlow() throws IOException, InterruptedException {
        String value = Resources.toString(getClass().getResource("/install_ingress_none_flow.json"), Charsets.UTF_8);
        InstallIngressFlow data = (InstallIngressFlow) prepareData(value);
        OFMeterMod meterCommand = scheme.installMeter(data.getBandwidth(), 1024, data.getMeterId());
        OFFlowAdd flowCommand = scheme.ingressNoneFlowMod(data.getInputPort(), data.getOutputPort(),
                data.getTransitVlanId(), data.getMeterId(), 123L);
        runTest(value, flowCommand, meterCommand, null, null);
    }

    @Test
    public void installIngressReplaceFlow() throws IOException, InterruptedException {
        String value = Resources.toString(getClass().getResource("/install_ingress_replace_flow.json"), Charsets.UTF_8);
        InstallIngressFlow data = (InstallIngressFlow) prepareData(value);
        OFMeterMod meterCommand = scheme.installMeter(data.getBandwidth(), 1024, data.getMeterId());
        OFFlowAdd flowCommand = scheme.ingressReplaceFlowMod(data.getInputPort(), data.getOutputPort(),
                data.getInputVlanId(), data.getTransitVlanId(), data.getMeterId(), 123L);
        runTest(value, flowCommand, meterCommand, null, null);
    }

    @Test
    public void installIngressPushFlow() throws IOException, InterruptedException {
        String value = Resources.toString(getClass().getResource("/install_ingress_push_flow.json"), Charsets.UTF_8);
        InstallIngressFlow data = (InstallIngressFlow) prepareData(value);
        OFMeterMod meterCommand = scheme.installMeter(data.getBandwidth(), 1024, data.getMeterId());
        OFFlowAdd flowCommand = scheme.ingressPushFlowMod(data.getInputPort(), data.getOutputPort(),
                data.getTransitVlanId(), data.getMeterId(), 123L);
        runTest(value, flowCommand, meterCommand, null, null);
    }

    @Test
    public void installIngressPopFlow() throws IOException, InterruptedException {
        String value = Resources.toString(getClass().getResource("/install_ingress_pop_flow.json"), Charsets.UTF_8);
        InstallIngressFlow data = (InstallIngressFlow) prepareData(value);
        OFMeterMod meterCommand = scheme.installMeter(data.getBandwidth(), 1024, data.getMeterId());
        OFFlowAdd flowCommand = scheme.ingressPopFlowMod(data.getInputPort(), data.getOutputPort(),
                data.getInputVlanId(), data.getTransitVlanId(), data.getMeterId(), 123L);
        runTest(value, flowCommand, meterCommand, null, null);
    }

    @Test
    public void installEgressNoneFlow() throws IOException, InterruptedException {
        String value = Resources.toString(getClass().getResource("/install_egress_none_flow.json"), Charsets.UTF_8);
        InstallEgressFlow data = (InstallEgressFlow) prepareData(value);
        OFFlowAdd flowCommand = scheme.egressNoneFlowMod(data.getInputPort(), data.getOutputPort(),
                data.getTransitVlanId(), 123L);
        runTest(value, flowCommand, null, null, null);
    }

    @Test
    public void installEgressReplaceFlow() throws IOException, InterruptedException {
        String value = Resources.toString(getClass().getResource("/install_egress_replace_flow.json"), Charsets.UTF_8);
        InstallEgressFlow data = (InstallEgressFlow) prepareData(value);
        OFFlowAdd flowCommand = scheme.egressReplaceFlowMod(data.getInputPort(), data.getOutputPort(),
                data.getTransitVlanId(), data.getOutputVlanId(), 123L);
        runTest(value, flowCommand, null, null, null);
    }

    @Test
    public void installEgressPushFlow() throws IOException, InterruptedException {
        String value = Resources.toString(getClass().getResource("/install_egress_push_flow.json"), Charsets.UTF_8);
        InstallEgressFlow data = (InstallEgressFlow) prepareData(value);
        OFFlowAdd flowCommand = scheme.egressPushFlowMod(data.getInputPort(), data.getOutputPort(),
                data.getTransitVlanId(), data.getOutputVlanId(), 123L);
        runTest(value, flowCommand, null, null, null);
    }

    @Test
    public void installEgressPopFlow() throws IOException, InterruptedException {
        String value = Resources.toString(getClass().getResource("/install_egress_pop_flow.json"), Charsets.UTF_8);
        InstallEgressFlow data = (InstallEgressFlow) prepareData(value);
        OFFlowAdd flowCommand = scheme.egressPopFlowMod(data.getInputPort(), data.getOutputPort(),
                data.getTransitVlanId(), 123L);
        runTest(value, flowCommand, null, null, null);
    }

    @Test
    public void installTransitFlow() throws IOException, InterruptedException {
        String value = Resources.toString(getClass().getResource("/install_transit_flow.json"), Charsets.UTF_8);
        InstallTransitFlow data = (InstallTransitFlow) prepareData(value);
        OFFlowAdd flowCommand = scheme.transitFlowMod(data.getInputPort(), data.getOutputPort(),
                data.getTransitVlanId(), 123L);
        runTest(value, flowCommand, null, null, null);
    }

    /**
     * Runs test case.
     *
     * @param value       data string from json resource file
     * @param flowCommand OFFlowAdd instance to compare result with
     * @throws InterruptedException if test was interrupted during run
     */
    private void runTest(final String value, final OFFlowAdd flowCommand, final OFMeterMod meterCommand,
                         final OFFlowAdd reverseFlowCommand, final OFMeterMod reverseMeterCommand)
            throws InterruptedException {
        // construct kafka message
        ConsumerRecord<String, String> record = new ConsumerRecord<>("", 0, 0, "", value);

        ConfigurationProvider provider = new ConfigurationProvider(context, collector);
        KafkaTopicsConfig topicsConfig = provider.getConfiguration(KafkaTopicsConfig.class);

        // create parser instance
        ConsumerContext kafkaContext = new ConsumerContext(context, topicsConfig);
        RecordHandler parseRecord = new RecordHandler(kafkaContext, record, new MeterPool());
        // init test mocks
        Capture<OFFlowAdd> flowAddCapture = flowCommand == null ? null : newCapture(CaptureType.ALL);
        Capture<OFMeterMod> meterAddCapture = meterCommand == null ? null : newCapture(CaptureType.ALL);
        prepareMocks(flowAddCapture, meterAddCapture, reverseFlowCommand != null, reverseMeterCommand != null);

        // run parser and wait for termination or timeout
        parseRecordExecutor.execute(parseRecord);
        parseRecordExecutor.shutdown();
        parseRecordExecutor.awaitTermination(10, TimeUnit.SECONDS);

        // verify results
        if (meterCommand != null) {
            assertEquals(meterCommand, meterAddCapture.getValues().get(0));
            if (reverseMeterCommand != null) {
                assertEquals(reverseMeterCommand, meterAddCapture.getValues().get(1));
            }
        }
        if (flowCommand != null) {
            assertEquals(flowCommand, flowAddCapture.getValues().get(0));
            if (reverseFlowCommand != null) {
                assertEquals(reverseFlowCommand, flowAddCapture.getValues().get(1));
            }
        }
    }

    /**
     * Prepares test mocks for run.
     *
     * @param flowAddCapture  Capture for FlowAdd command
     * @param meterAddCapture Capture for MeterMod<Add> command
     */
    private void prepareMocks(Capture<OFFlowAdd> flowAddCapture, Capture<OFMeterMod> meterAddCapture,
                              boolean needCheckReverseFlow, boolean needCheckReverseMeter) {
        IOFSwitch iofSwitch = createMock(IOFSwitch.class);

        expect(ofSwitchService.getSwitch(anyObject(DatapathId.class))).andStubReturn(iofSwitch);
        expect(iofSwitch.getOFFactory()).andStubReturn(ofFactory);
        expect(iofSwitch.getSwitchDescription()).andStubReturn(switchDescription);

        if (meterAddCapture != null) {
            expect(iofSwitch.write(capture(meterAddCapture))).andReturn(true);
            expect(iofSwitch.writeRequest(anyObject(OFBarrierRequest.class)))
                    .andReturn(Futures.immediateFuture(createMock(OFBarrierReply.class)));
            if (flowAddCapture != null) {
                expect(iofSwitch.write(capture(flowAddCapture))).andReturn(true);
            }
            if (needCheckReverseMeter) {
                expect(iofSwitch.write(capture(meterAddCapture))).andReturn(true);
            }
            if (needCheckReverseFlow) {
                expect(iofSwitch.write(capture(flowAddCapture))).andReturn(true);
            }
        } else if (flowAddCapture != null) {
            expect(iofSwitch.write(capture(flowAddCapture))).andReturn(true).times(needCheckReverseFlow ? 2 : 1);
        }

        replay(ofSwitchService);
        replay(iofSwitch);
    }
}
