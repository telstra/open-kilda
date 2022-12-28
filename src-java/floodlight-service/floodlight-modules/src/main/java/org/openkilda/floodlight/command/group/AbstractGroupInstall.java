/* Copyright 2021 Telstra Open Source
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

package org.openkilda.floodlight.command.group;

import static java.lang.String.format;
import static org.openkilda.floodlight.switchmanager.SwitchManager.STUB_VXLAN_UDP_SRC;
import static org.openkilda.model.SwitchFeature.KILDA_OVS_PUSH_POP_MATCH_VXLAN;
import static org.openkilda.model.SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN;

import org.openkilda.floodlight.command.SpeakerCommandReport;
import org.openkilda.floodlight.model.FlowTransitData;
import org.openkilda.floodlight.utils.OfAdapter;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.MirrorConfig;
import org.openkilda.model.MirrorConfig.MirrorConfigData;
import org.openkilda.model.SwitchId;

import com.google.common.collect.Lists;
import lombok.Getter;
import org.projectfloodlight.openflow.protocol.OFBucket;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFGroupMod;
import org.projectfloodlight.openflow.protocol.OFGroupType;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFGroup;
import org.projectfloodlight.openflow.types.OFPort;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
abstract class AbstractGroupInstall<T extends SpeakerCommandReport> extends GroupCommand<T> {
    // payload
    protected final MirrorConfig mirrorConfig;
    protected final FlowTransitData flowTransitData;

    AbstractGroupInstall(MessageContext messageContext, SwitchId switchId, MirrorConfig mirrorConfig,
                         FlowTransitData flowTransitData) {
        super(messageContext, switchId);
        this.mirrorConfig = mirrorConfig;
        this.flowTransitData = flowTransitData;
    }

    protected OFGroupMod makeGroupAddOfMessage() {
        final OFFactory ofFactory = getSw().getOFFactory();
        return makeGroupModOfMessage(ofFactory, ofFactory.buildGroupAdd());
    }

    protected OFGroupMod makeGroupModifyOfMessage() {
        final OFFactory ofFactory = getSw().getOFFactory();
        return makeGroupModOfMessage(ofFactory, ofFactory.buildGroupModify());
    }

    private OFGroupMod makeGroupModOfMessage(OFFactory of, OFGroupMod.Builder builder) {
        List<OFBucket> buckets = buildGroupOfBuckets(of);
        return builder
                .setGroup(OFGroup.of((int) mirrorConfig.getGroupId().getValue()))
                .setGroupType(OFGroupType.ALL)
                .setBuckets(buckets).build();
    }

    protected List<OFBucket> buildGroupOfBuckets(OFFactory ofFactory) {
        List<OFAction> flowActions = buildFlowActions(ofFactory);
        flowActions.add(ofFactory.actions().buildOutput()
                .setPort(OFPort.of(mirrorConfig.getFlowPort())).build());
        OFBucket flowBucket = ofFactory.buildBucket()
                .setActions(flowActions)
                .setWatchGroup(OFGroup.ANY)
                .build();

        List<OFBucket> buckets = Lists.newArrayList(flowBucket);

        for (MirrorConfigData config : mirrorConfig.getMirrorConfigDataSet()) {
            List<OFAction> mirrorActions = new ArrayList<>();

            if (config.getPushVlan() != null && config.getPushVlan() > 0) {
                mirrorActions.addAll(OfAdapter.INSTANCE.makeVlanReplaceActions(ofFactory, Collections.emptyList(),
                        Lists.newArrayList(config.getPushVlan())));
            }
            if (config.getPushVxlan() != null && config.getPushVxlan().getVni() > 0) {
                mirrorActions.add(buildPushMirrorVxlan(ofFactory, config));
            }
            mirrorActions.add(ofFactory.actions().buildOutput().setPort(OFPort.of(config.getOutPort())).build());

            buckets.add(ofFactory.buildBucket().setActions(mirrorActions).setWatchGroup(OFGroup.ANY).build());
        }

        return buckets;
    }

    private OFAction buildPushMirrorVxlan(OFFactory ofFactory, MirrorConfigData config) {
        if (getSwitchFeatures().contains(NOVIFLOW_PUSH_POP_VXLAN)) {
            return OfAdapter.INSTANCE.makeNoviflowPushVxlanAction(
                    ofFactory, config.getPushVxlan().getVni(), MacAddress.of(switchId.toLong()),
                    MacAddress.of(config.getPushVxlan().getDestinationMac().toLong()), STUB_VXLAN_UDP_SRC);
        } else if (getSwitchFeatures().contains(KILDA_OVS_PUSH_POP_MATCH_VXLAN)) {
            return OfAdapter.INSTANCE.makeOvsPushVxlanAction(
                    ofFactory, config.getPushVxlan().getVni(), MacAddress.of(switchId.toLong()),
                    MacAddress.of(config.getPushVxlan().getDestinationMac().toLong()), STUB_VXLAN_UDP_SRC);
        } else {
            throw new IllegalArgumentException(String.format(
                    "To push VXLAN switch %s must support one of the following features: [%s, %s]",
                    switchId, NOVIFLOW_PUSH_POP_VXLAN, KILDA_OVS_PUSH_POP_MATCH_VXLAN));

        }
    }

    private List<OFAction> buildFlowActions(OFFactory ofFactory) {
        if (flowTransitData != null) {
            switch (flowTransitData.getEncapsulation().getType()) {
                case TRANSIT_VLAN:
                    return OfAdapter.INSTANCE.makeVlanReplaceActions(ofFactory, Collections.emptyList(),
                            Lists.newArrayList(flowTransitData.getEncapsulation().getId()));
                case VXLAN:
                    OFAction pushVxlan;
                    if (getSwitchFeatures().contains(NOVIFLOW_PUSH_POP_VXLAN)) {
                        pushVxlan = OfAdapter.INSTANCE.makeNoviflowPushVxlanAction(
                                ofFactory, flowTransitData.getEncapsulation().getId(),
                                MacAddress.of(flowTransitData.getIngressSwitchId().toLong()),
                                MacAddress.of(flowTransitData.getEgressSwitchId().toLong()),
                                STUB_VXLAN_UDP_SRC);
                    } else if (getSwitchFeatures().contains(KILDA_OVS_PUSH_POP_MATCH_VXLAN)) {
                        pushVxlan = OfAdapter.INSTANCE.makeOvsPushVxlanAction(
                                ofFactory, flowTransitData.getEncapsulation().getId(),
                                MacAddress.of(flowTransitData.getIngressSwitchId().toLong()),
                                MacAddress.of(flowTransitData.getEgressSwitchId().toLong()),
                                STUB_VXLAN_UDP_SRC);
                    } else {
                        log.error("To push VXLAN switch {} must support one of the following features: [{}, {}]",
                                switchId, NOVIFLOW_PUSH_POP_VXLAN, KILDA_OVS_PUSH_POP_MATCH_VXLAN);
                        return Lists.newArrayList();
                    }

                    return Lists.newArrayList(pushVxlan);
                default:
                    log.warn(format("Unexpected transit encapsulation type \"%s\"",
                            flowTransitData.getEncapsulation().getType()));
            }
        }

        return new ArrayList<>();
    }
}
