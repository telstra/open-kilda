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
import org.openkilda.floodlight.error.SwitchOperationException;
import org.openkilda.floodlight.utils.CompletableFutureAdapter;
import org.openkilda.floodlight.utils.SwitchPipelineAdapter;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.TableFeatures;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFTableFeatureProp;
import org.projectfloodlight.openflow.protocol.OFTableFeatures;
import org.projectfloodlight.openflow.protocol.OFTableFeaturesCommand;
import org.projectfloodlight.openflow.protocol.OFTableFeaturesStatsReply;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.U32;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class PreparePipeline extends AbstractMultiTableCommand {
    @JsonCreator
    public PreparePipeline(@JsonProperty("message_context") MessageContext messageContext,
                           @JsonProperty("switch_id") SwitchId switchId) {
        super(switchId, messageContext);
    }

    @Override
    protected CompletableFuture<Optional<OFMessage>> writeCommands(IOFSwitch sw, FloodlightModuleContext moduleContext)
            throws SwitchOperationException {
        OFFactory of = sw.getOFFactory();
        SwitchDescriptor swDesc = new SwitchDescriptor(sw);
        List<OFTableFeatures> tableFeatures = new ArrayList<>(sw.getNumTables());

        Set<TableId> allUsedTables = swDesc.getAllUsedTables();
        for (TableId tableId : sw.getTables()) {
            if (allUsedTables.contains(tableId)) {
                makeTableFeaturesUpdate(of, sw.getTableFeatures(tableId))
                        .ifPresent(tableFeatures::add);
            }
        }

        return new CompletableFutureAdapter<>(
                sw.writeRequest(of.buildTableFeaturesStatsRequest().setEntries(tableFeatures)
                                        .build()))
                .thenApply(response -> handleResponse(sw, response));
    }

    private Optional<OFMessage> handleResponse(IOFSwitch sw, OFTableFeaturesStatsReply reply) {
        new SwitchPipelineAdapter(sw).dumpPipeline(reply);
        return Optional.of(reply);
    }

    private Optional<OFTableFeatures> makeTableFeaturesUpdate(OFFactory of, TableFeatures current) {
        List<OFTableFeatureProp> update = new ArrayList<>();
        makeMatchProp(of, current)
                .ifPresent(update::add);

        if (update.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(makeTableFeaturesBuilder(of, current)
                                   .setCommand(OFTableFeaturesCommand.MODIFY)
                                   .setProperties(update)
                                   .build());
    }

    private OFTableFeatures.Builder makeTableFeaturesBuilder(OFFactory of, TableFeatures current) {
        return of.buildTableFeatures()
                .setConfig(current.getConfig())
                .setMaxEntries(current.getMaxEntries())
                .setMetadataMatch(current.getMetadataMatch())
                .setMetadataWrite(current.getMetadataWrite())
                .setTableId(current.getTableId())
                .setName(current.getTableName());
    }

    private Optional<OFTableFeatureProp> makeMatchProp(OFFactory of, TableFeatures current) {
        List<U32> matchUpdate = new ArrayList<>();
        Set<U32> matchCheck = new HashSet<>(current.getPropMatch().getOxmIds());
        for (U32 oxmId : makeOxmsRequirement(of, current.getTableId())) {
            if (matchCheck.add(oxmId)) {
                matchUpdate.add(oxmId);
            }
        }

        if (matchUpdate.isEmpty()) {
            return Optional.empty();
        }

        matchUpdate.addAll(current.getPropMatch().getOxmIds());

        return Optional.of(of.buildTableFeaturePropMatch()
                .setOxmIds(matchUpdate)
                .build());
    }

    private Set<U32> makeOxmsRequirement(OFFactory of, TableId tableId) {
        Set<U32> required = new HashSet<>();

        required.add(U32.of(of.oxms().buildMetadata().getTypeLen()));
        required.add(U32.of(of.oxms().buildMetadataMasked().getTypeLen()));

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
