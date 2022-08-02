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

package org.openkilda.wfm.topology.switchmanager.service.impl;

import static java.lang.String.format;
import static org.openkilda.messaging.command.switches.DeleteRulesAction.DROP_ALL_ADD_DEFAULTS;
import static org.openkilda.messaging.command.switches.DeleteRulesAction.OVERWRITE_DEFAULTS;
import static org.openkilda.messaging.command.switches.DeleteRulesAction.REMOVE_ADD_DEFAULTS;
import static org.openkilda.model.cookie.Cookie.ARP_INGRESS_COOKIE;
import static org.openkilda.model.cookie.Cookie.ARP_INPUT_PRE_DROP_COOKIE;
import static org.openkilda.model.cookie.Cookie.ARP_POST_INGRESS_COOKIE;
import static org.openkilda.model.cookie.Cookie.ARP_POST_INGRESS_ONE_SWITCH_COOKIE;
import static org.openkilda.model.cookie.Cookie.ARP_POST_INGRESS_VXLAN_COOKIE;
import static org.openkilda.model.cookie.Cookie.ARP_TRANSIT_COOKIE;
import static org.openkilda.model.cookie.Cookie.CATCH_BFD_RULE_COOKIE;
import static org.openkilda.model.cookie.Cookie.DROP_RULE_COOKIE;
import static org.openkilda.model.cookie.Cookie.DROP_VERIFICATION_LOOP_RULE_COOKIE;
import static org.openkilda.model.cookie.Cookie.LLDP_INGRESS_COOKIE;
import static org.openkilda.model.cookie.Cookie.LLDP_INPUT_PRE_DROP_COOKIE;
import static org.openkilda.model.cookie.Cookie.LLDP_POST_INGRESS_COOKIE;
import static org.openkilda.model.cookie.Cookie.LLDP_POST_INGRESS_ONE_SWITCH_COOKIE;
import static org.openkilda.model.cookie.Cookie.LLDP_POST_INGRESS_VXLAN_COOKIE;
import static org.openkilda.model.cookie.Cookie.LLDP_TRANSIT_COOKIE;
import static org.openkilda.model.cookie.Cookie.MULTITABLE_EGRESS_PASS_THROUGH_COOKIE;
import static org.openkilda.model.cookie.Cookie.MULTITABLE_INGRESS_DROP_COOKIE;
import static org.openkilda.model.cookie.Cookie.MULTITABLE_POST_INGRESS_DROP_COOKIE;
import static org.openkilda.model.cookie.Cookie.MULTITABLE_PRE_INGRESS_PASS_THROUGH_COOKIE;
import static org.openkilda.model.cookie.Cookie.MULTITABLE_TRANSIT_DROP_COOKIE;
import static org.openkilda.model.cookie.Cookie.ROUND_TRIP_LATENCY_RULE_COOKIE;
import static org.openkilda.model.cookie.Cookie.SERVER_42_FLOW_RTT_OUTPUT_VLAN_COOKIE;
import static org.openkilda.model.cookie.Cookie.SERVER_42_FLOW_RTT_OUTPUT_VXLAN_COOKIE;
import static org.openkilda.model.cookie.Cookie.SERVER_42_FLOW_RTT_TURNING_COOKIE;
import static org.openkilda.model.cookie.Cookie.SERVER_42_FLOW_RTT_VXLAN_TURNING_COOKIE;
import static org.openkilda.model.cookie.Cookie.SERVER_42_ISL_RTT_OUTPUT_COOKIE;
import static org.openkilda.model.cookie.Cookie.SERVER_42_ISL_RTT_TURNING_COOKIE;
import static org.openkilda.model.cookie.Cookie.VERIFICATION_BROADCAST_RULE_COOKIE;
import static org.openkilda.model.cookie.Cookie.VERIFICATION_UNICAST_RULE_COOKIE;
import static org.openkilda.model.cookie.Cookie.VERIFICATION_UNICAST_VXLAN_RULE_COOKIE;

import org.openkilda.messaging.command.switches.DeleteRulesAction;
import org.openkilda.messaging.command.switches.InstallRulesAction;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.SpeakerData;

