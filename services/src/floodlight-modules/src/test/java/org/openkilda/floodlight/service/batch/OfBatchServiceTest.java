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

package org.openkilda.floodlight.service.batch;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.expect;

import org.openkilda.floodlight.SwitchUtils;
import org.openkilda.floodlight.model.OfInput;
import org.openkilda.floodlight.model.OfRequestResponse;
import org.openkilda.floodlight.service.of.InputService;

import com.google.common.collect.ImmutableList;
import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.easymock.EasyMockSupport;
import org.easymock.Mock;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFBadRequestCode;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPortMod;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.ver13.OFFactoryVer13;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class OfBatchServiceTest extends EasyMockSupport {
    private OfBatchService batchService = new OfBatchService();

    protected final FloodlightModuleContext moduleContext = new FloodlightModuleContext();

    @Mock
    private InputService inputService;

    @Mock
    private IOFSwitch switchAlpha;

    @Mock
    private SwitchUtils switchUtils;

    @Before
    public void setUp() throws Exception {
        injectMocks(this);

        moduleContext.addService(InputService.class, inputService);

        inputService.addTranslator(OFType.ERROR, batchService);
        inputService.addTranslator(OFType.BARRIER_REPLY, batchService);

        final DatapathId dpIdAlpha = DatapathId.of(0xfffe000000000001L);

        OFFactory ofFactory = new OFFactoryVer13();
        expect(switchAlpha.getOFFactory()).andReturn(ofFactory).anyTimes();
        expect(switchAlpha.getId()).andReturn(dpIdAlpha).anyTimes();
        expect(switchAlpha.getLatency()).andReturn(U64.of(8)).anyTimes();

        expect(switchUtils.lookupSwitch(dpIdAlpha)).andReturn(switchAlpha).anyTimes();
    }

    @After
    public void tearDown() throws Exception {
        verifyAll();
    }

    @Test
    public void input() {
        expect(switchAlpha.write(anyObject(OFMessage.class))).andReturn(true).times(2);
        replayAll();
        batchService.init(moduleContext);

        final OFFactory ofFactory = switchAlpha.getOFFactory();
        final OFPortMod requestAlpha = ofFactory.buildPortMod()
                .setPortNo(OFPort.of(1))
                .build();

        OfBatch batch = new OfBatch(
                switchUtils, ImmutableList.of(new OfRequestResponse(switchAlpha.getId(), requestAlpha)));
        CompletableFuture<List<OfRequestResponse>> future = batch.getFuture();

        batchService.write(batch);
        Assert.assertFalse(future.isDone());

        HashMap<DatapathId, OfBatchSwitchQueue> pendingMap = batchService.getPendingMap();
        Assert.assertEquals(1, pendingMap.size());

        // mismatch xId
        feedMessage(switchAlpha, ofFactory.errorMsgs().buildBadRequestErrorMsg()
                .setCode(OFBadRequestCode.BAD_LEN)
                .setXid(requestAlpha.getXid() + 1000)
                .build());
        Assert.assertFalse(future.isDone());

        // match barrier
        feedMessage(switchAlpha, ofFactory.buildBarrierReply()
                .setXid(batch.getPendingBarriers().get(0).xid)
                .build());
        Assert.assertTrue(future.isDone());

        // cleanup must remove all complete batches
        Assert.assertEquals(0, pendingMap.size());
    }

    @Test
    public void futureCancel() {
        expect(switchAlpha.write(anyObject(OFMessage.class))).andReturn(true).times(2);
        replayAll();
        batchService.init(moduleContext);

        final OFFactory ofFactory = switchAlpha.getOFFactory();
        final OFPortMod requestAlpha = ofFactory.buildPortMod()
                .setPortNo(OFPort.of(1))
                .build();
        OfBatch batch = new OfBatch(
                switchUtils, ImmutableList.of(new OfRequestResponse(switchAlpha.getId(), requestAlpha)));
        CompletableFuture<List<OfRequestResponse>> future = batch.getFuture();
        future.cancel(false);

        batchService.write(batch);

        // any OF message from switch mentioned in OfBatch record
        feedMessage(switchAlpha, ofFactory.errorMsgs().buildBadRequestErrorMsg()
                .setCode(OFBadRequestCode.BAD_LEN)
                .setXid(requestAlpha.getXid() + 1000)
                .build());

        Assert.assertEquals(0, batchService.getPendingMap().size());
    }

    private void feedMessage(IOFSwitch sw, OFMessage message) {
        OfInput input = new OfInput(sw, message, new FloodlightContext());
        batchService.input(input);
    }
}
