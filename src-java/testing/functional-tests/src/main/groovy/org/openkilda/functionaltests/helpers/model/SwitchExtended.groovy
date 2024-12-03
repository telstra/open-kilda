package org.openkilda.functionaltests.helpers.model

import static groovyx.gpars.GParsPool.withPool
import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.hasItem
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.OTHER
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.RESET_SWITCH_MAINTENANCE
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.RESTORE_SWITCH_PROPERTIES
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.REVIVE_SWITCH
import static org.openkilda.messaging.info.event.IslChangeType.DISCOVERED
import static org.openkilda.messaging.info.event.IslChangeType.FAILED
import static org.openkilda.messaging.info.event.SwitchChangeType.ACTIVATED
import static org.openkilda.messaging.info.event.SwitchChangeType.DEACTIVATED
import static org.openkilda.model.MeterId.LACP_REPLY_METER_ID
import static org.openkilda.model.SwitchFeature.KILDA_OVS_PUSH_POP_MATCH_VXLAN
import static org.openkilda.model.SwitchFeature.KILDA_OVS_SWAP_FIELD
import static org.openkilda.model.SwitchFeature.NOVIFLOW_COPY_FIELD
import static org.openkilda.model.SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN
import static org.openkilda.model.SwitchFeature.NOVIFLOW_SWAP_ETH_SRC_ETH_DST
import static org.openkilda.model.cookie.Cookie.ARP_INGRESS_COOKIE
import static org.openkilda.model.cookie.Cookie.ARP_INPUT_PRE_DROP_COOKIE
import static org.openkilda.model.cookie.Cookie.ARP_POST_INGRESS_COOKIE
import static org.openkilda.model.cookie.Cookie.ARP_POST_INGRESS_ONE_SWITCH_COOKIE
import static org.openkilda.model.cookie.Cookie.ARP_POST_INGRESS_VXLAN_COOKIE
import static org.openkilda.model.cookie.Cookie.ARP_TRANSIT_COOKIE
import static org.openkilda.model.cookie.Cookie.CATCH_BFD_RULE_COOKIE
import static org.openkilda.model.cookie.Cookie.DROP_RULE_COOKIE
import static org.openkilda.model.cookie.Cookie.DROP_SLOW_PROTOCOLS_LOOP_COOKIE
import static org.openkilda.model.cookie.Cookie.DROP_VERIFICATION_LOOP_RULE_COOKIE
import static org.openkilda.model.cookie.Cookie.LLDP_INGRESS_COOKIE
import static org.openkilda.model.cookie.Cookie.LLDP_INPUT_PRE_DROP_COOKIE
import static org.openkilda.model.cookie.Cookie.LLDP_POST_INGRESS_COOKIE
import static org.openkilda.model.cookie.Cookie.LLDP_POST_INGRESS_ONE_SWITCH_COOKIE
import static org.openkilda.model.cookie.Cookie.LLDP_POST_INGRESS_VXLAN_COOKIE
import static org.openkilda.model.cookie.Cookie.LLDP_TRANSIT_COOKIE
import static org.openkilda.model.cookie.Cookie.MULTITABLE_EGRESS_PASS_THROUGH_COOKIE
import static org.openkilda.model.cookie.Cookie.MULTITABLE_INGRESS_DROP_COOKIE
import static org.openkilda.model.cookie.Cookie.MULTITABLE_POST_INGRESS_DROP_COOKIE
import static org.openkilda.model.cookie.Cookie.MULTITABLE_PRE_INGRESS_PASS_THROUGH_COOKIE
import static org.openkilda.model.cookie.Cookie.MULTITABLE_TRANSIT_DROP_COOKIE
import static org.openkilda.model.cookie.Cookie.ROUND_TRIP_LATENCY_RULE_COOKIE
import static org.openkilda.model.cookie.Cookie.SERVER_42_FLOW_RTT_OUTPUT_VLAN_COOKIE
import static org.openkilda.model.cookie.Cookie.SERVER_42_FLOW_RTT_OUTPUT_VXLAN_COOKIE
import static org.openkilda.model.cookie.Cookie.SERVER_42_FLOW_RTT_TURNING_COOKIE
import static org.openkilda.model.cookie.Cookie.SERVER_42_FLOW_RTT_VXLAN_TURNING_COOKIE
import static org.openkilda.model.cookie.Cookie.SERVER_42_ISL_RTT_OUTPUT_COOKIE
import static org.openkilda.model.cookie.Cookie.SERVER_42_ISL_RTT_TURNING_COOKIE
import static org.openkilda.model.cookie.Cookie.VERIFICATION_BROADCAST_RULE_COOKIE
import static org.openkilda.model.cookie.Cookie.VERIFICATION_UNICAST_RULE_COOKIE
import static org.openkilda.model.cookie.Cookie.VERIFICATION_UNICAST_VXLAN_RULE_COOKIE
import static org.openkilda.model.cookie.CookieBase.CookieType.SERVER_42_ISL_RTT_INPUT
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.helpers.KildaProperties
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.model.cleanup.CleanupAfter
import org.openkilda.functionaltests.model.cleanup.CleanupManager
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.IslInfoData
import org.openkilda.messaging.payload.flow.FlowPayload
import org.openkilda.model.MeterId
import org.openkilda.model.SwitchFeature
import org.openkilda.model.SwitchId
import org.openkilda.model.cookie.Cookie
import org.openkilda.model.cookie.CookieBase.CookieType
import org.openkilda.model.cookie.PortColourCookie
import org.openkilda.model.cookie.ServiceCookie
import org.openkilda.model.cookie.ServiceCookie.ServiceCookieTag
import org.openkilda.northbound.dto.v1.switches.MeterInfoDto
import org.openkilda.northbound.dto.v1.switches.SwitchDto
import org.openkilda.northbound.dto.v1.switches.SwitchPropertiesDto
import org.openkilda.northbound.dto.v2.switches.LagPortResponse
import org.openkilda.northbound.dto.v2.switches.MeterInfoDtoV2
import org.openkilda.northbound.dto.v2.switches.SwitchFlowsPerPortResponse
import org.openkilda.northbound.dto.v2.switches.SwitchLocationDtoV2
import org.openkilda.northbound.dto.v2.switches.SwitchPatchDto
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.floodlight.model.Floodlight
import org.openkilda.testing.service.floodlight.model.FloodlightConnectMode
import org.openkilda.testing.service.lockkeeper.LockKeeperService
import org.openkilda.testing.service.lockkeeper.model.FloodlightResourceAddress
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.northbound.NorthboundServiceV2
import org.openkilda.testing.service.northbound.payloads.SwitchSyncExtendedResult
import org.openkilda.testing.service.northbound.payloads.SwitchValidationExtendedResult
import org.openkilda.testing.service.northbound.payloads.SwitchValidationV2ExtendedResult
import org.openkilda.testing.tools.SoftAssertions

