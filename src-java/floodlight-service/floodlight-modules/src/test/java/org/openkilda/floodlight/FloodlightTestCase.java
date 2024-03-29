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

/**
 * Copyright 2011, Big Switch Networks, Inc.
 * Originally created by David Erickson, Stanford University
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 **/

package org.openkilda.floodlight;

import static org.easymock.EasyMock.expect;

import org.openkilda.floodlight.service.FeatureDetectorService;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.SwitchDescription;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.easymock.EasyMock;
import org.junit.jupiter.api.BeforeEach;
import org.projectfloodlight.openflow.protocol.OFDescStatsReply;
import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFeaturesReply;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;

import java.net.InetSocketAddress;

/**
 * This class gets a input on the application context which is used to
 * retrieve Spring beans from during tests.
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public class FloodlightTestCase {
    protected MockFloodlightProvider mockFloodlightProvider;
    protected MockSwitchManager mockSwitchManager;
    protected OFFeaturesReply swFeatures;
    protected OFFactory factory = OFFactories.getFactory(OFVersion.OF_13);
    protected FeatureDetectorService featureDetectorService = new FeatureDetectorService();

    public MockFloodlightProvider getMockFloodlightProvider() {
        return mockFloodlightProvider;
    }

    public MockSwitchManager getMockSwitchService() {
        return mockSwitchManager;
    }

    @BeforeEach
    public void setUp() throws Exception {
        mockFloodlightProvider = new MockFloodlightProvider();
        mockSwitchManager = new MockSwitchManager();
        swFeatures = factory.buildFeaturesReply().setNBuffers(1000).build();
        featureDetectorService.setup(new FloodlightModuleContext());
    }

    public IOFSwitch buildMockIoFSwitch(Long id, OFPortDesc portDesc, OFFactory factory,
                                        OFDescStatsReply swDesc, InetSocketAddress inetAddr) {
        IOFSwitch sw = EasyMock.createMock(IOFSwitch.class);
        expect(sw.getId()).andReturn(DatapathId.of(id)).anyTimes();
        expect(sw.getPort(OFPort.of(1))).andReturn(portDesc).anyTimes();
        expect(sw.getOFFactory()).andReturn(factory).anyTimes();
        expect(sw.getBuffers()).andReturn(swFeatures.getNBuffers()).anyTimes();
        expect(sw.hasAttribute(IOFSwitch.PROP_SUPPORTS_OFPP_TABLE)).andReturn(true).anyTimes();
        expect(sw.getSwitchDescription()).andReturn(new SwitchDescription(swDesc)).anyTimes();
        expect(sw.isActive()).andReturn(true).anyTimes();
        expect(sw.getLatency()).andReturn(U64.of(10L)).anyTimes();
        expect(sw.getInetAddress()).andReturn(inetAddr).anyTimes();
        expect(sw.getNumTables()).andReturn((short) 8).anyTimes();
        return sw;
    }
}
