/* Copyright 2020 Telstra Open Source
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

import org.openkilda.floodlight.switchmanager.factory.SwitchFlowTuple;

import com.google.common.collect.ImmutableList;
import lombok.Builder;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.util.FlowModUtils;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionGotoTable;
import org.projectfloodlight.openflow.types.TableId;

@Builder
public class TablePassThroughDefaultFlowGenerator implements SwitchFlowGenerator {

    private long cookie;
    private int goToTableId;
    private int tableId;

    @Override
    public SwitchFlowTuple generateFlow(IOFSwitch sw) {
        OFFactory ofFactory = sw.getOFFactory();

        OFInstructionGotoTable goToTable = ofFactory.instructions().gotoTable(TableId.of(goToTableId));
        OFFlowMod flowMod = prepareFlowModBuilder(ofFactory, cookie, FlowModUtils.PRIORITY_MIN + 1, tableId)
                .setInstructions(ImmutableList.of(goToTable)).build();

        return SwitchFlowTuple.builder()
                .sw(sw)
                .flow(flowMod)
                .build();
    }
}