import com.fasterxml.jackson.annotation.JsonIdentityInfo
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.ObjectIdGenerators.PropertyGenerator
import groovy.transform.EqualsAndHashCode
import groovy.transform.Memoized
import groovy.transform.builder.Builder
import groovy.util.logging.Slf4j

import java.math.RoundingMode

@Slf4j
@EqualsAndHashCode(excludes = 'northbound, northboundV2, database, lockKeeper, cleanupManager')
@Builder
@JsonIdentityInfo(property = "name", generator = PropertyGenerator.class)
class SwitchExtended {

    //below values are manufacturer-specific and override default Kilda values on firmware level
    static NOVIFLOW_BURST_COEFFICIENT = 1.005 // Driven by the Noviflow specification
    static CENTEC_MIN_BURST = 1024 // Driven by the Centec specification
    static CENTEC_MAX_BURST = 32000 // Driven by the Centec specification

    //Kilda allows user to pass reserved VLAN IDs 1 and 4095 if they want.
    static final IntRange KILDA_ALLOWED_VLANS = 1..4095
    static final String LACP_COOKIE = Cookie.toString(DROP_SLOW_PROTOCOLS_LOOP_COOKIE)


    Switch sw
    List<Integer> islPorts
    List<Integer> traffGenPorts
    SwitchDto nbDetails

    @JsonIgnore
    NorthboundService northbound
    @JsonIgnore
    NorthboundServiceV2 northboundV2
    @JsonIgnore
    Database database
    @JsonIgnore
    LockKeeperService lockKeeper
    @JsonIgnore
    CleanupManager cleanupManager

    SwitchExtended(Switch sw,
                   List<Integer> islPorts,
                   List<Integer> traffGenPorts,
                   NorthboundService northbound,
                   NorthboundServiceV2 northboundV2,
                   Database database,
                   LockKeeperService lockKeeper,
                   CleanupManager cleanupManager) {
        this.sw = sw

        this.islPorts = islPorts
        this.traffGenPorts = traffGenPorts
        this.northbound = northbound
        this.northboundV2 = northboundV2
        this.database = database
        this.lockKeeper = lockKeeper
        this.cleanupManager = cleanupManager
    }

    @Override
    String toString() {
        return String.format("Switch: %s, islPorts: %s, traffGen(s) port(s) %s, nbDetails: %s",
                switchId, islPorts, traffGenPorts, nbDetails)
    }

    @JsonIgnore
    @Memoized
    SwitchRules getRulesManager() {
        return new SwitchRules(northbound, database, cleanupManager, sw.dpId)
    }

    @JsonIgnore
    @Memoized
    SwitchMeters getMetersManager() {
        return new SwitchMeters(northbound, database, sw.dpId)
    }

    SwitchId getSwitchId() {
        sw.dpId
    }

    String getOfVersion() {
        sw.ofVersion
    }

