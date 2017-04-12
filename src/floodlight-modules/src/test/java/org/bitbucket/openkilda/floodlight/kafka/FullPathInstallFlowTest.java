package org.bitbucket.openkilda.floodlight.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.common.util.concurrent.MoreExecutors;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.staticentry.IStaticEntryPusherService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.bitbucket.openkilda.floodlight.message.CommandMessage;
import org.bitbucket.openkilda.floodlight.message.Message;
import org.bitbucket.openkilda.floodlight.message.command.*;
import org.bitbucket.openkilda.floodlight.pathverification.IPathVerificationService;
import org.bitbucket.openkilda.floodlight.pathverification.PathVerificationService;
import org.bitbucket.openkilda.floodlight.switchmanager.ISwitchManager;
import org.bitbucket.openkilda.floodlight.switchmanager.SwitchEventCollector;
import org.bitbucket.openkilda.floodlight.switchmanager.SwitchManager;
import org.easymock.Capture;
import org.easymock.CaptureType;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFMeterMod;
import org.projectfloodlight.openflow.protocol.OFObject;
import org.projectfloodlight.openflow.types.DatapathId;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.bitbucket.openkilda.floodlight.message.command.CommandUtils.*;
import static org.easymock.EasyMock.*;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;

/**
 * Created by atopilin on 11/04/2017.
 */
public class FullPathInstallFlowTest {
    private static final FloodlightModuleContext context = new FloodlightModuleContext();
    private final ExecutorService parseRecordExecutor = MoreExecutors.sameThreadExecutor();

    private IStaticEntryPusherService staticEntryPusher;
    private IOFSwitchService ofSwitchService;

    private KafkaMessageCollector collector;

    @Before
    public void setUp() throws FloodlightModuleException {
        final SwitchManager switchManager = new SwitchManager();
        final PathVerificationService pathVerificationService = new PathVerificationService();

        staticEntryPusher = createMock(IStaticEntryPusherService.class);
        ofSwitchService = createMock(IOFSwitchService.class);

        context.addService(IStaticEntryPusherService.class, staticEntryPusher);
        context.addService(IOFSwitchService.class, ofSwitchService);
        context.addService(IRestApiService.class, null);
        context.addService(SwitchEventCollector.class, null);
        context.addService(KafkaMessageProducer.class, null);
        context.addService(IPathVerificationService.class, pathVerificationService);
        context.addService(ISwitchManager.class, switchManager);

        switchManager.init(context);

        collector = new KafkaMessageCollector();
        context.addConfigParam(collector,"topic", "");
        context.addConfigParam(collector, "bootstrap-servers", "");
        collector.init(context);
    }

    @Test
    public void installOneSwitchNoneFlow() throws IOException, InterruptedException {
        String value = Resources.toString(getClass().getResource("/install_one_switch_none_flow.json"), Charsets.UTF_8);
        InstallOneSwitchFlow data = (InstallOneSwitchFlow) prepareData(value);
        OFMeterMod directMeterCommand = expectedMeter(data.getBandwidth().intValue(), 1024, data.getInputMeterId().intValue());
        OFFlowAdd directFlowCommand = oneSwitchNoneFlowMod(data.getInputPort().intValue(), data.getOutputPort().intValue(),
                data.getInputMeterId().intValue(), 123L);
        OFMeterMod reverseMeterCommand = expectedMeter(data.getBandwidth().intValue(), 1024, data.getOutputMeterId().intValue());
        OFFlowAdd reverseFlowCommand = oneSwitchNoneFlowMod(data.getOutputPort().intValue(), data.getInputPort().intValue(),
                data.getOutputMeterId().intValue(), 123L);
        runTest(value, directFlowCommand, directMeterCommand, reverseFlowCommand, reverseMeterCommand);
    }

    @Test
    public void installOneSwitchReplaceFlow() throws IOException, InterruptedException {
        String value = Resources.toString(getClass().getResource("/install_one_switch_replace_flow.json"), Charsets.UTF_8);
        InstallOneSwitchFlow data = (InstallOneSwitchFlow) prepareData(value);
        OFMeterMod directMeterCommand = expectedMeter(data.getBandwidth().intValue(), 1024, data.getInputMeterId().intValue());
        OFFlowAdd directFlowCommand = oneSwitchReplaceFlowMod(data.getInputPort().intValue(), data.getOutputPort().intValue(),
                data.getInputVlanId().intValue(), data.getOutputVlanId().intValue(), data.getInputMeterId().intValue(), 123L);
        OFMeterMod reverseMeterCommand = expectedMeter(data.getBandwidth().intValue(), 1024, data.getOutputMeterId().intValue());
        OFFlowAdd reverseFlowCommand = oneSwitchReplaceFlowMod(data.getOutputPort().intValue(), data.getInputPort().intValue(),
                data.getOutputVlanId().intValue(), data.getInputVlanId().intValue(), data.getOutputMeterId().intValue(), 123L);
        runTest(value, directFlowCommand, directMeterCommand, reverseFlowCommand, reverseMeterCommand);
    }

