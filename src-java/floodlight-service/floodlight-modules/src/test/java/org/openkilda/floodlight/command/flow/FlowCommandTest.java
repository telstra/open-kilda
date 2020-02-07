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

package org.openkilda.floodlight.command.flow;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.openkilda.floodlight.test.standard.OutputCommands.ofFactory;
import static org.openkilda.model.SwitchFeature.BFD;
import static org.openkilda.model.SwitchFeature.GROUP_PACKET_OUT_CONTROLLER;
import static org.openkilda.model.SwitchFeature.METERS;
import static org.openkilda.model.SwitchFeature.NOVIFLOW_COPY_FIELD;
import static org.openkilda.model.SwitchFeature.RESET_COUNTS_FLAG;

import org.openkilda.floodlight.service.FeatureDetectorService;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;

import com.google.common.collect.Sets;
import net.floodlightcontroller.core.IOFSwitch;
import org.junit.Before;

import java.util.Set;

abstract class FlowCommandTest {
    protected static final String FLOW_ID = "test_flow";
    protected static final SwitchId SWITCH_ID = new SwitchId(1);

    protected IOFSwitch iofSwitch;
    protected FeatureDetectorService featureDetectorService;

    @Before
    public void setUp() {
        iofSwitch = createMock(IOFSwitch.class);
        featureDetectorService = createMock(FeatureDetectorService.class);
        Set<SwitchFeature> features = Sets.newHashSet(METERS, BFD, GROUP_PACKET_OUT_CONTROLLER,
                NOVIFLOW_COPY_FIELD, RESET_COUNTS_FLAG);
        expect(featureDetectorService.detectSwitch(iofSwitch)).andStubReturn(features);

        expect(iofSwitch.getOFFactory()).andStubReturn(ofFactory);
        replay(iofSwitch);
        replay(featureDetectorService);
    }
}