    /**
     *
     * Get list of switch ports excluding the ports which are busy with ISLs or s42.
     */
    @Memoized
    List<Integer> getPorts() {
        List<Integer> allPorts = sw.getAllPorts()
        allPorts.removeAll(islPorts)
        allPorts.removeAll([sw?.prop?.server42Port])
        allPorts.unique()
    }
    /***
     *
     * @param useTraffgenPorts allows us to select random TraffGen port for further traffic verification
     * @return random port for further interaction
     */
    PortExtended getRandomPort(boolean useTraffgenPorts = true, List<Integer> busyPort = []) {
        List<Integer> allPorts = useTraffgenPorts ? traffGenPorts : getPorts()
        def availablePorts = allPorts - busyPort
        if(!availablePorts) {
            //as default flow is generated with vlan, we can reuse the same port if all available ports have been used
            //this is a rare case for the situation when we need to create more than 20 flows in a row
            availablePorts = allPorts
        }
        Integer portNo = availablePorts.shuffled().first()
        return new PortExtended(sw, portNo, northbound, northboundV2, cleanupManager)
    }

    LagPort getLagPort(Set<Integer> portNumbers) {
        new LagPort(switchId, portNumbers, northboundV2, cleanupManager)
    }

    @Memoized
    PortExtended getPort(Integer portNo) {
        return new PortExtended(sw, portNo, northbound, northboundV2, cleanupManager)
    }

    String getDescription() {
        nbFormat().description
    }

    SwitchDto nbFormat() {
        if (!nbDetails) {
            nbDetails = northbound.getSwitch(sw.dpId)
        }
        return nbDetails
    }

    @Memoized
    boolean isVxlanEnabled() {
        return getProps().supportedTransitEncapsulation
                .contains(org.openkilda.model.FlowEncapsulationType.VXLAN.toString().toLowerCase())
    }

    String hwSwString() {
        "${nbFormat().hardware} ${nbFormat().software}"
    }

    boolean isCentec() {
        nbFormat().manufacturer.toLowerCase().contains("centec")
    }

    boolean isNoviflow() {
        nbFormat().manufacturer.toLowerCase().contains("noviflow")
    }

    boolean isVirtual() {
        nbFormat().manufacturer.toLowerCase().contains("nicira")
    }

    /**
     * A hardware with 100GB ports. Due to its nature sometimes requires special actions from Kilda
     */
    boolean isWb5164() {
        nbFormat().hardware =~ "WB5164"
    }

    static boolean isS42Supported(SwitchPropertiesDto swProps) {
        swProps?.server42Port != null && swProps?.server42MacAddress != null && swProps?.server42Vlan != null
    }