    @Test
    public void installOneSwitchPushFlow() throws IOException, InterruptedException {
        String value = Resources.toString(getClass().getResource("/install_one_switch_push_flow.json"), Charsets.UTF_8);
        InstallOneSwitchFlow data = (InstallOneSwitchFlow) prepareData(value);
        OFMeterMod directMeterCommand = expectedMeter(data.getBandwidth().intValue(), 1024, data.getInputMeterId().intValue());
        OFFlowAdd directFlowCommand = oneSwitchPushFlowMod(data.getInputPort().intValue(), data.getOutputPort().intValue(),
                data.getOutputVlanId().intValue(), data.getInputMeterId().intValue(), 123L);
        OFMeterMod reverseMeterCommand = expectedMeter(data.getBandwidth().intValue(), 1024, data.getOutputMeterId().intValue());
        OFFlowAdd reverseFlowCommand = oneSwitchPopFlowMod(data.getOutputPort().intValue(), data.getInputPort().intValue(),
                data.getOutputVlanId().intValue(), data.getOutputMeterId().intValue(), 123L);
        runTest(value, directFlowCommand, directMeterCommand, reverseFlowCommand, reverseMeterCommand);
    }

    @Test
    public void installOneSwitchPopFlow() throws IOException, InterruptedException {
        String value = Resources.toString(getClass().getResource("/install_one_switch_pop_flow.json"), Charsets.UTF_8);
        InstallOneSwitchFlow data = (InstallOneSwitchFlow) prepareData(value);
        OFMeterMod directMeterCommand = expectedMeter(data.getBandwidth().intValue(), 1024, data.getInputMeterId().intValue());
        OFFlowAdd directFlowCommand = oneSwitchPopFlowMod(data.getInputPort().intValue(), data.getOutputPort().intValue(),
                data.getInputVlanId().intValue(), data.getInputMeterId().intValue(), 123L);
        OFMeterMod reverseMeterCommand = expectedMeter(data.getBandwidth().intValue(), 1024, data.getOutputMeterId().intValue());
        OFFlowAdd reverseFlowCommand = oneSwitchPushFlowMod(data.getOutputPort().intValue(), data.getInputPort().intValue(),
                data.getInputVlanId().intValue(), data.getOutputMeterId().intValue(), 123L);
        runTest(value, directFlowCommand, directMeterCommand, reverseFlowCommand, reverseMeterCommand);
    }

    @Test
    public void installIngressNoneFlow() throws IOException, InterruptedException {
        String value = Resources.toString(getClass().getResource("/install_ingress_none_flow.json"), Charsets.UTF_8);
        InstallIngressFlow data = (InstallIngressFlow) prepareData(value);
        OFMeterMod meterCommand = expectedMeter(data.getBandwidth().intValue(), 1024, data.getMeterId().intValue());
        OFFlowAdd flowCommand = ingressNoneFlowMod(data.getInputPort().intValue(), data.getOutputPort().intValue(),
                data.getTransitVlanId().intValue(), data.getMeterId().intValue(), 123L);
        runTest(value, flowCommand, meterCommand, null, null);
    }

    @Test
    public void installIngressReplaceFlow() throws IOException, InterruptedException {
        String value = Resources.toString(getClass().getResource("/install_ingress_replace_flow.json"), Charsets.UTF_8);
        InstallIngressFlow data = (InstallIngressFlow) prepareData(value);
        OFMeterMod meterCommand = expectedMeter(data.getBandwidth().intValue(), 1024, data.getMeterId().intValue());
        OFFlowAdd flowCommand = ingressReplaceFlowMod(data.getInputPort().intValue(), data.getOutputPort().intValue(),
                data.getInputVlanId().intValue(), data.getTransitVlanId().intValue(), data.getMeterId().intValue(),
                123L);
        runTest(value, flowCommand, meterCommand, null, null);
    }

    @Test
    public void installIngressPushFlow() throws IOException, InterruptedException {
        String value = Resources.toString(getClass().getResource("/install_ingress_push_flow.json"), Charsets.UTF_8);
        InstallIngressFlow data = (InstallIngressFlow) prepareData(value);
        OFMeterMod meterCommand = expectedMeter(data.getBandwidth().intValue(), 1024, data.getMeterId().intValue());
        OFFlowAdd flowCommand = ingressPushFlowMod(data.getInputPort().intValue(), data.getOutputPort().intValue(),
                data.getTransitVlanId().intValue(), data.getMeterId().intValue(), 123L);
        runTest(value, flowCommand, meterCommand, null, null);
    }

    @Test
    public void installIngressPopFlow() throws IOException, InterruptedException {
        String value = Resources.toString(getClass().getResource("/install_ingress_pop_flow.json"), Charsets.UTF_8);
        InstallIngressFlow data = (InstallIngressFlow) prepareData(value);
        OFMeterMod meterCommand = expectedMeter(data.getBandwidth().intValue(), 1024, data.getMeterId().intValue());
        OFFlowAdd flowCommand = ingressPopFlowMod(data.getInputPort().intValue(), data.getOutputPort().intValue(),
                data.getInputVlanId().intValue(), data.getTransitVlanId().intValue(), data.getMeterId().intValue(),
                123L);
        runTest(value, flowCommand, meterCommand, null, null);
    }

