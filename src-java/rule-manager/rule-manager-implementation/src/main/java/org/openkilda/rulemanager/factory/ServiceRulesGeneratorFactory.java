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

import org.openkilda.model.MacAddress;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.factory.generator.service.BfdCatchRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.BroadCastDiscoveryRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.DropDiscoveryLoopRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.TableDefaultRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.TablePassThroughDefaultRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.UniCastDiscoveryRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.UnicastVerificationVxlanRuleGenerator;
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
import org.openkilda.rulemanager.factory.generator.service.noviflow.RoundTripLatencyRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.server42.Server42FlowRttOutputVlanRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.server42.Server42FlowRttOutputVxlanRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.server42.Server42FlowRttTurningRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.server42.Server42FlowRttVxlanTurningRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.server42.Server42IslRttInputRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.server42.Server42IslRttOutputRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.server42.Server42IslRttTurningRuleGenerator;

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
     * Get drop discovery loop rule generator.
     */
    public RuleGenerator getDropDiscoveryLoopRuleGenerator() {
        return DropDiscoveryLoopRuleGenerator.builder()
                .config(config)
                .build();
    }

    /**
     * Get BFD catch rule generator.
     */
    public RuleGenerator getBfdCatchRuleGenerator() {
        return new BfdCatchRuleGenerator();
    }

    /**
     * Get round trip latency rule generator.
     */
    public RuleGenerator getRoundTripLatencyRuleGenerator() {
        return RoundTripLatencyRuleGenerator.builder()
                .config(config)
                .build();
    }

    /**
     * Get unicast verification VXLAN rule generator.
     */
    public RuleGenerator getUnicastVerificationVxlanRuleGenerator() {
        return UnicastVerificationVxlanRuleGenerator.builder()
                .config(config)
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

    /**
     * Get Server 42 Flow RTT turning rule generator.
     */
    public Server42FlowRttTurningRuleGenerator getServer42FlowRttTurningRuleGenerator() {
        return new Server42FlowRttTurningRuleGenerator();
    }

    /**
     * Get Server 42 Flow RTT Vxlan turning rule generator.
     */
    public Server42FlowRttVxlanTurningRuleGenerator getServer42FlowRttVxlanTurningRuleGenerator() {
        return new Server42FlowRttVxlanTurningRuleGenerator();
    }

    /**
     * Get Server 42 Flow RTT output vlan rule generator.
     */
    public Server42FlowRttOutputVlanRuleGenerator getServer42FlowRttOutputVlanRuleGenerator(
            int server42Port, int server42Vlan, MacAddress server42MacAddress) {
        return Server42FlowRttOutputVlanRuleGenerator.builder()
                .server42Port(server42Port)
                .server42Vlan(server42Vlan)
                .server42MacAddress(server42MacAddress)
                .build();
    }

    /**
     * Get Server 42 Flow RTT output VXLAN rule generator.
     */
    public Server42FlowRttOutputVxlanRuleGenerator getServer42FlowRttOutputVxlanRuleGenerator(
            int server42Port, int server42Vlan, MacAddress server42MacAddress) {
        return Server42FlowRttOutputVxlanRuleGenerator.builder()
                .server42Port(server42Port)
                .server42Vlan(server42Vlan)
                .server42MacAddress(server42MacAddress)
                .build();
    }

    /**
     * Get Server 42 ISL RTT input rule generator.
     */
    public Server42IslRttInputRuleGenerator getServer42IslRttInputRuleGenerator(int server42Port, int islPort) {
        return Server42IslRttInputRuleGenerator.builder()
                .config(config)
                .server42Port(server42Port)
                .islPort(islPort)
                .build();
    }

    /**
     * Get Server 42 ISL RTT turning rule generator.
     */
    public Server42IslRttTurningRuleGenerator getServer42IslRttTurningRuleGenerator() {
        return Server42IslRttTurningRuleGenerator.builder()
                .config(config)
                .build();
    }

    /**
     * Get Server 42 ISL RTT output rule generator.
     */
    public Server42IslRttOutputRuleGenerator getServer42IslRttOutputRuleGenerator(
            int server42Port, int server42Vlan, MacAddress server42MacAddress) {
        return Server42IslRttOutputRuleGenerator.builder()
                .config(config)
                .server42Port(server42Port)
                .server42Vlan(server42Vlan)
                .server42MacAddress(server42MacAddress)
                .build();
    }
}