    List<Long> collectDefaultCookies() {
        def swProps = northbound.getSwitchProperties(sw.dpId)
        def multiTableRules = []
        def devicesRules = []
        def server42Rules = []
        def vxlanRules = []
        def lacpRules = []
        def toggles = northbound.getFeatureToggles()
        multiTableRules = [MULTITABLE_PRE_INGRESS_PASS_THROUGH_COOKIE, MULTITABLE_INGRESS_DROP_COOKIE,
                           MULTITABLE_POST_INGRESS_DROP_COOKIE, MULTITABLE_EGRESS_PASS_THROUGH_COOKIE,
                           MULTITABLE_TRANSIT_DROP_COOKIE, LLDP_POST_INGRESS_COOKIE, LLDP_POST_INGRESS_ONE_SWITCH_COOKIE,
                           ARP_POST_INGRESS_COOKIE, ARP_POST_INGRESS_ONE_SWITCH_COOKIE]
        def unifiedCookies = [DROP_RULE_COOKIE, VERIFICATION_BROADCAST_RULE_COOKIE,
                              VERIFICATION_UNICAST_RULE_COOKIE, DROP_VERIFICATION_LOOP_RULE_COOKIE]

        if (isVxlanFeatureEnabled()) {
            multiTableRules.addAll([LLDP_POST_INGRESS_VXLAN_COOKIE, ARP_POST_INGRESS_VXLAN_COOKIE])
            vxlanRules << VERIFICATION_UNICAST_VXLAN_RULE_COOKIE
        }

        if (swProps.switchLldp) {
            devicesRules.addAll([LLDP_INPUT_PRE_DROP_COOKIE, LLDP_TRANSIT_COOKIE, LLDP_INGRESS_COOKIE])
        }
        if (swProps.switchArp) {
            devicesRules.addAll([ARP_INPUT_PRE_DROP_COOKIE, ARP_TRANSIT_COOKIE, ARP_INGRESS_COOKIE])
        }

        northbound.getSwitchFlows(sw.dpId).each { flow ->
            [flow.source, flow.destination].findAll { ep -> ep.datapath == sw.dpId }.each { ep ->
                multiTableRules.add(new PortColourCookie(CookieType.MULTI_TABLE_INGRESS_RULES, ep.portNumber).getValue())
                if (swProps.switchLldp || ep.detectConnectedDevices.lldp) {
                    devicesRules.add(new PortColourCookie(CookieType.LLDP_INPUT_CUSTOMER_TYPE, ep.portNumber).getValue())
                }
                if (swProps.switchArp || ep.detectConnectedDevices.arp) {
                    devicesRules.add(new PortColourCookie(CookieType.ARP_INPUT_CUSTOMER_TYPE, ep.portNumber).getValue())
                }
            }
        }

        def relatedLinks= getRelatedLinks()
        relatedLinks.each {
            if (isVxlanFeatureEnabled()) {
                multiTableRules.add(new PortColourCookie(CookieType.MULTI_TABLE_ISL_VXLAN_EGRESS_RULES, it.source.portNo).getValue())
                multiTableRules.add(new PortColourCookie(CookieType.MULTI_TABLE_ISL_VXLAN_TRANSIT_RULES, it.source.portNo).getValue())
            }
            multiTableRules.add(new PortColourCookie(CookieType.MULTI_TABLE_ISL_VLAN_EGRESS_RULES, it.source.portNo).getValue())
            multiTableRules.add(new PortColourCookie(CookieType.PING_INPUT, it.source.portNo).getValue())
        }

        if ((toggles.server42IslRtt && isS42Supported(swProps) && (swProps.server42IslRtt == "ENABLED" ||
                swProps.server42IslRtt == "AUTO" && !sw.features.contains(NOVIFLOW_COPY_FIELD)))) {
            devicesRules.add(SERVER_42_ISL_RTT_TURNING_COOKIE)
            devicesRules.add(SERVER_42_ISL_RTT_OUTPUT_COOKIE)
            relatedLinks.each {
                devicesRules.add(new PortColourCookie(SERVER_42_ISL_RTT_INPUT, it.source.portNo).getValue())
            }
        }

        if (swProps.server42FlowRtt) {
            server42Rules << SERVER_42_FLOW_RTT_OUTPUT_VLAN_COOKIE
            if (isVxlanFeatureEnabled()) {
                server42Rules << SERVER_42_FLOW_RTT_OUTPUT_VXLAN_COOKIE
            }
        }
        if (toggles.server42FlowRtt) {
            if (getDbFeatures().contains(NOVIFLOW_SWAP_ETH_SRC_ETH_DST) || getDbFeatures().contains(KILDA_OVS_SWAP_FIELD)) {
                server42Rules << SERVER_42_FLOW_RTT_TURNING_COOKIE
                server42Rules << SERVER_42_FLOW_RTT_VXLAN_TURNING_COOKIE
            }
        }

        def lacpPorts = northboundV2.getLagLogicalPort(sw.dpId).findAll { it.lacpReply }
        if (!lacpPorts.isEmpty()) {
            lacpRules << new ServiceCookie(ServiceCookieTag.DROP_SLOW_PROTOCOLS_LOOP_COOKIE).getValue()
            lacpPorts.each {
                lacpRules << new PortColourCookie(CookieType.LACP_REPLY_INPUT, it.logicalPortNumber).getValue()
            }
        }
        if (isNoviflow() && !isWb5164()) {
            return ([CATCH_BFD_RULE_COOKIE, ROUND_TRIP_LATENCY_RULE_COOKIE]
                    + unifiedCookies + vxlanRules + multiTableRules + devicesRules + server42Rules + lacpRules)
        } else if ((isNoviflow() || nbFormat().manufacturer == "E") && isWb5164()) {
            return ([CATCH_BFD_RULE_COOKIE]
                    + unifiedCookies + vxlanRules + multiTableRules + devicesRules + server42Rules + lacpRules)
        } else if (sw.ofVersion == "OF_12") {
            return [VERIFICATION_BROADCAST_RULE_COOKIE]
        } else {
            return (unifiedCookies + vxlanRules + multiTableRules + devicesRules + server42Rules + lacpRules)
        }
    }

    List<Long> collectDefaultMeters() {
        if (sw.ofVersion == "OF_12") {
            return []
        }
        def swProps = northbound.getSwitchProperties(sw.dpId)
        List<MeterId> result = []
        result << MeterId.createMeterIdForDefaultRule(VERIFICATION_BROADCAST_RULE_COOKIE) //2
        result << MeterId.createMeterIdForDefaultRule(VERIFICATION_UNICAST_RULE_COOKIE) //3
        if (isVxlanFeatureEnabled()) {
            result << MeterId.createMeterIdForDefaultRule(VERIFICATION_UNICAST_VXLAN_RULE_COOKIE) //7
        }
        result << MeterId.createMeterIdForDefaultRule(LLDP_POST_INGRESS_COOKIE) //16
        result << MeterId.createMeterIdForDefaultRule(LLDP_POST_INGRESS_ONE_SWITCH_COOKIE) //18
        result << MeterId.createMeterIdForDefaultRule(ARP_POST_INGRESS_COOKIE) //22
        result << MeterId.createMeterIdForDefaultRule(ARP_POST_INGRESS_ONE_SWITCH_COOKIE) //24
        if (isVxlanFeatureEnabled()) {
            result << MeterId.createMeterIdForDefaultRule(LLDP_POST_INGRESS_VXLAN_COOKIE) //17
            result << MeterId.createMeterIdForDefaultRule(ARP_POST_INGRESS_VXLAN_COOKIE) //23
        }
        if (swProps.switchLldp) {
            result << MeterId.createMeterIdForDefaultRule(LLDP_INPUT_PRE_DROP_COOKIE) //13
            result << MeterId.createMeterIdForDefaultRule(LLDP_TRANSIT_COOKIE) //14
            result << MeterId.createMeterIdForDefaultRule(LLDP_INGRESS_COOKIE) //15
        }
        if (swProps.switchArp) {
            result << MeterId.createMeterIdForDefaultRule(ARP_INPUT_PRE_DROP_COOKIE) //19
            result << MeterId.createMeterIdForDefaultRule(ARP_TRANSIT_COOKIE) //20
            result << MeterId.createMeterIdForDefaultRule(ARP_INGRESS_COOKIE) //21
        }
        def lacpPorts = northboundV2.getLagLogicalPort(sw.dpId).findAll {
            it.lacpReply
        }
        if (!lacpPorts.isEmpty()) {
            result << MeterId.LACP_REPLY_METER_ID //31
        }

        return result*.getValue().sort()
    }

