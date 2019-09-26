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

package org.openkilda.floodlight.kafka;

import static java.util.Collections.emptyMap;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.newCapture;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;
import static org.openkilda.floodlight.test.standard.PushSchemeOutputCommands.ofFactory;
import static org.openkilda.messaging.Utils.MAPPER;

import org.openkilda.floodlight.KafkaChannel;
import org.openkilda.floodlight.pathverification.IPathVerificationService;
import org.openkilda.floodlight.pathverification.PathVerificationService;
import org.openkilda.floodlight.pathverification.PathVerificationServiceConfig;
import org.openkilda.floodlight.service.FeatureDetectorService;
import org.openkilda.floodlight.service.kafka.IKafkaProducerService;
import org.openkilda.floodlight.service.kafka.KafkaUtilityService;
import org.openkilda.floodlight.switchmanager.ISwitchManager;
import org.openkilda.floodlight.switchmanager.SwitchManager;
import org.openkilda.floodlight.switchmanager.SwitchManagerConfig;
import org.openkilda.floodlight.switchmanager.SwitchTrackingService;
import org.openkilda.floodlight.test.standard.OutputCommands;
import org.openkilda.floodlight.test.standard.ReplaceSchemeOutputCommands;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.InstallEgressFlow;
import org.openkilda.messaging.command.flow.InstallIngressFlow;
import org.openkilda.messaging.command.flow.InstallOneSwitchFlow;
import org.openkilda.messaging.command.flow.InstallTransitFlow;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.sabre.oss.conf4j.factory.jdkproxy.JdkProxyStaticConfigurationFactory;
import com.sabre.oss.conf4j.source.MapConfigurationSource;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.SwitchDescription;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.restserver.IRestApiService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.EasyMock;
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
    private static final String KAFKA_ISL_DISCOVERY_TOPIC = "kilda.topo.disco";
    private static final String KAFKA_FLOW_TOPIC = "kilda.flow";
    private static final String KAFKA_NORTHBOUND_TOPIC = "kilda.northbound";
    private static final DatapathId SWITCH_ID = DatapathId.of("00:00:00:00:00:00:00:09");
    private static final DatapathId INGRESS_SWITCH_DP_ID = DatapathId.of("00:00:00:00:00:00:00:09");

    private static final FloodlightModuleContext context = new FloodlightModuleContext();
    private final ExecutorService parseRecordExecutor = MoreExecutors.sameThreadExecutor();
    protected SwitchDescription switchDescription;
    protected OutputCommands scheme;
    private IOFSwitchService ofSwitchService;
    private KafkaMessageCollector collector;

    private SwitchManagerConfig config;

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
        JdkProxyStaticConfigurationFactory factory = new JdkProxyStaticConfigurationFactory();
        config = factory.createConfiguration(SwitchManagerConfig.class, new MapConfigurationSource(emptyMap()));

        final SwitchManager switchManager = new SwitchManager();
        PathVerificationServiceConfig config = EasyMock.createMock(PathVerificationServiceConfig.class);
        expect(config.getVerificationBcastPacketDst()).andReturn("00:26:E1:FF:FF:FF").anyTimes();
        replay(config);
        PathVerificationService pathVerificationService = EasyMock.createMock(PathVerificationService.class);
        expect(pathVerificationService.getConfig()).andReturn(config).anyTimes();
        replay(pathVerificationService);

        FeatureDetectorService featureDetectorService = new FeatureDetectorService();
        featureDetectorService.setup(context);

        ofSwitchService = createMock(IOFSwitchService.class);

        context.addService(IOFSwitchService.class, ofSwitchService);
        context.addService(IRestApiService.class, null);
        context.addService(SwitchTrackingService.class, null);
        context.addService(IKafkaProducerService.class, createMock(IKafkaProducerService.class));
        context.addService(IPathVerificationService.class, pathVerificationService);
        context.addService(ISwitchManager.class, switchManager);
        context.addService(FeatureDetectorService.class, featureDetectorService);

        switchManager.init(context);

        collector = new KafkaMessageCollector();
        context.addConfigParam(collector, "topic", "");
        context.addConfigParam(collector, "bootstrap-servers", "");

        KafkaUtilityService kafkaUtility = createMock(KafkaUtilityService.class);
        KafkaChannel topics = createMock(KafkaChannel.class);
        expect(topics.getTopoDiscoTopic()).andReturn(KAFKA_ISL_DISCOVERY_TOPIC).anyTimes();
        expect(topics.getFlowTopic()).andReturn(KAFKA_FLOW_TOPIC).anyTimes();
        expect(topics.getNorthboundTopic()).andReturn(KAFKA_NORTHBOUND_TOPIC).anyTimes();
        expect(kafkaUtility.getKafkaChannel()).andReturn(topics).anyTimes();

        replay(kafkaUtility, topics);

        context.addService(KafkaUtilityService.class, kafkaUtility);

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
        OFMeterMod meterCommand =
                scheme.installMeter(data.getBandwidth(), calculateBurstSize(data.getBandwidth()), data.getMeterId());
        OFFlowAdd flowCommand = scheme.oneSwitchNoneFlowMod(data.getInputPort(), data.getOutputPort(),
                data.getMeterId(), 123L);
        runTest(value, flowCommand, meterCommand, null, null);
    }

    @Test
    public void installOneSwitchReplaceFlow() throws IOException, InterruptedException {
        String value = Resources.toString(
                getClass().getResource("/install_one_switch_replace_flow.json"), Charsets.UTF_8);
        InstallOneSwitchFlow data = (InstallOneSwitchFlow) prepareData(value);
        OFMeterMod meterCommand =
                scheme.installMeter(data.getBandwidth(), calculateBurstSize(data.getBandwidth()), data.getMeterId());
        OFFlowAdd flowCommand = scheme.oneSwitchReplaceFlowMod(data.getInputPort(), data.getOutputPort(),
                data.getInputVlanId(), data.getOutputVlanId(), data.getMeterId(), 123L);
        runTest(value, flowCommand, meterCommand, null, null);
    }

    @Test
    public void installOneSwitchPushFlow() throws IOException, InterruptedException {
        String value = Resources.toString(getClass().getResource("/install_one_switch_push_flow.json"), Charsets.UTF_8);
        InstallOneSwitchFlow data = (InstallOneSwitchFlow) prepareData(value);
        OFMeterMod meterCommand =
                scheme.installMeter(data.getBandwidth(), calculateBurstSize(data.getBandwidth()), data.getMeterId());
        OFFlowAdd flowCommand = scheme.oneSwitchPushFlowMod(data.getInputPort(), data.getOutputPort(),
                data.getOutputVlanId(), data.getMeterId(), 123L);
        runTest(value, flowCommand, meterCommand, null, null);
    }

    @Test
    public void installOneSwitchPopFlow() throws IOException, InterruptedException {
        String value = Resources.toString(getClass().getResource("/install_one_switch_pop_flow.json"), Charsets.UTF_8);
        InstallOneSwitchFlow data = (InstallOneSwitchFlow) prepareData(value);
        OFMeterMod meterCommand =
                scheme.installMeter(data.getBandwidth(), calculateBurstSize(data.getBandwidth()), data.getMeterId());
        OFFlowAdd flowCommand = scheme.oneSwitchPopFlowMod(data.getInputPort(), data.getOutputPort(),
                data.getInputVlanId(), data.getMeterId(), 123L);
        runTest(value, flowCommand, meterCommand, null, null);
    }

    @Test
    public void installIngressNoneFlow() throws IOException, InterruptedException {
        String value = Resources.toString(getClass().getResource("/install_ingress_none_flow.json"), Charsets.UTF_8);
        InstallIngressFlow data = (InstallIngressFlow) prepareData(value);
        OFMeterMod meterCommand =
                scheme.installMeter(data.getBandwidth(), calculateBurstSize(data.getBandwidth()), data.getMeterId());
        OFFlowAdd flowCommand = scheme.ingressNoneFlowMod(data.getInputPort(), data.getOutputPort(),
                data.getTransitEncapsulationId(), data.getMeterId(), 123L, data.getTransitEncapsulationType(),
                DatapathId.of(data.getIngressSwitchId().toLong()));
        runTest(value, flowCommand, meterCommand, null, null);
    }

    @Test
    public void installIngressReplaceFlow() throws IOException, InterruptedException {
        String value = Resources.toString(getClass().getResource("/install_ingress_replace_flow.json"), Charsets.UTF_8);
        InstallIngressFlow data = (InstallIngressFlow) prepareData(value);
        OFMeterMod meterCommand =
                scheme.installMeter(data.getBandwidth(), calculateBurstSize(data.getBandwidth()), data.getMeterId());
        OFFlowAdd flowCommand = scheme.ingressReplaceFlowMod(data.getInputPort(), data.getOutputPort(),
                data.getInputVlanId(), data.getTransitEncapsulationId(), data.getMeterId(), 123L,
                data.getTransitEncapsulationType(), DatapathId.of(data.getIngressSwitchId().toLong()));
        runTest(value, flowCommand, meterCommand, null, null);
    }

    @Test
    public void installIngressPushFlow() throws IOException, InterruptedException {
        String value = Resources.toString(getClass().getResource("/install_ingress_push_flow.json"), Charsets.UTF_8);
        InstallIngressFlow data = (InstallIngressFlow) prepareData(value);
        OFMeterMod meterCommand =
                scheme.installMeter(data.getBandwidth(), calculateBurstSize(data.getBandwidth()), data.getMeterId());
        OFFlowAdd flowCommand = scheme.ingressPushFlowMod(data.getInputPort(), data.getOutputPort(),
                data.getTransitEncapsulationId(), data.getMeterId(), 123L, data.getTransitEncapsulationType(),
                DatapathId.of(data.getIngressSwitchId().toLong()));
        runTest(value, flowCommand, meterCommand, null, null);
    }

    @Test
    public void installIngressPopFlow() throws IOException, InterruptedException {
        String value = Resources.toString(getClass().getResource("/install_ingress_pop_flow.json"), Charsets.UTF_8);
        InstallIngressFlow data = (InstallIngressFlow) prepareData(value);
        OFMeterMod meterCommand =
                scheme.installMeter(data.getBandwidth(), calculateBurstSize(data.getBandwidth()), data.getMeterId());
        OFFlowAdd flowCommand = scheme.ingressPopFlowMod(data.getInputPort(), data.getOutputPort(),
                data.getInputVlanId(), data.getTransitEncapsulationId(), data.getMeterId(), 123L,
                data.getTransitEncapsulationType(), DatapathId.of(data.getIngressSwitchId().toLong()));
        runTest(value, flowCommand, meterCommand, null, null);
    }

    @Test
    public void installEgressNoneFlow() throws IOException, InterruptedException {
        String value = Resources.toString(getClass().getResource("/install_egress_none_flow.json"), Charsets.UTF_8);
        InstallEgressFlow data = (InstallEgressFlow) prepareData(value);
        OFFlowAdd flowCommand = scheme.egressNoneFlowMod(data.getInputPort(), data.getOutputPort(),
                data.getTransitEncapsulationId(), 123L, data.getTransitEncapsulationType(), INGRESS_SWITCH_DP_ID);
        runTest(value, flowCommand, null, null, null);
    }

    @Test
    public void installEgressReplaceFlow() throws IOException, InterruptedException {
        String value = Resources.toString(getClass().getResource("/install_egress_replace_flow.json"), Charsets.UTF_8);
        InstallEgressFlow data = (InstallEgressFlow) prepareData(value);
        OFFlowAdd flowCommand = scheme.egressReplaceFlowMod(data.getInputPort(), data.getOutputPort(),
                data.getTransitEncapsulationId(), data.getOutputVlanId(), 123L, data.getTransitEncapsulationType(),
                DatapathId.of(data.getIngressSwitchId().toLong()));
        runTest(value, flowCommand, null, null, null);
    }

    @Test
    public void installEgressPushFlow() throws IOException, InterruptedException {
        String value = Resources.toString(getClass().getResource("/install_egress_push_flow.json"), Charsets.UTF_8);
        InstallEgressFlow data = (InstallEgressFlow) prepareData(value);
        OFFlowAdd flowCommand = scheme.egressPushFlowMod(data.getInputPort(), data.getOutputPort(),
                data.getTransitEncapsulationId(), data.getOutputVlanId(), 123L, data.getTransitEncapsulationType(),
                DatapathId.of(data.getIngressSwitchId().toLong()));
        runTest(value, flowCommand, null, null, null);
    }

    @Test
    public void installEgressPopFlow() throws IOException, InterruptedException {
        String value = Resources.toString(getClass().getResource("/install_egress_pop_flow.json"), Charsets.UTF_8);
        InstallEgressFlow data = (InstallEgressFlow) prepareData(value);
        OFFlowAdd flowCommand = scheme.egressPopFlowMod(data.getInputPort(), data.getOutputPort(),
                data.getTransitEncapsulationId(), 123L, data.getTransitEncapsulationType(),
                INGRESS_SWITCH_DP_ID);
        runTest(value, flowCommand, null, null, null);
    }

    @Test
    public void installTransitFlow() throws IOException, InterruptedException {
        String value = Resources.toString(getClass().getResource("/install_transit_flow.json"), Charsets.UTF_8);
        InstallTransitFlow data = (InstallTransitFlow) prepareData(value);
        OFFlowAdd flowCommand = scheme.transitFlowMod(data.getInputPort(), data.getOutputPort(),
                data.getTransitEncapsulationId(), 123L, data.getTransitEncapsulationType(),
                DatapathId.of(data.getIngressSwitchId().toLong()));
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

        // create parser instance
        ConsumerContext kafkaContext = new ConsumerContext(context);
        RecordHandler parseRecord = new RecordHandler(kafkaContext, ImmutableList.of(), record);
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

    private void prepareMocks(Capture<OFFlowAdd> flowAddCapture, Capture<OFMeterMod> meterAddCapture,
                              boolean needCheckReverseFlow, boolean needCheckReverseMeter) {
        IOFSwitch iofSwitch = createMock(IOFSwitch.class);

        expect(iofSwitch.getId()).andReturn(SWITCH_ID);
        expect(iofSwitch.getNumTables()).andReturn((short) 8);
        expect(ofSwitchService.getActiveSwitch(anyObject(DatapathId.class))).andStubReturn(iofSwitch);
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

    private long calculateBurstSize(long bandwidth) {
        return (long) (bandwidth * config.getFlowMeterBurstCoefficient());
    }
}
