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

package org.openkilda.floodlight.switchmanager.factory.generator;

import static org.openkilda.floodlight.switchmanager.SwitchFlowUtils.prepareFlowModBuilder;
import static org.openkilda.floodlight.switchmanager.SwitchManager.MINIMAL_POSITIVE_PRIORITY;
import static org.projectfloodlight.openflow.protocol.OFVersion.OF_12;

import org.openkilda.floodlight.switchmanager.factory.SwitchFlowTuple;

import lombok.Builder;
import net.floodlightcontroller.core.IOFSwitch;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowMod;

@Builder
public class DropFlowGenerator implements SwitchFlowGenerator {

    private long cookie;
    private int tableId;

    @Override
    public SwitchFlowTuple generateFlow(IOFSwitch sw) {
        OFFactory ofFactory = sw.getOFFactory();

        if (ofFactory.getVersion() == OF_12) {
            return SwitchFlowTuple.getEmpty();
        }

        OFFlowMod flowMod = prepareFlowModBuilder(ofFactory, cookie, MINIMAL_POSITIVE_PRIORITY, tableId)
                .build();
        return SwitchFlowTuple.builder()
                .sw(sw)
                .flow(flowMod)
                .build();
    }
}
