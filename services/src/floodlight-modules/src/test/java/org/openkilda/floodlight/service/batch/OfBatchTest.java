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

import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.newCapture;

import org.openkilda.floodlight.SwitchUtils;
import org.openkilda.floodlight.model.OfRequestResponse;

import com.google.common.collect.ImmutableList;
import net.floodlightcontroller.core.IOFSwitch;
import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.EasyMockSupport;
import org.easymock.Mock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFBarrierRequest;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPortMod;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.ver13.OFFactoryVer13;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class OfBatchTest extends EasyMockSupport {
    @Mock
    private IOFSwitch switchAlpha;

    @Mock
    private IOFSwitch switchBeta;

    @Mock
    private SwitchUtils switchUtils;

    @Before
    public void setUp() throws Exception {
        injectMocks(this);

        final DatapathId dpIdAlpha = DatapathId.of(0xfffe000001L);
        final DatapathId dpIdBeta = DatapathId.of(0xfffe000002L);

        OFFactory ofFactory = new OFFactoryVer13();

        expect(switchAlpha.getOFFactory()).andReturn(ofFactory).anyTimes();
        expect(switchAlpha.getId()).andReturn(dpIdAlpha).anyTimes();

        expect(switchBeta.getOFFactory()).andReturn(ofFactory).anyTimes();
        expect(switchBeta.getId()).andReturn(dpIdBeta).anyTimes();

        expect(switchUtils.lookupSwitch(dpIdAlpha)).andReturn(switchAlpha).anyTimes();
        expect(switchUtils.lookupSwitch(dpIdBeta)).andReturn(switchBeta).anyTimes();
    }

    @Test
    public void write() {
        Capture<OFMessage> captureSwitchWrite = newCapture(CaptureType.ALL);

        expect(switchAlpha.write(capture(captureSwitchWrite))).andReturn(true).times(2);

        replayAll();

        OFFlowAdd payload = switchAlpha.getOFFactory().buildFlowAdd()
                .setPriority(0)
                .build();

        OfRequestResponse batchRecord = new OfRequestResponse(switchAlpha.getId(), payload);
        OfBatch batch = new OfBatch(switchUtils, ImmutableList.of(batchRecord));
        batch.write();

        verifyAll();

        List<OFMessage> switchWriteRecords = captureSwitchWrite.getValues();

        Iterator<OFMessage> iter = switchWriteRecords.iterator();
        Assert.assertEquals(payload, iter.next());
        Assert.assertEquals(OFType.BARRIER_REQUEST, iter.next().getType());
    }

    @Test
    public void writeMultipleSwitches() {
        Capture<OFMessage> captureAlphaWrite = newCapture(CaptureType.ALL);
        Capture<OFMessage> captureBetaWrite = newCapture(CaptureType.ALL);

        expect(switchAlpha.write(capture(captureAlphaWrite))).andReturn(true).times(2);
        expect(switchBeta.write(capture(captureBetaWrite))).andReturn(true).times(2);

        replayAll();

        final OFPortMod requestAlpha = switchAlpha.getOFFactory().buildPortMod()
                .setPortNo(OFPort.of(1))
                .build();
        final OFBarrierRequest requestBeta = switchBeta.getOFFactory().buildBarrierRequest().build();

        ArrayList<OfRequestResponse> requests = new ArrayList<>();
        requests.add(new OfRequestResponse(switchAlpha.getId(), requestAlpha));
        requests.add(
                new OfRequestResponse(
                        switchBeta.getId(),
                        requestBeta));

        OfBatch batch = new OfBatch(switchUtils, requests);
        batch.write();

        verifyAll();

        // alpha
        Iterator<OFMessage> alphaWrite = captureAlphaWrite.getValues().iterator();
        Assert.assertEquals(requestAlpha, alphaWrite.next());
        Assert.assertEquals(OFType.BARRIER_REQUEST, alphaWrite.next().getType());

        // beta
        Iterator<OFMessage> betaWrite = captureBetaWrite.getValues().iterator();
        Assert.assertEquals(requestBeta, betaWrite.next());
        Assert.assertEquals(OFType.BARRIER_REQUEST, betaWrite.next().getType());
    }
}