    int collectFlowRelatedRulesAmount(FlowExtended flow) {
        def swProps = getProps()
        def isSwSrcOrDst = (sw.dpId in [flow.source.switchId, flow.destination.switchId])
        def defaultAmountOfFlowRules = 2 // ingress + egress
        def amountOfServer42Rules = 0
        if(swProps.server42FlowRtt && isSwSrcOrDst) {
            amountOfServer42Rules += 1
            sw.dpId == flow.source.switchId && flow.source.vlanId && ++amountOfServer42Rules
            sw.dpId == flow.destination.switchId && flow.destination.vlanId && ++amountOfServer42Rules
        }

       defaultAmountOfFlowRules + amountOfServer42Rules + (isSwSrcOrDst ? 1 : 0)
    }


    /**
     * The same as direct northbound call, but additionally waits that default rules and default meters are indeed
     * reinstalled according to config
     */
    SwitchPropertiesDto updateProperties(SwitchPropertiesDto switchProperties) {
        cleanupManager.addAction(OTHER, { northbound.updateSwitchProperties(sw.dpId, getCashedProps()) })
        def response = northbound.updateSwitchProperties(sw.dpId, switchProperties)
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            def actualHexCookie = []
            for (long cookie : rulesManager.getRules().cookie) {
                actualHexCookie.add(new Cookie(cookie).toString())
            }
            def expectedHexCookie = []
            for (long cookie : collectDefaultCookies()) {
                expectedHexCookie.add(new Cookie(cookie).toString())
            }
            expectedHexCookie.forEach { item ->
                assertThat sw.toString(), actualHexCookie, hasItem(item)
            }

            def actualDefaultMetersIds = metersManager.getMeters().meterId.findAll {
                MeterId.isMeterIdOfDefaultRule((long) it)
            }
            assert actualDefaultMetersIds.sort() == collectDefaultMeters().sort()
        }
        return response
    }

    SwitchPropertiesDto getProps() {
        northboundV2.getAllSwitchProperties().switchProperties.find { it.switchId == sw.dpId }
    }

    @Memoized
    SwitchPropertiesDto getCashedProps() {
        getProps()
    }

    SwitchDto getDetails() {
        northbound.getSwitch(sw.dpId)
    }

    List<FlowPayload> getFlows() {
        return northbound.getSwitchFlows(sw.dpId)
    }

    List<FlowPayload> getFlows(Integer port) {
        return northbound.getSwitchFlows(sw.dpId, port)
    }

    SwitchFlowsPerPortResponse getFlowsV2(List<Integer> portIds = []){
        return northboundV2.getSwitchFlows(new SwitchId(sw.dpId.id), portIds)
    }

    List<Integer> getUsedPorts() {
        return northboundV2.getSwitchFlows(sw.dpId, []).flowsByPort.keySet().asList()
    }

    SwitchValidationV2ExtendedResult validate(String include = null, String exclude = null) {
        return northboundV2.validateSwitch(sw.dpId, include, exclude)
    }

    SwitchValidationExtendedResult validateV1() {
        return northbound.validateSwitch(sw.dpId)
    }

    Optional<SwitchValidationV2ExtendedResult> validateAndCollectFoundDiscrepancies() {
        SwitchValidationV2ExtendedResult validationResponse = northboundV2.validateSwitch(switchId)
        return validationResponse.asExpected ?
                Optional.empty() as Optional<SwitchValidationV2ExtendedResult> : Optional.of(validationResponse)
    }

    SwitchSyncExtendedResult synchronize(boolean removeExcess = true) {
        return northbound.synchronizeSwitch(sw.dpId, removeExcess)
    }

    /**
     * Synchronizes the switch and returns an optional SwitchSyncExtendedResult if the switch was in an unexpected state
     * before the synchronization.
     * @return optional SwitchSyncExtendedResult if the switch was in an unexpected state
     * before the synchronization
     */
    Optional<SwitchSyncExtendedResult> synchronizeAndCollectFixedDiscrepancies() {
        def syncResponse = synchronize(true)
        boolean isAnyDiscrepancyFound = [syncResponse.rules.missing,
                                         syncResponse.rules.misconfigured,
                                         syncResponse.rules.excess,
                                         syncResponse.meters.missing,
                                         syncResponse.meters.misconfigured,
                                         syncResponse.meters.excess].any { !it.isEmpty() }
        return isAnyDiscrepancyFound ? Optional.of(syncResponse) : Optional.empty() as Optional<SwitchSyncExtendedResult>
    }


    SwitchDto setMaintenance(boolean maintenance, boolean evacuate) {
        cleanupManager.addAction(RESET_SWITCH_MAINTENANCE, { northbound.setSwitchMaintenance(sw.dpId, false, false) })
        northbound.setSwitchMaintenance(sw.dpId, maintenance, evacuate)
    }

    def partialUpdate(SwitchPatchDto updateDto) {
        def initialSettings = northbound.getSwitch(sw.dpId)
        cleanupManager.addAction(RESTORE_SWITCH_PROPERTIES, { northboundV2.partialSwitchUpdate(sw.dpId, convertToUpdateRequest(initialSettings)) })
        return northboundV2.partialSwitchUpdate(sw.dpId, updateDto)
    }

    List<LagPortResponse> getAllLogicalPorts() {
        northboundV2.getLagLogicalPort(switchId)
    }

    List<IslInfoData> getRelatedLinks() {
        northbound.getLinks(switchId, null, null, null)
    }

    def delete(Boolean force = false) {
        return northbound.deleteSwitch(sw.dpId, force)
    }

    boolean isS42FlowRttEnabled() {
        def swProps = northbound.getSwitchProperties(sw.dpId)
        def featureToggles = northbound.getFeatureToggles()
        swProps.server42FlowRtt && featureToggles.server42FlowRtt
    }

    /***
     * Floodlight interaction
     */

    /**
     * Waits for certain switch to appear/disappear from switch list in certain floodlights.
     * Useful when knocking out switches
     *
     * @deprecated use 'northboundV2.getSwitchConnections(switchId)' instead
     */
    @Deprecated
    void waitForFlConnection(boolean shouldBePresent, List<Floodlight> floodlights) {
        Wrappers.wait(WAIT_OFFSET) {
            withPool {
                floodlights.eachParallel {
                    assert it.getFloodlightService().getSwitches()*.switchId.contains(sw.dpId) == shouldBePresent
                }
            }
        }
    }

    /**
     * Disconnect a switch from controller either removing controller settings inside an OVS switch
     * or blocking access to floodlight via iptables for a hardware switch.
     *
     * @param FL mode
     * @param waitForRelatedLinks make sure that all switch related ISLs are FAILED
     */
    List<FloodlightResourceAddress> knockout(FloodlightConnectMode mode, boolean waitForRelatedLinks, double timeout = WAIT_OFFSET) {
        def blockData = lockKeeper.knockoutSwitch(sw, mode)
        cleanupManager.addAction(REVIVE_SWITCH, { revive(blockData, true) }, CleanupAfter.TEST)
        Wrappers.wait(timeout) {
            assert northbound.getSwitch(sw.dpId).state == DEACTIVATED
        }
        if (waitForRelatedLinks) {
            Wrappers.wait(KildaProperties.DISCOVERY_TIMEOUT + timeout * 2) {
                verifyRelatedLinksState(FAILED )
            }
        }

        return blockData
    }

    List<FloodlightResourceAddress> knockout(FloodlightConnectMode mode) {
        knockout(mode, false)
    }

    List<FloodlightResourceAddress> knockout(List<String> regions) {
        def blockData = lockKeeper.knockoutSwitch(sw, regions)
        cleanupManager.addAction(REVIVE_SWITCH, { revive(blockData, true) }, CleanupAfter.TEST)
        return blockData
    }

    List<FloodlightResourceAddress> knockoutFromStatsController(){
        def blockData = lockKeeper.knockoutSwitch(sw, FloodlightConnectMode.RO)
        cleanupManager.addAction(REVIVE_SWITCH, { revive(blockData, true) }, CleanupAfter.TEST)
        return blockData
    }

    /**
     * Connect a switch to controller either adding controller settings inside an OVS switch
     * or setting proper iptables to allow access to floodlight for a hardware switch.
     *
     * @param flResourceAddress to register sw in the specific FL regions
     * @param waitForRelatedLinks make sure that all switch related ISLs are DISCOVERED
     */
    void revive(List<FloodlightResourceAddress> flResourceAddress, boolean waitForRelatedLinks) {
        lockKeeper.reviveSwitch(sw, flResourceAddress)
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getSwitch(sw.dpId).state == ACTIVATED
        }
        if (waitForRelatedLinks) {
            Wrappers.wait(KildaProperties.DISCOVERY_TIMEOUT + WAIT_OFFSET * 2) {
                verifyRelatedLinksState(DISCOVERED)
            }
        }
    }

    void revive(List<FloodlightResourceAddress> flResourceAddress) {
        revive(flResourceAddress, false)
    }

    void verifyRelatedLinksState(IslChangeType expectedState) {
        def relatedLinks = northbound.getAllLinks().findAll {
            switchId in [it.source.switchId, it.destination.switchId]
        }
        assert relatedLinks.size() == islPorts.size() * 2

        relatedLinks.each { isl -> assert isl.state == expectedState }
    }

    void waitForS42FlowRttRulesSetup(boolean isS42ToggleOn = true) {
        SwitchPropertiesDto switchDetails = getProps()
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            def expectedRulesNumber = (isS42ToggleOn && switchDetails.server42FlowRtt) ? getExpectedS42RulesBasedOnVxlanSupport() : 0
            assert rulesManager.getServer42SwitchRelatedRules().size() == expectedRulesNumber
        }
    }

    int getExpectedS42RulesBasedOnVxlanSupport() {
        //one rule per vlan/vxlan
        isVxlanEnabled() ? 2 : 1
    }

    /**
     * This method calculates expected burst for different types of switches. The common burst equals to
     * `rate * BURST_COEFFICIENT`. There are couple exceptions though:
     * NOVIFLOW: Does not use our common burst coefficient and overrides it with its own coefficient (see static
     * variables at the top of the class).
     * CENTEC: Follows our burst coefficient policy, except for restrictions for the minimum and maximum burst.
     * In cases when calculated burst is higher or lower of the Centec max/min - the max/min burst value will be used
     * instead.
     *
     * @param rate meter rate which is used to calculate burst
     * Needed to get the switch manufacturer and apply special calculations if required
     * @return the expected burst value for given switch and rate
     */
    def getExpectedBurst(long rate) {
        def burstCoefficient = KildaProperties.BURST_COEFFICIENT
        if (isNoviflow() || isWb5164()) {
            return (rate * NOVIFLOW_BURST_COEFFICIENT - 1).setScale(0, RoundingMode.CEILING)
        } else if (isCentec()) {
            def burst = (rate * burstCoefficient).toBigDecimal().setScale(0, RoundingMode.FLOOR)
            if (burst <= CENTEC_MIN_BURST) {
                return CENTEC_MIN_BURST
            } else if (burst > CENTEC_MIN_BURST && burst <= CENTEC_MAX_BURST) {
                return burst
            } else {
                return CENTEC_MAX_BURST
            }
        } else {
            return (rate * burstCoefficient).round(0)
        }
    }

    /***
     * Database interaction
     */

    @Memoized
    Set<SwitchFeature> getDbFeatures() {
        database.getSwitch(sw.dpId).features
    }

    boolean isVxlanFeatureEnabled() {
       !getDbFeatures().intersect([NOVIFLOW_PUSH_POP_VXLAN, KILDA_OVS_PUSH_POP_MATCH_VXLAN]).isEmpty()
    }
    
    static int randomVlan() {
        return randomVlan([])
    }

    static int randomVlan(List<Integer> exclusions) {
        return (KILDA_ALLOWED_VLANS - exclusions).shuffled().first()
    }

    static List<Integer> availableVlanList(List<Integer> exclusions) {
        return (KILDA_ALLOWED_VLANS - exclusions)
    }

    static SwitchPatchDto convertToUpdateRequest(SwitchDto swDetails) {
        def pop = swDetails.pop ? swDetails.pop : ""
        def location = new SwitchLocationDtoV2(swDetails.location.latitude, swDetails.location.longitude, "", "", "")
        !swDetails.location.street ?: location.setStreet(swDetails.location.street)
        !swDetails.location.city ?: location.setCity(swDetails.location.city)
        !swDetails.location.country ?: location.setCountry(swDetails.location.country)

        return new SwitchPatchDto(pop, location)
    }

    void verifyLacpRulesAndMeters(List<String> containsRules, List<String> excludesRules, boolean isLacpMeterPresent) {
        assert !validateAndCollectFoundDiscrepancies().isPresent()

        // check rules
        def hexCookies = rulesManager.getRules().cookie.collect { Cookie.toString(it) }
        assert hexCookies.containsAll(containsRules)
        assert hexCookies.intersect(excludesRules).isEmpty()

        // check meters
        def meters = metersManager.getMeters().meterId
        if (isLacpMeterPresent) {
            assert LACP_REPLY_METER_ID.value in meters
        } else {
            assert LACP_REPLY_METER_ID.value !in meters
        }
    }

    void verifyRateSizeIsCorrect(Long expected, Long actual) {
        if (isWb5164()) {
            verifyRateSizeOnWb5164(expected, actual)
        } else {
            assert Math.abs(expected - actual) <= 1
        }
    }

    void verifyBurstSizeIsCorrect(Long expected, Long actual) {
        if (isWb5164()) {
            verifyBurstSizeOnWb5164(expected, actual)
        } else {
            assert Math.abs(expected - actual) <= 1
        }
    }

    static List<String> getLagCookies(List<LagPort> ports, boolean withLacpCookie) {
        List<String> portRelatedLagCookies = ports.logicalPortNumber.collect { portNumber ->
            new PortColourCookie(CookieType.LACP_REPLY_INPUT, portNumber as int).toString()
        }
        !withLacpCookie ?: portRelatedLagCookies.add(LACP_COOKIE)
        portRelatedLagCookies
    }

    static void verifyBurstSizeOnWb5164(Long expected, Long actual) {
        //...ValidationServiceImpl.E_SWITCH_METER_RATE_EQUALS_DELTA_COEFFICIENT = 0.01
        assert Math.abs(expected - actual) <= expected * 0.01
    }

    static void verifyRateSizeOnWb5164(Long expectedRate, Long actualRate) {
        //...ValidationServiceImpl.E_SWITCH_METER_BURST_SIZE_EQUALS_DELTA_COEFFICIENT = 0.01
        assert Math.abs(expectedRate - actualRate) <= expectedRate * 0.01
    }

    static boolean isDefaultMeter(MeterInfoDto dto) {
        return MeterId.isMeterIdOfDefaultRule(dto.getMeterId())
    }

    static boolean isDefaultMeter(MeterInfoDtoV2 dto) {
        return MeterId.isMeterIdOfDefaultRule(dto.getMeterId())
    }

    /**
     * Verifies that specified rule sections in the validation response are empty.
     * NOTE: will filter out default rules, except default flow rules(multiTable flow)
     * Default flow rules for the system looks like as a simple default rule.
     * Based on that you have to use extra filter to detect these rules in missing/excess/misconfigured sections.
     */
    static void verifyRuleSectionsAreEmpty(SwitchValidationExtendedResult switchValidateInfo,
                                           List<String> sections = ["missing", "proper", "excess", "misconfigured"]) {
        def assertions = new SoftAssertions()
        sections.each { String section ->
            if (section == "proper") {
                assertions.checkSucceeds {
                    assert switchValidateInfo.rules.proper.findAll {
                        def cookie = new Cookie(it)
                        !cookie.serviceFlag && cookie.type != CookieType.SHARED_OF_FLOW
                    }.empty
                }
            } else {
                assertions.checkSucceeds { assert switchValidateInfo.rules."$section".empty }
            }
        }
        assertions.verify()
    }

    static void verifyRuleSectionsAreEmpty(SwitchValidationV2ExtendedResult switchValidateInfo,
                                           List<String> sections = ["missing", "proper", "excess", "misconfigured"]) {
        def assertions = new SoftAssertions()
        sections.each { String section ->
            if (section == "proper") {
                assertions.checkSucceeds {
                    assert switchValidateInfo.rules.proper.findAll {
                        def cookie = new Cookie(it.cookie)
                        !cookie.serviceFlag && cookie.type != CookieType.SHARED_OF_FLOW
                    }.empty
                }
            } else {
                assertions.checkSucceeds { assert switchValidateInfo.rules."$section".empty }
            }
        }
        assertions.verify()
    }

    /**
     * Verifies that specified meter sections in the validation response are empty.
     * NOTE: will filter out default meters for 'proper' section, so that switch without flow meters, but only with
     * default meters in 'proper' section is considered 'empty'
     */
    static void verifyMeterSectionsAreEmpty(SwitchValidationExtendedResult switchValidateInfo,
                                            List<String> sections = ["missing", "misconfigured", "proper", "excess"]) {
        def assertions = new SoftAssertions()
        if (switchValidateInfo.meters) {
            sections.each { section ->
                if (section == "proper") {
                    assertions.checkSucceeds {
                        assert switchValidateInfo.meters.proper.findAll { !it.defaultMeter }.empty
                    }
                } else {
                    assertions.checkSucceeds { assert switchValidateInfo.meters."$section".empty }
                }
            }
        }
        assertions.verify()
    }

    static void verifyMeterSectionsAreEmpty(SwitchValidationV2ExtendedResult switchValidateInfo,
                                            List<String> sections = ["missing", "misconfigured", "proper", "excess"]) {
        def assertions = new SoftAssertions()
        if (switchValidateInfo.meters) {
            sections.each { section ->
                if (section == "proper") {
                    assertions.checkSucceeds {
                        assert switchValidateInfo.meters.proper.findAll { !it.defaultMeter }.empty
                    }
                } else {
                    assertions.checkSucceeds { assert switchValidateInfo.meters."$section".empty }
                }
            }
        }
        assertions.verify()
    }
    /**
     * Verifies that specified hexRule sections in the validation response are empty.
     * NOTE: will filter out default rules, except default flow rules(multiTable flow)
     * Default flow rules for the system looks like as a simple default rule.
     * Based on that you have to use extra filter to detect these rules in
     * missingHex/excessHex/misconfiguredHex sections.
     */
    static void verifyHexRuleSectionsAreEmpty(SwitchValidationExtendedResult switchValidateInfo,
                                              List<String> sections = ["properHex", "excessHex", "missingHex",
                                                                       "misconfiguredHex"]) {
        def assertions = new SoftAssertions()
        sections.each { String section ->
            if (section == "properHex") {
                def defaultCookies = switchValidateInfo.rules.proper.findAll {
                    def cookie = new Cookie(it)
                    cookie.serviceFlag || cookie.type == CookieType.SHARED_OF_FLOW
                }

                def defaultHexCookies = []
                defaultCookies.each { defaultHexCookies.add(Long.toHexString(it)) }
                assertions.checkSucceeds {
                    assert switchValidateInfo.rules.properHex.findAll { !(it in defaultHexCookies) }.empty
                }
            } else {
                assertions.checkSucceeds { assert switchValidateInfo.rules."$section".empty }
            }
        }
        assertions.verify()
    }
}
