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

package org.openkilda.rulemanager.utils;

import static java.lang.String.format;
import static org.openkilda.model.SwitchFeature.KILDA_OVS_PUSH_POP_MATCH_VXLAN;
import static org.openkilda.model.SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN;
import static org.openkilda.rulemanager.Constants.VXLAN_DST_IPV4_ADDRESS;
import static org.openkilda.rulemanager.Constants.VXLAN_SRC_IPV4_ADDRESS;

import org.openkilda.adapter.FlowSideAdapter;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowPath;
import org.openkilda.model.MacAddress;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.OfMetadata;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.ActionType;
import org.openkilda.rulemanager.action.PopVlanAction;
import org.openkilda.rulemanager.action.PushVlanAction;
import org.openkilda.rulemanager.action.PushVxlanAction;
import org.openkilda.rulemanager.action.SetFieldAction;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public final class Utils {

    private Utils() {
    }

    /**
     * Create series of actions required to reTAG one set of vlan tags to another.
     */
    public static List<Action> makeVlanReplaceActions(List<Integer> currentVlanStack, List<Integer> targetVlanStack) {
        Iterator<Integer> currentIter = currentVlanStack.iterator();
        Iterator<Integer> targetIter = targetVlanStack.iterator();

        final List<Action> actions = new ArrayList<>();
        while (currentIter.hasNext() && targetIter.hasNext()) {
            Integer current = currentIter.next();
            Integer target = targetIter.next();
            if (current == null || target == null) {
                throw new IllegalArgumentException(
                        "Null elements are not allowed inside currentVlanStack and targetVlanStack arguments");
            }

            if (!current.equals(target)) {
                // remove all extra VLANs
                while (currentIter.hasNext()) {
                    currentIter.next();
                    actions.add(new PopVlanAction());
                }
                // rewrite existing VLAN stack "head"
                actions.add(SetFieldAction.builder().field(Field.VLAN_VID).value(target).build());
                break;
            }
        }

        // remove all extra VLANs (if previous loops ends with lack of target VLANs
        while (currentIter.hasNext()) {
            currentIter.next();
            actions.add(new PopVlanAction());
        }

        while (targetIter.hasNext()) {
            actions.add(PushVlanAction.builder().vlanId(targetIter.next().shortValue()).build());
        }
        return actions;
    }

    public static boolean isFullPortEndpoint(FlowEndpoint flowEndpoint) {
        return !FlowEndpoint.isVlanIdSet(flowEndpoint.getOuterVlanId());
    }

    /**
     * Returns port to which packets must be sent by first path switch.
     */
    public static int getOutPort(FlowPath path, Flow flow) {
        if (path.isOneSwitchFlow()) {
            FlowEndpoint endpoint = FlowSideAdapter.makeEgressAdapter(flow, path).getEndpoint();
            return endpoint.getPortNumber();
        } else {
            if (path.getSegments().isEmpty()) {
                throw new IllegalStateException(
                        format("Multi switch flow path %s has no segments", path.getPathId()));
            }
            return path.getSegments().get(0).getSrcPort();
        }
    }

    /**
     * Builds push VXLAN action.
     */
    public static PushVxlanAction buildPushVxlan(
            int vni, SwitchId srcSwitchId, SwitchId dstSwitchId, int udpSrc, Set<SwitchFeature> features) {
        ActionType type;
        if (features.contains(NOVIFLOW_PUSH_POP_VXLAN)) {
            type = ActionType.PUSH_VXLAN_NOVIFLOW;
        } else if (features.contains(KILDA_OVS_PUSH_POP_MATCH_VXLAN)) {
            type = ActionType.PUSH_VXLAN_OVS;
        } else {
            throw new IllegalArgumentException(format("To push VXLAN switch %s must support one of the following "
                    + "features: [%s, %s]", srcSwitchId, NOVIFLOW_PUSH_POP_VXLAN, KILDA_OVS_PUSH_POP_MATCH_VXLAN));
        }
        return PushVxlanAction.builder()
                .type(type)
                .vni(vni)
                .srcMacAddress(new MacAddress(srcSwitchId.toMacAddress()))
                .dstMacAddress(new MacAddress(dstSwitchId.toMacAddress()))
                .srcIpv4Address(VXLAN_SRC_IPV4_ADDRESS)
                .dstIpv4Address(VXLAN_DST_IPV4_ADDRESS)
                .udpSrc(udpSrc)
                .build();

    }

    public static OfMetadata mapMetadata(RoutingMetadata metadata) {
        return new OfMetadata(metadata.getValue(), metadata.getMask());
    }
}
