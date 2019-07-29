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

package org.openkilda.floodlight.command.poc;

import org.openkilda.floodlight.FloodlightResponse;
import org.openkilda.floodlight.command.SessionProxy;
import org.openkilda.floodlight.utils.CompletableFutureAdapter;
import org.openkilda.floodlight.utils.SwitchPipelineAdapter;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFTableFeatureProp;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropMatch;
import org.projectfloodlight.openflow.protocol.OFTableFeatures;
import org.projectfloodlight.openflow.protocol.OFTableFeaturesStatsReply;
import org.projectfloodlight.openflow.protocol.OFTableFeaturesStatsRequest;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.U32;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class SetupPipeline extends AbstractMultiTableCommand {
    @JsonCreator
    public SetupPipeline(@JsonProperty("message_context") MessageContext messageContext,
                         @JsonProperty("switch_id") SwitchId switchId) {
        super(switchId, messageContext);
    }

    @Override
    protected CompletableFuture<Optional<OFMessage>> writeCommands(IOFSwitch sw,
                                                                   FloodlightModuleContext moduleContext) {
        OFFactory of = sw.getOFFactory();
        SwitchDescriptor swDesc = new SwitchDescriptor(sw);
        return new CompletableFutureAdapter<>(messageContext,
                                              sw.writeRequest(of.buildTableFeaturesStatsRequest().build()))
                .thenCompose(current -> makePipelineRequest(sw, swDesc, current))
                .thenApply(response -> handlePipelineResponse(sw, response));
    }

    private CompletableFuture<OFTableFeaturesStatsReply> makePipelineRequest(IOFSwitch sw, SwitchDescriptor swDesc,
                                                                     OFTableFeaturesStatsReply current) {
        OFFactory of = sw.getOFFactory();
        List<OFTableFeatures> tableFeatures = new ArrayList<>();

        Set<TableId> allUsedTables = swDesc.getAllUsedTables();
        for (OFTableFeatures entry : current.getEntries()) {
            if (allUsedTables.contains(entry.getTableId())) {
                tableFeatures.add(makeTableFeaturesUpdate(of, entry));
            } else {
                tableFeatures.add(entry);
            }
        }

        OFTableFeaturesStatsRequest request = of.buildTableFeaturesStatsRequest()
                .setEntries(tableFeatures)
                .build();
        return new CompletableFutureAdapter<>(messageContext, sw.writeRequest(request));
    }

    private Optional<OFMessage> handlePipelineResponse(IOFSwitch sw, OFTableFeaturesStatsReply reply) {
        new SwitchPipelineAdapter(sw).dumpPipeline(reply);
        return Optional.of(reply);
    }

    private OFTableFeatures makeTableFeaturesUpdate(OFFactory of, OFTableFeatures current) {
        List<OFTableFeatureProp> update = new ArrayList<>();
        for (OFTableFeatureProp entry : current.getProperties()) {
            switch (SwitchPipelineAdapter.getTableFeaturePropType(entry)) {
                case MATCH:
                    update.add(makeMatchProp(of, current.getTableId(), (OFTableFeaturePropMatch) entry));
                    break;
                default:
                    update.add(entry);
            }
        }

        return makeTableBasicSetup(current.createBuilder(), current.getTableId())
                .setProperties(update)
                .build();
    }

    private OFTableFeatures.Builder makeTableBasicSetup(OFTableFeatures.Builder table, TableId tableId) {
        /*
        table.setMetadataWrite(U64.NO_MASK);
        if (!TableId.of(0).equals(tableId)) {
            table.setMetadataMatch(U64.NO_MASK);
        }
        */
        return table;
    }

    private OFTableFeatureProp makeMatchProp(OFFactory of, TableId tableId, OFTableFeaturePropMatch current) {
        List<U32> matchUpdate = new ArrayList<>();
        Set<U32> matchCheck = new HashSet<>(current.getOxmIds());
        for (U32 oxmId : makeOxmsRequirement(of, tableId)) {
            if (matchCheck.add(oxmId)) {
                matchUpdate.add(oxmId);
            }
        }

        if (matchUpdate.isEmpty()) {
            return current;
        }

        matchUpdate.addAll(current.getOxmIds());
        return current.createBuilder()
                .setOxmIds(matchUpdate)
                .build();
    }

    private Set<U32> makeOxmsRequirement(OFFactory of, TableId tableId) {
        Set<U32> required = new HashSet<>();

        if (! TableId.of(0).equals(tableId)) {
            required.add(U32.of(of.oxms().buildMetadata().getTypeLen()));
            required.add(U32.of(of.oxms().buildMetadataMasked().getTypeLen()));
        }
        return required;
    }

    @Override
    protected FloodlightResponse buildError(Throwable error) {
        return null;
    }

    @Override
    public List<SessionProxy> getCommands(IOFSwitch sw, FloodlightModuleContext moduleContext) {
        // Don't used due to custom {@link org.openkilda.floodlight.command.poc.PreparePipeline.writeCommands}
        // implementation.
        return null;
    }
}
