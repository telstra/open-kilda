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

package org.openkilda.floodlight.utils;

import lombok.Getter;
import net.floodlightcontroller.util.FlowModUtils;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.types.TableId;

public abstract class OfFlowModBuilderFactory {
    @Getter
    private final int basePriority;

    public OfFlowModBuilderFactory() {
        this(FlowModUtils.PRIORITY_MED);
    }

    public OfFlowModBuilderFactory(int basePriority) {
        this.basePriority = basePriority;
    }

    public OFFlowMod.Builder makeBuilder(OFFactory of, int priorityOffset) {
        return makeBuilder(of, null, priorityOffset);
    }

    public OFFlowMod.Builder makeBuilder(OFFactory of, TableId tableId) {
        return makeBuilder(of, tableId, 0);
    }

    /**
     * Make OF flow-mod builder.
     */
    public OFFlowMod.Builder makeBuilder(OFFactory of, TableId tableId, int priorityOffset) {
        OFFlowMod.Builder builder = makeBuilder(of);
        if (tableId != null) {
            builder = setTableId(builder, tableId);
        }
        return builder.setPriority(basePriority + priorityOffset);
    }

    public abstract OFFlowMod.Builder makeBuilder(OFFactory of);

    protected abstract OFFlowMod.Builder setTableId(OFFlowMod.Builder builder, TableId tableId);
}
