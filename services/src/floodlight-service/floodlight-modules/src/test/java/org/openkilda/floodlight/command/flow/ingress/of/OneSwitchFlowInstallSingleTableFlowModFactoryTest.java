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

package org.openkilda.floodlight.command.flow.ingress.of;

import org.openkilda.floodlight.command.flow.ingress.OneSwitchFlowInstallCommand;
import org.openkilda.floodlight.model.FlowSegmentMetadata;

import org.projectfloodlight.openflow.types.TableId;

public class OneSwitchFlowInstallSingleTableFlowModFactoryTest extends OneSwitchFlowInstallFlowModFactoryTest {

    @Override
    IngressFlowModFactory makeFactory(OneSwitchFlowInstallCommand command) {
        return new OneSwitchFlowInstallSingleTableFlowModFactory(command, sw, switchFeatures);
    }

    @Override
    FlowSegmentMetadata makeMetadata() {
        return new FlowSegmentMetadata(flowId, cookie, false);
    }

    @Override
    TableId getTargetTableId() {
        return TableId.ZERO;
    }
}