    @Test
    public void installEgressNoneFlow() throws IOException, InterruptedException {
        String value = Resources.toString(getClass().getResource("/install_egress_none_flow.json"), Charsets.UTF_8);
        InstallEgressFlow data = (InstallEgressFlow) prepareData(value);
        OFFlowAdd flowCommand = egressNoneFlowMod(data.getInputPort().intValue(), data.getOutputPort().intValue(),
                data.getTransitVlanId().intValue(), 123L);
        runTest(value, flowCommand, null, null, null);
    }

    @Test
    public void installEgressReplaceFlow() throws IOException, InterruptedException {
        String value = Resources.toString(getClass().getResource("/install_egress_replace_flow.json"), Charsets.UTF_8);
        InstallEgressFlow data = (InstallEgressFlow) prepareData(value);
        OFFlowAdd flowCommand = egressReplaceFlowMod(data.getInputPort().intValue(), data.getOutputPort().intValue(),
                data.getTransitVlanId().intValue(), data.getOutputVlanId().intValue(), 123L);
        runTest(value, flowCommand, null, null, null);
    }

    @Test
    public void installEgressPushFlow() throws IOException, InterruptedException {
        String value = Resources.toString(getClass().getResource("/install_egress_push_flow.json"), Charsets.UTF_8);
        InstallEgressFlow data = (InstallEgressFlow) prepareData(value);
        OFFlowAdd flowCommand = egressPushFlowMod(data.getInputPort().intValue(), data.getOutputPort().intValue(),
                data.getTransitVlanId().intValue(), data.getOutputVlanId().intValue(),123L);
        runTest(value, flowCommand, null, null, null);
    }

    @Test
    public void installEgressPopFlow() throws IOException, InterruptedException {
        String value = Resources.toString(getClass().getResource("/install_egress_pop_flow.json"), Charsets.UTF_8);
        InstallEgressFlow data = (InstallEgressFlow) prepareData(value);
        OFFlowAdd flowCommand = egressPopFlowMod(data.getInputPort().intValue(), data.getOutputPort().intValue(),
                data.getTransitVlanId().intValue(), 123L);
        runTest(value, flowCommand, null, null, null);
    }

    @Test
    public void installTransitFlow() throws IOException, InterruptedException {
        String value = Resources.toString(getClass().getResource("/install_transit_flow.json"), Charsets.UTF_8);
        InstallTransitFlow data = (InstallTransitFlow) prepareData(value);
        OFFlowAdd flowCommand = transitFlowMod(data.getInputPort().intValue(), data.getOutputPort().intValue(),
                data.getTransitVlanId().intValue(), 123L);
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
        KafkaMessageCollector.ParseRecord parseRecord = collector.new ParseRecord(record);
        // init test mocks
        Capture<OFFlowAdd> flowAddCapture = flowCommand == null ? null : newCapture(CaptureType.ALL);
        Capture<OFMeterMod> meterAddCapture = meterCommand == null ? null : newCapture(CaptureType.ALL);
        prepareMocks(flowAddCapture, meterAddCapture, reverseFlowCommand != null, reverseMeterCommand != null);

        // run parser and wait for termination or timeout
        parseRecordExecutor.execute(parseRecord);
        parseRecordExecutor.shutdown();
        parseRecordExecutor.awaitTermination(10, TimeUnit.SECONDS);

        // verify results
        verify(staticEntryPusher);
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
     * Returns CommandData entity constructed by data string from json resource file.
     *
     * @param value data string from json resource file
     * @return CommandData entity
     * @throws IOException if mapping fails
     */
    private static CommandData prepareData(String value) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Message message = mapper.readValue(value, Message.class);
        CommandMessage commandMessage = (CommandMessage) message;
        return commandMessage.getData();
    }

    /**
     * Prepares test mocks for run.
     *
     * @param flowAddCapture Capture for FlowAdd command
     * @param meterAddCapture Capture for MeterMod<Add> command
     */
    private void prepareMocks(Capture<OFFlowAdd> flowAddCapture, Capture<OFMeterMod> meterAddCapture,
                              boolean needCheckReverseFlow, boolean needCheckReverseMeter) {
        IOFSwitch iofSwitch = createMock(IOFSwitch.class);

        expect(ofSwitchService.getSwitch(anyObject(DatapathId.class))).andStubReturn(iofSwitch);
        expect(iofSwitch.getOFFactory()).andStubReturn(ofFactory);

        if (meterAddCapture != null) {
            expect(iofSwitch.write(capture(meterAddCapture))).andReturn(true).times(needCheckReverseMeter ? 2 : 1);
        }
        if (flowAddCapture != null) {
            staticEntryPusher.addFlow(anyString(), capture(flowAddCapture), anyObject(DatapathId.class));
            expectLastCall().times(needCheckReverseFlow ? 2 : 1);
        }

        replay(ofSwitchService);
        replay(iofSwitch);
        replay(staticEntryPusher);
    }
}
