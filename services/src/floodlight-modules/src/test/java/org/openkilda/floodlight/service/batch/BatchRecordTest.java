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
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.newCapture;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import org.openkilda.floodlight.SwitchUtils;
import org.openkilda.floodlight.switchmanager.OFInstallException;

import com.google.common.collect.ImmutableList;
import net.floodlightcontroller.core.IOFSwitch;
import org.easymock.Capture;
import org.easymock.CaptureType;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.ver13.OFFactoryVer13;
import org.projectfloodlight.openflow.types.DatapathId;

import java.util.Iterator;
import java.util.List;

public class BatchRecordTest {
    private OFMessage payload;
    private IOFSwitch iofSwitch = createMock(IOFSwitch.class);
    private SwitchUtils switchUtils = createMock(SwitchUtils.class);
    protected final DatapathId switchId = DatapathId.of(0x00ff000001L);

    @Before
    public void setUp() throws Exception {
        OFFactory ofFactory = new OFFactoryVer13();
        payload = ofFactory.buildFlowAdd()
                .setPriority(0)
                .build();
        expect(iofSwitch.getOFFactory()).andReturn(ofFactory).anyTimes();
    }

    @Test
    public void write() {
        OfPendingMessage batchRecord = new OfPendingMessage(switchId, payload);
        BatchRecord batch = new BatchRecord(switchUtils, ImmutableList.of(batchRecord));

        expect(switchUtils.lookupSwitch(switchId)).andReturn(iofSwitch).anyTimes();
        replay(switchUtils);

        Capture<OFMessage> captureSwitchWrite = newCapture(CaptureType.ALL);

        expect(iofSwitch.getId()).andReturn(switchId).anyTimes();
        expect(iofSwitch.write(capture(captureSwitchWrite))).andReturn(true).times(2);
        replay(iofSwitch);

        try {
            batch.write();
        } catch (OFInstallException e) {
            throw new AssertionError("Batch write operation failed", e);
        }

        verify(switchUtils, iofSwitch);

        List<OFMessage> switchWriteRecords = captureSwitchWrite.getValues();

        Iterator<OFMessage> iter = switchWriteRecords.iterator();
        Assert.assertEquals(payload, iter.next());
        Assert.assertEquals(OFType.BARRIER_REQUEST, iter.next().getType());
    }
}