import java.util.function.Predicate;

/**
 * Helper class for building predicates by install/delete rules actions. Helpful to filter out OpenFlow elements
 * required to install or delete for specific action.
 */
public final class PredicateBuilder {

    private PredicateBuilder() {
    }

    /**
     * Builds predicate by install action.
     */
    public static Predicate<SpeakerData> buildInstallPredicate(InstallRulesAction action) {
        switch (action) {
            case INSTALL_DROP:
                return buildPredicate(DROP_RULE_COOKIE);
            case INSTALL_BROADCAST:
                return buildPredicate(VERIFICATION_BROADCAST_RULE_COOKIE);
            case INSTALL_UNICAST:
                return buildPredicate(VERIFICATION_UNICAST_RULE_COOKIE);
            case INSTALL_DROP_VERIFICATION_LOOP:
                return buildPredicate(DROP_VERIFICATION_LOOP_RULE_COOKIE);
            case INSTALL_BFD_CATCH:
                return buildPredicate(CATCH_BFD_RULE_COOKIE);
            case INSTALL_ROUND_TRIP_LATENCY:
                return buildPredicate(ROUND_TRIP_LATENCY_RULE_COOKIE);
            case INSTALL_UNICAST_VXLAN:
                return buildPredicate(VERIFICATION_UNICAST_VXLAN_RULE_COOKIE);
            case INSTALL_MULTITABLE_PRE_INGRESS_PASS_THROUGH:
                return buildPredicate(MULTITABLE_PRE_INGRESS_PASS_THROUGH_COOKIE);
            case INSTALL_MULTITABLE_INGRESS_DROP:
                return buildPredicate(MULTITABLE_INGRESS_DROP_COOKIE);
            case INSTALL_MULTITABLE_POST_INGRESS_DROP:
                return buildPredicate(MULTITABLE_POST_INGRESS_DROP_COOKIE);
            case INSTALL_MULTITABLE_EGRESS_PASS_THROUGH:
                return buildPredicate(MULTITABLE_EGRESS_PASS_THROUGH_COOKIE);
            case INSTALL_MULTITABLE_TRANSIT_DROP:
                return buildPredicate(MULTITABLE_TRANSIT_DROP_COOKIE);
            case INSTALL_LLDP_INPUT_PRE_DROP:
                return buildPredicate(LLDP_INPUT_PRE_DROP_COOKIE);
            case INSTALL_LLDP_INGRESS:
                return buildPredicate(LLDP_INGRESS_COOKIE);
            case INSTALL_LLDP_POST_INGRESS:
                return buildPredicate(LLDP_POST_INGRESS_COOKIE);
            case INSTALL_LLDP_POST_INGRESS_VXLAN:
                return buildPredicate(LLDP_POST_INGRESS_VXLAN_COOKIE);
            case INSTALL_LLDP_POST_INGRESS_ONE_SWITCH:
                return buildPredicate(LLDP_POST_INGRESS_ONE_SWITCH_COOKIE);
            case INSTALL_LLDP_TRANSIT:
                return buildPredicate(LLDP_TRANSIT_COOKIE);
            case INSTALL_ARP_INPUT_PRE_DROP:
                return buildPredicate(ARP_INPUT_PRE_DROP_COOKIE);
            case INSTALL_ARP_INGRESS:
                return buildPredicate(ARP_INGRESS_COOKIE);
            case INSTALL_ARP_POST_INGRESS:
                return buildPredicate(ARP_POST_INGRESS_COOKIE);
            case INSTALL_ARP_POST_INGRESS_VXLAN:
                return buildPredicate(ARP_POST_INGRESS_VXLAN_COOKIE);
            case INSTALL_ARP_POST_INGRESS_ONE_SWITCH:
                return buildPredicate(ARP_POST_INGRESS_ONE_SWITCH_COOKIE);
            case INSTALL_ARP_TRANSIT:
                return buildPredicate(ARP_TRANSIT_COOKIE);
            case INSTALL_SERVER_42_TURNING:
            case INSTALL_SERVER_42_FLOW_RTT_TURNING:
                return buildPredicate(SERVER_42_FLOW_RTT_TURNING_COOKIE);
            case INSTALL_SERVER_42_OUTPUT_VLAN:
            case INSTALL_SERVER_42_FLOW_RTT_OUTPUT_VLAN:
                return buildPredicate(SERVER_42_FLOW_RTT_OUTPUT_VLAN_COOKIE);
            case INSTALL_SERVER_42_OUTPUT_VXLAN:
            case INSTALL_SERVER_42_FLOW_RTT_OUTPUT_VXLAN:
                return buildPredicate(SERVER_42_FLOW_RTT_OUTPUT_VXLAN_COOKIE);
            case INSTALL_SERVER_42_FLOW_RTT_VXLAN_TURNING:
                return buildPredicate(SERVER_42_FLOW_RTT_VXLAN_TURNING_COOKIE);
            case INSTALL_SERVER_42_ISL_RTT_TURNING:
                return buildPredicate(SERVER_42_ISL_RTT_TURNING_COOKIE);
            case INSTALL_SERVER_42_ISL_RTT_OUTPUT:
                return buildPredicate(SERVER_42_ISL_RTT_OUTPUT_COOKIE);
            case INSTALL_DEFAULTS:
                return PredicateBuilder::allServiceRulesPredicate;
            default:
                throw new IllegalStateException(format("Unknown install rules action %s", action));
        }
    }

