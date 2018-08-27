/* Copyright 2018 Telstra Open Source
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

package org.openkilda.floodlight.command.flow;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;

import org.openkilda.floodlight.command.CommandContext;
import org.openkilda.floodlight.kafka.KafkaMessageProducer;
import org.openkilda.floodlight.service.FlowVerificationService;
import org.openkilda.floodlight.service.batch.OfBatchService;
import org.openkilda.messaging.command.flow.FlowDirection;
import org.openkilda.messaging.command.flow.FlowVerificationRequest;
import org.openkilda.messaging.command.flow.UniFlowVerificationRequest;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.SwitchId;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import org.easymock.EasyMock;
import org.junit.Before;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.U64;

public abstract class AbstractVerificationCommandTest {
    private final String correlationIdTemplate = String.format(
            "%s-%%d", AbstractVerificationCommandTest.class.getCanonicalName());
    private int testIndex = 0;

    protected final DatapathId sourceSwitchId = DatapathId.of(0x00ff000001L);
    protected final int sourcePort = 15;
    protected IOFSwitch sourceSwitch = EasyMock.createMock(IOFSwitch.class);
    protected final DatapathId destSwitchId = DatapathId.of(0x00ff000002L);
    protected final int destPort = sourcePort + 5;
    protected IOFSwitch destSwitch = EasyMock.createMock(IOFSwitch.class);

    protected CommandContext context;

    protected KafkaMessageProducer kafkaProducerService = EasyMock.createMock(KafkaMessageProducer.class);
    protected OfBatchService ioService = EasyMock.createMock(OfBatchService.class);
    protected IOFSwitchService switchService = EasyMock.createMock(IOFSwitchService.class);
    protected FlowVerificationService flowVerificationService = EasyMock.createMock(FlowVerificationService.class);
    protected IThreadPoolService threadPoolService = EasyMock.createMock(IThreadPoolService.class);

    @Before
    public void setUp() throws Exception {
        FloodlightModuleContext moduleContext = new FloodlightModuleContext();
        moduleContext.addService(KafkaMessageProducer.class, kafkaProducerService);
        moduleContext.addService(OfBatchService.class, ioService);
        moduleContext.addService(IOFSwitchService.class, switchService);
        moduleContext.addService(FlowVerificationService.class, flowVerificationService);
        moduleContext.addService(IThreadPoolService.class, threadPoolService);

        context = new CommandContext(moduleContext, String.format(correlationIdTemplate, testIndex++));

        expect(switchService.getSwitch(sourceSwitchId)).andReturn(sourceSwitch).anyTimes();
        expect(switchService.getActiveSwitch(sourceSwitchId)).andReturn(sourceSwitch).anyTimes();
        expect(switchService.getSwitch(destSwitchId)).andReturn(destSwitch).anyTimes();
        expect(switchService.getActiveSwitch(destSwitchId)).andReturn(destSwitch).anyTimes();
        replay(switchService);

        expect(sourceSwitch.getLatency()).andReturn(U64.ZERO).anyTimes();
        expect(destSwitch.getLatency()).andReturn(U64.ZERO).anyTimes();
    }

    protected UniFlowVerificationRequest makeVerificationRequest() {
        String flowId = "junit-flow";
        Flow flow = new Flow(
                flowId, 1000, false, "unit test flow",
                new SwitchId(sourceSwitchId.toString()), sourcePort, 0x100,
                new SwitchId(destSwitchId.toString()), destPort, 0x101);
        FlowVerificationRequest request = new FlowVerificationRequest(flowId, 1000);
        return new UniFlowVerificationRequest(request, flow, FlowDirection.FORWARD);
    }
}
