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

package org.openkilda.floodlight.switchmanager.factory.generator.server42;

import static org.openkilda.floodlight.switchmanager.SwitchFlowUtils.actionSetUdpDstAction;
import static org.openkilda.floodlight.switchmanager.SwitchFlowUtils.actionSetUdpSrcAction;
import static org.openkilda.floodlight.switchmanager.SwitchFlowUtils.buildInstructionApplyActions;
import static org.openkilda.floodlight.switchmanager.SwitchFlowUtils.instructionGoToTable;
import static org.openkilda.floodlight.switchmanager.SwitchFlowUtils.prepareFlowModBuilder;
import static org.openkilda.floodlight.switchmanager.SwitchManager.INPUT_TABLE_ID;
import static org.openkilda.floodlight.switchmanager.SwitchManager.NOVIFLOW_TIMESTAMP_SIZE_IN_BITS;
import static org.openkilda.floodlight.switchmanager.SwitchManager.PRE_INGRESS_TABLE_ID;
import static org.openkilda.floodlight.switchmanager.SwitchManager.SERVER_42_FORWARD_UDP_PORT;
import static org.openkilda.floodlight.switchmanager.SwitchManager.SERVER_42_INPUT_PRIORITY;
import static org.openkilda.model.SwitchFeature.NOVIFLOW_COPY_FIELD;
import static org.openkilda.model.cookie.Cookie.encodeServer42InputInput;

import org.openkilda.floodlight.KildaCore;
import org.openkilda.floodlight.service.FeatureDetectorService;
import org.openkilda.floodlight.switchmanager.factory.SwitchFlowTuple;
import org.openkilda.floodlight.switchmanager.factory.generator.SwitchFlowGenerator;
import org.openkilda.floodlight.utils.metadata.RoutingMetadata;
import org.openkilda.model.MacAddress;
import org.openkilda.model.SwitchFeature;

import com.google.common.collect.ImmutableList;
import lombok.Builder;
import net.floodlightcontroller.core.IOFSwitch;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.oxm.OFOxms;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.TransportPort;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class Server42InputFlowGenerator implements SwitchFlowGenerator {
    private final FeatureDetectorService featureDetectorService;
    private final KildaCore kildaCore;
    private final int server42Port;
    private final int customerPort;
    private final MacAddress server42macAddress;

    @Builder
    public Server42InputFlowGenerator(
            FeatureDetectorService featureDetectorService, KildaCore kildaCore, int server42Port, int customerPort,
            MacAddress server42macAddress) {
        this.featureDetectorService = featureDetectorService;
        this.kildaCore = kildaCore;
        this.server42Port = server42Port;
        this.customerPort = customerPort;
        this.server42macAddress = server42macAddress;
    }

    /**
     * Generated OFFlowMod for Server 42 input rule.
     */
    public static Optional<OFFlowMod> generateFlowMod(
            OFFactory ofFactory, Set<SwitchFeature> features, int udpOffset, int customerPort, int server42Port,
            MacAddress server42macAddress) {
        if (!features.contains(NOVIFLOW_COPY_FIELD)) {
            return Optional.empty();
        }

        Match match = buildMatch(ofFactory, server42Port, customerPort + udpOffset, server42macAddress);


        List<OFAction> actions = ImmutableList.of(
                actionSetUdpSrcAction(ofFactory, TransportPort.of(SERVER_42_FORWARD_UDP_PORT)),
                actionSetUdpDstAction(ofFactory, TransportPort.of(SERVER_42_FORWARD_UDP_PORT)),
                buildCopyTimestamp(ofFactory));

        List<OFInstruction> instructions = ImmutableList.of(
                buildInstructionApplyActions(ofFactory, actions),
                instructionWriteMetadata(ofFactory, customerPort, features),
                instructionGoToTable(ofFactory, TableId.of(PRE_INGRESS_TABLE_ID)));

        return Optional.of(prepareFlowModBuilder(
                ofFactory, encodeServer42InputInput(customerPort), SERVER_42_INPUT_PRIORITY, INPUT_TABLE_ID)
                .setMatch(match)
                .setInstructions(instructions)
                .build());
    }

    @Override
    public SwitchFlowTuple generateFlow(IOFSwitch sw) {
        Set<SwitchFeature> features = featureDetectorService.detectSwitch(sw);
        Optional<OFFlowMod> flowMod = generateFlowMod(
                sw.getOFFactory(), features, kildaCore.getConfig().getServer42UdpPortOffset(), customerPort,
                server42Port, server42macAddress);

        if (!flowMod.isPresent()) {
            return SwitchFlowTuple.EMPTY;
        }

        return SwitchFlowTuple.builder()
                .sw(sw)
                .flow(flowMod.get())
                .build();
    }

    private static Match buildMatch(OFFactory ofFactory, int server42Port, int udpSrcPort,
                                    MacAddress server42macAddress) {
        return ofFactory.buildMatch()
                .setExact(MatchField.IN_PORT, OFPort.of(server42Port))
                .setExact(MatchField.ETH_SRC, org.projectfloodlight.openflow.types.MacAddress.of(
                        server42macAddress.toString()))
                .setExact(MatchField.ETH_TYPE, EthType.IPv4)
                .setExact(MatchField.IP_PROTO, IpProtocol.UDP)
                .setExact(MatchField.UDP_SRC, TransportPort.of(udpSrcPort))
                .build();
    }

    private static OFAction buildCopyTimestamp(OFFactory factory) {
        OFOxms oxms = factory.oxms();
        return factory.actions().buildNoviflowCopyField()
                .setNBits(NOVIFLOW_TIMESTAMP_SIZE_IN_BITS)
                .setSrcOffset(0)
                .setDstOffset(0)
                .setOxmSrcHeader(oxms.buildNoviflowTxtimestamp().getTypeLen())
                .setOxmDstHeader(oxms.buildNoviflowUpdPayload().getTypeLen())
                .build();
    }


    private static OFInstruction instructionWriteMetadata(
            OFFactory ofFactory, int customerPort, Set<SwitchFeature> features) {
        RoutingMetadata metadata = RoutingMetadata.builder().inputPort(customerPort).build(features);
        return ofFactory.instructions().buildWriteMetadata()
                .setMetadata(metadata.getValue())
                .setMetadataMask(metadata.getMask())
                .build();
    }
}