    /**
     * Return true if delete action requires to reinstalling service rules.
     */
    public static boolean isInstallServiceRulesRequired(DeleteRulesAction action) {
        return action == DROP_ALL_ADD_DEFAULTS || action == REMOVE_ADD_DEFAULTS || action == OVERWRITE_DEFAULTS;
    }

    /**
     * Builds predicate by delete action.
     */
    public static Predicate<SpeakerData> buildDeletePredicate(DeleteRulesAction action) {
        switch (action) {
            case DROP_ALL:
            case DROP_ALL_ADD_DEFAULTS:
                return ignore -> true;
            case REMOVE_DEFAULTS:
            case REMOVE_ADD_DEFAULTS:
                return PredicateBuilder::allServiceRulesPredicate;
            case IGNORE_DEFAULTS:
            case OVERWRITE_DEFAULTS:
                return PredicateBuilder::allNotServiceRulesPredicate;
            case REMOVE_DROP:
                return buildPredicate(DROP_RULE_COOKIE);
            case REMOVE_BROADCAST:
                return buildPredicate(VERIFICATION_BROADCAST_RULE_COOKIE);
            case REMOVE_UNICAST:
                return buildPredicate(VERIFICATION_UNICAST_RULE_COOKIE);
            case REMOVE_VERIFICATION_LOOP:
                return buildPredicate(DROP_VERIFICATION_LOOP_RULE_COOKIE);
            case REMOVE_BFD_CATCH:
                return buildPredicate(CATCH_BFD_RULE_COOKIE);
            case REMOVE_ROUND_TRIP_LATENCY:
                return buildPredicate(ROUND_TRIP_LATENCY_RULE_COOKIE);
            case REMOVE_UNICAST_VXLAN:
                return buildPredicate(VERIFICATION_UNICAST_VXLAN_RULE_COOKIE);
            case REMOVE_MULTITABLE_PRE_INGRESS_PASS_THROUGH:
                return buildPredicate(MULTITABLE_PRE_INGRESS_PASS_THROUGH_COOKIE);
            case REMOVE_MULTITABLE_INGRESS_DROP:
                return buildPredicate(MULTITABLE_INGRESS_DROP_COOKIE);
            case REMOVE_MULTITABLE_POST_INGRESS_DROP:
                return buildPredicate(MULTITABLE_POST_INGRESS_DROP_COOKIE);
            case REMOVE_MULTITABLE_EGRESS_PASS_THROUGH:
                return buildPredicate(MULTITABLE_EGRESS_PASS_THROUGH_COOKIE);
            case REMOVE_MULTITABLE_TRANSIT_DROP:
                return buildPredicate(MULTITABLE_TRANSIT_DROP_COOKIE);
            case REMOVE_LLDP_INPUT_PRE_DROP:
                return buildPredicate(LLDP_INPUT_PRE_DROP_COOKIE);
            case REMOVE_LLDP_INGRESS:
                return buildPredicate(LLDP_INGRESS_COOKIE);
            case REMOVE_LLDP_POST_INGRESS:
                return buildPredicate(LLDP_POST_INGRESS_COOKIE);
            case REMOVE_LLDP_POST_INGRESS_VXLAN:
                return buildPredicate(LLDP_POST_INGRESS_VXLAN_COOKIE);
            case REMOVE_LLDP_POST_INGRESS_ONE_SWITCH:
                return buildPredicate(LLDP_POST_INGRESS_ONE_SWITCH_COOKIE);
            case REMOVE_LLDP_TRANSIT:
                return buildPredicate(LLDP_TRANSIT_COOKIE);
            case REMOVE_ARP_INPUT_PRE_DROP:
                return buildPredicate(ARP_INPUT_PRE_DROP_COOKIE);
            case REMOVE_ARP_INGRESS:
                return buildPredicate(ARP_INGRESS_COOKIE);
            case REMOVE_ARP_POST_INGRESS:
                return buildPredicate(ARP_POST_INGRESS_COOKIE);
            case REMOVE_ARP_POST_INGRESS_VXLAN:
                return buildPredicate(ARP_POST_INGRESS_VXLAN_COOKIE);
            case REMOVE_ARP_POST_INGRESS_ONE_SWITCH:
                return buildPredicate(ARP_POST_INGRESS_ONE_SWITCH_COOKIE);
            case REMOVE_ARP_TRANSIT:
                return buildPredicate(ARP_TRANSIT_COOKIE);
            case REMOVE_SERVER_42_TURNING:
            case REMOVE_SERVER_42_FLOW_RTT_TURNING:
                return buildPredicate(SERVER_42_FLOW_RTT_TURNING_COOKIE);
            case REMOVE_SERVER_42_OUTPUT_VLAN:
            case REMOVE_SERVER_42_FLOW_RTT_OUTPUT_VLAN:
                return buildPredicate(SERVER_42_FLOW_RTT_OUTPUT_VLAN_COOKIE);
            case REMOVE_SERVER_42_OUTPUT_VXLAN:
            case REMOVE_SERVER_42_FLOW_RTT_OUTPUT_VXLAN:
                return buildPredicate(SERVER_42_FLOW_RTT_OUTPUT_VXLAN_COOKIE);
            case REMOVE_SERVER_42_FLOW_RTT_VXLAN_TURNING:
                return buildPredicate(SERVER_42_FLOW_RTT_VXLAN_TURNING_COOKIE);
            case REMOVE_SERVER_42_ISL_RTT_TURNING:
                return buildPredicate(SERVER_42_ISL_RTT_TURNING_COOKIE);
            case REMOVE_SERVER_42_ISL_RTT_OUTPUT:
                return buildPredicate(SERVER_42_ISL_RTT_OUTPUT_COOKIE);
            default:
                throw new IllegalStateException(format("Received unexpected delete switch rule action: %s", action));
        }
    }

    private static Predicate<SpeakerData> buildPredicate(long cookie) {
        return data -> {
            if (data instanceof FlowSpeakerData) {
                FlowSpeakerData flowSpeakerData = (FlowSpeakerData) data;
                return cookie == flowSpeakerData.getCookie().getValue();
            }
            return false;
        };
    }

    /**
     * Return true if speaker data is a service rule.
     */
    public static boolean allServiceRulesPredicate(SpeakerData data) {
        if (data instanceof FlowSpeakerData) {
            FlowSpeakerData flowSpeakerData = (FlowSpeakerData) data;
            return flowSpeakerData.getCookie().getServiceFlag();
        }
        return false;
    }

    /**
     * Return true if speaker data is a not-service rule.
     */
    private static boolean allNotServiceRulesPredicate(SpeakerData data) {
        if (data instanceof FlowSpeakerData) {
            FlowSpeakerData flowSpeakerData = (FlowSpeakerData) data;
            return !flowSpeakerData.getCookie().getServiceFlag();
        }
        return false;
    }
}
