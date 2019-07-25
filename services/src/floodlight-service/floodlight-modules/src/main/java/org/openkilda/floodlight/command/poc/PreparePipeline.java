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

import com.google.common.collect.ImmutableList;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.TableFeatures;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFTableFeatureProp;
import org.projectfloodlight.openflow.protocol.OFTableFeatures;
import org.projectfloodlight.openflow.protocol.OFTableFeaturesStatsRequest;
import org.projectfloodlight.openflow.protocol.OFTableStatsReply;
import org.projectfloodlight.openflow.types.U32;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class PreparePipeline extends AbstractMultiTableCommand {
    @Override
    protected CompletableFuture<Optional<OFMessage>> writeCommands(IOFSwitch sw, FloodlightModuleContext moduleContext)
            throws SwitchOperationException {
        OFFactory of = sw.getOFFactory();
        SwitchDescriptor swDesc = new SwitchDescriptor(sw);

        TableFeatures current = sw.getTableFeatures(swDesc.getTableDispatch());

        List<OFTableFeatureProp> update = new ArrayList<>();
        makeMatchProp(of, current)
                .ifPresent(update::add);

        if (!update.isEmpty()) {

        }
        new CompletableFutureAdapter<OFTableStatsReply>(sw.writeRequest(of.buildTableStatsRequest().build()))
        .thenAccept(features -> handleFEaturesResponse(features));



        TableFeatures current = swDesc.getSw().getTableFeatures(swDesc.getTableDispatch());

        current.createBuilder();
        OFTableFeaturesStatsRequest featuresRequest = of.buildTableFeaturesStatsRequest()
                .setEntries(ImmutableList.of(
                        of.buildTableFeatures()
                                .
                                .build()))
                .build();

        OFTableFeatures.Builder features = of.buildTableFeatures();

        Optional<OFTableFeatures> update = makeTableFeaturesUpdate(of, sw.getTableFeatures(swDesc.getTableDispatch()));

        Set<U32> requiredOxms = makeOxmsRequirement(of);
    }

    private void makeTableFeatureUpdate(IOFSwitch sw, OFTableStatsReply features) {
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

    private OFTableFeatures.Builder cloneTableFeatures(OFFactory of, TableFeatures current) {

    }

    private Optional<OFTableFeatureProp> makeMatchProp(OFFactory of, TableFeatures current) {
        List<U32> matchUpdate = new ArrayList<>();
        Set<U32> matchCheck = new HashSet<>(current.getPropMatch().getOxmIds());
        for (U32 oxmId : makeOxmsRequirement(of)) {
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

    private Set<U32> makeOxmsRequirement(OFFactory of) {
        Set<U32> required = new HashSet<>();

        required.add(U32.of(of.oxms().buildMetadata().getTypeLen()));
        required.add(U32.of(of.oxms().buildMetadataMasked().getTypeLen()));

        return required;
    }
}
