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

package org.openkilda.rulemanager;

import static java.util.stream.Collectors.toList;
import static org.openkilda.model.cookie.Cookie.DROP_RULE_COOKIE;
import static org.openkilda.model.cookie.Cookie.MULTITABLE_INGRESS_DROP_COOKIE;
import static org.openkilda.model.cookie.Cookie.MULTITABLE_POST_INGRESS_DROP_COOKIE;
import static org.openkilda.model.cookie.Cookie.MULTITABLE_TRANSIT_DROP_COOKIE;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.rulemanager.factory.FlowRulesGeneratorFactory;
import org.openkilda.rulemanager.factory.RuleGenerator;
import org.openkilda.rulemanager.factory.ServiceRulesGeneratorFactory;

import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RuleManagerImpl implements RuleManager {

    ServiceRulesGeneratorFactory serviceRulesFactory;
    FlowRulesGeneratorFactory flowRulesFactory;

    public RuleManagerImpl(RuleManagerConfig config) {
        serviceRulesFactory = new ServiceRulesGeneratorFactory(config);
        flowRulesFactory = new FlowRulesGeneratorFactory();
    }

    @Override
    public List<SpeakerCommandData> buildRulesForFlowPath(FlowPath flowPath, DataAdapter adapter) {
        // todo: implement build rules for path
        return null;
    }

    @Override
    public List<SpeakerCommandData> buildRulesForSwitch(SwitchId switchId, DataAdapter adapter) {
        Switch sw = adapter.getSwitch(switchId);
        SwitchProperties switchProperties = adapter.getSwitchProperties(switchId);

        List<SpeakerCommandData> result = buildServiceRules(sw, switchProperties);

        result.addAll(buildFlowRules(switchId, adapter));

        return result;
    }

    private List<SpeakerCommandData> buildServiceRules(Switch sw, SwitchProperties switchProperties) {
        return getServiceRuleGenerators(switchProperties).stream()
                .flatMap(g -> g.generateCommands(sw).stream())
                .collect(toList());
    }

    @VisibleForTesting
    List<RuleGenerator> getServiceRuleGenerators(SwitchProperties switchProperties) {
        List<RuleGenerator> generators = new ArrayList<>();
        generators.add(serviceRulesFactory.getTableDefaultRuleGenerator(new Cookie(DROP_RULE_COOKIE), OfTable.INPUT));
        generators.add(serviceRulesFactory.getUniCastDiscoveryRuleGenerator());
        generators.add(serviceRulesFactory.getBroadCastDiscoveryRuleGenerator());
        // TODO: add other rules

        if (switchProperties.isMultiTable()) {
            generators.add(serviceRulesFactory.getTableDefaultRuleGenerator(
                    new Cookie(MULTITABLE_INGRESS_DROP_COOKIE), OfTable.INGRESS));
            generators.add(serviceRulesFactory.getTableDefaultRuleGenerator(
                    new Cookie(MULTITABLE_TRANSIT_DROP_COOKIE), OfTable.TRANSIT));
            generators.add(serviceRulesFactory.getTableDefaultRuleGenerator(
                    new Cookie(MULTITABLE_POST_INGRESS_DROP_COOKIE), OfTable.POST_INGRESS));

            // TODO: add other rules
        }

        return generators;
    }

    private List<SpeakerCommandData> buildFlowRules(SwitchId switchId, DataAdapter adapter) {
        return adapter.getFlowPaths().values().stream()
                .flatMap(flowPath -> buildFlowRules(switchId, flowPath, adapter).stream())
                .collect(Collectors.toList());
    }

    /**
     * Builds command data only for switches present in the map. Silently skips all others.
     */
    private List<SpeakerCommandData> buildFlowRules(SwitchId switchId, FlowPath flowPath, DataAdapter adapter) {
        List<SpeakerCommandData> result = new ArrayList<>();

        SwitchId srcSwitchId = flowPath.getSrcSwitchId();
        if (switchId.equals(srcSwitchId)) {
            result.addAll(buildIngressCommands(adapter.getSwitch(srcSwitchId),
                    adapter.getSwitchProperties(srcSwitchId),
                    flowPath, adapter.getFlow(flowPath.getPathId())));
        }
        // todo: add transit, egress and other flow rules

        return result;
    }

    private List<SpeakerCommandData> buildIngressCommands(Switch sw, SwitchProperties switchProperties,
                                                          FlowPath flowPath, Flow flow) {
        List<RuleGenerator> generators = new ArrayList<>();

        generators.add(flowRulesFactory.getIngressRuleGenerator(flowPath, flow));
        // todo: add arp, lldp, flow loop, flow mirror, etc

        return generators.stream()
                .flatMap(generator -> generator.generateCommands(sw).stream())
                .collect(Collectors.toList());
    }
}
