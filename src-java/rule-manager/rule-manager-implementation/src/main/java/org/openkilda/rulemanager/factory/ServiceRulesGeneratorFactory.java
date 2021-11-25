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

package org.openkilda.rulemanager.factory;

import org.openkilda.model.cookie.Cookie;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.factory.generator.service.BroadCastDiscoveryRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.TableDefaultRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.TablePassThroughDefaultRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.UniCastDiscoveryRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.arp.ArpIngressRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.arp.ArpInputPreDropRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.arp.ArpPostIngressOneSwitchRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.arp.ArpPostIngressRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.arp.ArpPostIngressVxlanRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.arp.ArpTransitRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.lldp.LldpIngressRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.lldp.LldpInputPreDropRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.lldp.LldpPostIngressOneSwitchRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.lldp.LldpPostIngressRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.lldp.LldpPostIngressVxlanRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.lldp.LldpTransitRuleGenerator;

public class ServiceRulesGeneratorFactory {

    private final RuleManagerConfig config;

    public ServiceRulesGeneratorFactory(RuleManagerConfig config) {
        this.config = config;
    }

    /**
     * Get default drop rule generator.
     */
    public RuleGenerator getTableDefaultRuleGenerator(Cookie cookie, OfTable table) {
        return TableDefaultRuleGenerator.builder()
                .cookie(cookie)
                .ofTable(table)
                .build();
    }

    /**
     * Get unicast discovery rule generator.
     */
    public RuleGenerator getUniCastDiscoveryRuleGenerator() {
        return UniCastDiscoveryRuleGenerator.builder()
                .config(config)
                .build();
    }

    /**
     * Get broadcast discovery rule generator.
     */
    public RuleGenerator getBroadCastDiscoveryRuleGenerator() {
        return BroadCastDiscoveryRuleGenerator.builder()
                .config(config)
                .build();
    }

    /**
     * Get table pass through default rule generator.
     */
    public TablePassThroughDefaultRuleGenerator getTablePassThroughDefaultRuleGenerator(
            Cookie cookie, OfTable goToTableId, OfTable tableId) {
        return TablePassThroughDefaultRuleGenerator.builder()
                .cookie(cookie)
                .goToTableId(goToTableId)
                .tableId(tableId)
                .build();
    }

    /**
     * Get LLDP input pre drop rule generator.
     */
    public LldpInputPreDropRuleGenerator getLldpInputPreDropRuleGenerator() {
        return LldpInputPreDropRuleGenerator.builder()
                .config(config)
                .build();
    }

    /**
     * Get LLDP ingress rule generator.
     */
    public LldpIngressRuleGenerator getLldpIngressRuleGenerator() {
        return LldpIngressRuleGenerator.builder()
                .config(config)
                .build();
    }

    /**
     * Get LLDP post ingress rule generator.
     */
    public LldpPostIngressRuleGenerator getLldpPostIngressRuleGenerator() {
        return LldpPostIngressRuleGenerator.builder()
                .config(config)
                .build();
    }

    /**
     * Get LLDP post ingress VXLAN rule generator.
     */
    public LldpPostIngressVxlanRuleGenerator getLldpPostIngressVxlanRuleGenerator() {
        return LldpPostIngressVxlanRuleGenerator.builder()
                .config(config)
                .build();
    }

    /**
     * Get LLDP one switch post ingress rule generator.
     */
    public LldpPostIngressOneSwitchRuleGenerator getLldpPostIngressOneSwitchRuleGenerator() {
        return LldpPostIngressOneSwitchRuleGenerator.builder()
                .config(config)
                .build();
    }

    /**
     * Get LLDP transit rule generator.
     */
    public LldpTransitRuleGenerator getLldpTransitRuleGenerator() {
        return LldpTransitRuleGenerator.builder()
                .config(config)
                .build();
    }

    /**
     * Get ARP input pre drop rule generator.
     */
    public ArpInputPreDropRuleGenerator getArpInputPreDropRuleGenerator() {
        return ArpInputPreDropRuleGenerator.builder()
                .config(config)
                .build();
    }

    /**
     * Get ARP ingress rule generator.
     */
    public ArpIngressRuleGenerator getArpIngressRuleGenerator() {
        return ArpIngressRuleGenerator.builder()
                .config(config)
                .build();
    }

    /**
     * Get ARP post ingress rule generator.
     */
    public ArpPostIngressRuleGenerator getArpPostIngressRuleGenerator() {
        return ArpPostIngressRuleGenerator.builder()
                .config(config)
                .build();
    }

    /**
     * Get ARP post ingress VXLAN rule generator.
     */
    public ArpPostIngressVxlanRuleGenerator getArpPostIngressVxlanRuleGenerator() {
        return ArpPostIngressVxlanRuleGenerator.builder()
                .config(config)
                .build();
    }

    /**
     * Get ARP one switch post ingress rule generator.
     */
    public ArpPostIngressOneSwitchRuleGenerator getArpPostIngressOneSwitchRuleGenerator() {
        return ArpPostIngressOneSwitchRuleGenerator.builder()
                .config(config)
                .build();
    }

    /**
     * Get ARP transit rule generator.
     */
    public ArpTransitRuleGenerator getArpTransitRuleGenerator() {
        return ArpTransitRuleGenerator.builder()
                .config(config)
                .build();
    }
}
