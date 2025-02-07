package org.openkilda.functionaltests.helpers

import org.openkilda.messaging.model.SwitchPropertiesDto.RttState
import org.openkilda.testing.service.northbound.payloads.SwitchSyncExtendedResult

import groovy.transform.Memoized
import org.openkilda.functionaltests.helpers.model.SwitchDbData
import org.openkilda.functionaltests.model.cleanup.CleanupAfter
import org.openkilda.functionaltests.model.cleanup.CleanupManager
import org.openkilda.messaging.command.switches.DeleteRulesAction
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.SwitchChangeType
import org.openkilda.messaging.info.rule.FlowEntry
import org.openkilda.model.FlowEncapsulationType
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
import org.openkilda.northbound.dto.v2.flows.FlowMirrorPointPayload
import org.openkilda.northbound.dto.v2.switches.LagPortRequest
import org.openkilda.northbound.dto.v2.switches.LagPortResponse
import org.openkilda.northbound.dto.v2.switches.MeterInfoDtoV2
import org.openkilda.northbound.dto.v2.switches.PortPropertiesDto
import org.openkilda.northbound.dto.v2.switches.SwitchDtoV2
import org.openkilda.northbound.dto.v2.switches.SwitchFlowsPerPortResponse
import org.openkilda.northbound.dto.v2.switches.SwitchLocationDtoV2
import org.openkilda.northbound.dto.v2.switches.SwitchPatchDto
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.model.topology.TopologyDefinition.SwitchProperties
import org.openkilda.testing.model.topology.TopologyDefinition.TraffGen
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.floodlight.model.Floodlight
import org.openkilda.testing.service.floodlight.model.FloodlightConnectMode
import org.openkilda.testing.service.lockkeeper.LockKeeperService
import org.openkilda.testing.service.lockkeeper.model.FloodlightResourceAddress
import org.openkilda.testing.service.lockkeeper.model.TrafficControlData
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.northbound.NorthboundServiceV2
import org.openkilda.testing.service.northbound.payloads.SwitchValidationExtendedResult
import org.openkilda.testing.service.northbound.payloads.SwitchValidationV2ExtendedResult
import org.openkilda.testing.service.traffexam.TraffExamService
import org.openkilda.testing.tools.ConnectedDevice
import org.openkilda.testing.tools.IslUtils
import org.openkilda.testing.tools.SoftAssertions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

import java.math.RoundingMode

import static groovyx.gpars.GParsPool.withPool
import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.hasItem
import static org.hamcrest.Matchers.notNullValue
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.DELETE_LAG_LOGICAL_PORT
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.DELETE_MIRROR
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.OTHER
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.RESET_SWITCH_MAINTENANCE
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.RESTORE_ISL
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.RESTORE_SWITCH_PROPERTIES
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.REVIVE_SWITCH
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.SYNCHRONIZE_SWITCH
import static org.openkilda.model.SwitchFeature.KILDA_OVS_PUSH_POP_MATCH_VXLAN
import static org.openkilda.model.SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN
import static org.openkilda.model.cookie.Cookie.ARP_INGRESS_COOKIE
import static org.openkilda.model.cookie.Cookie.ARP_INPUT_PRE_DROP_COOKIE
import static org.openkilda.model.cookie.Cookie.ARP_POST_INGRESS_COOKIE
import static org.openkilda.model.cookie.Cookie.ARP_POST_INGRESS_ONE_SWITCH_COOKIE
import static org.openkilda.model.cookie.Cookie.ARP_POST_INGRESS_VXLAN_COOKIE
import static org.openkilda.model.cookie.Cookie.ARP_TRANSIT_COOKIE
import static org.openkilda.model.cookie.Cookie.CATCH_BFD_RULE_COOKIE
import static org.openkilda.model.cookie.Cookie.DROP_RULE_COOKIE
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
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

/**
 * Provides helper operations for some Switch-related content.
 * This helper is also injected as a Groovy Extension Module, so methods can be called in two ways:
 * <pre>
 * {@code
 * Switch sw = topology.activeSwitches[0]
 * def descriptionJ = SwitchHelper.getDescription(sw) //Java way
 * def descriptionG = sw.description //Groovysh way
 *}
 * </pre>
 */
@Component
@Scope(SCOPE_PROTOTYPE)
class SwitchHelper {
    static InheritableThreadLocal<NorthboundService> northbound = new InheritableThreadLocal<>()
    static InheritableThreadLocal<NorthboundServiceV2> northboundV2 = new InheritableThreadLocal<>()
    static InheritableThreadLocal<Database> database = new InheritableThreadLocal<>()
    static InheritableThreadLocal<TopologyDefinition> topology = new InheritableThreadLocal<>()

    //below values are manufacturer-specific and override default Kilda values on firmware level
    static NOVIFLOW_BURST_COEFFICIENT = 1.005 // Driven by the Noviflow specification
    static CENTEC_MIN_BURST = 1024 // Driven by the Centec specification
    static CENTEC_MAX_BURST = 32000 // Driven by the Centec specification

    //Kilda allows user to pass reserved VLAN IDs 1 and 4095 if they want.
    static final IntRange KILDA_ALLOWED_VLANS = 1..4095

    @Value('${burst.coefficient}')
    double burstCoefficient
    @Value('${discovery.generic.interval}')
    int discoveryInterval
    @Value('${discovery.timeout}')
    int discoveryTimeout
    @Autowired
    IslUtils islUtils
    @Autowired
    LockKeeperService lockKeeper
    @Autowired
    CleanupManager cleanupManager

    @Autowired
    SwitchHelper(@Qualifier("northboundServiceImpl") NorthboundService northbound,
                 @Qualifier("northboundServiceV2Impl") NorthboundServiceV2 northboundV2,
                 Database database, TopologyDefinition topology) {
        this.northbound.set(northbound)
        this.northboundV2.set(northboundV2)
        this.database.set(database)
        this.topology.set(topology)
    }

    static String getDescription(Switch sw) {
        sw.nbFormat().description
    }

    @Memoized
    static String getHwSwString(Switch sw) {
        "${sw.nbFormat().hardware} ${sw.nbFormat().software}"
    }

    @Memoized
    static String getHwSwString(SwitchDto sw) {
        "${sw.hardware} ${sw.software}"
    }

    @Memoized
    static String getHwSwString(SwitchDtoV2 sw) {
        "${sw.hardware} ${sw.software}"
    }

    static List<TraffGen> getTraffGens(Switch sw) {
        topology.get().activeTraffGens.findAll { it.switchConnected.dpId == sw.dpId }
    }

    static int getRandomAvailablePort(Switch sw, TopologyDefinition topologyDefinition, boolean useTraffgenPorts = true, List<Integer> busyPort = []) {
        List<Integer> allowedPorts = topologyDefinition.getAllowedPortsForSwitch(sw)
        def availablePorts = allowedPorts - busyPort
        if(!availablePorts) {
            //as default flow is generated with vlan, we can reuse the same port if all available ports have been used
            //this is a rare case for the situation when we need to create more than 20 flows in a row
            availablePorts = allowedPorts
        }
        def port = availablePorts[new Random().nextInt(availablePorts.size())]
        if (useTraffgenPorts) {
            List<Integer> tgPorts = sw.traffGens*.switchPort.findAll { availablePorts.contains(it) }
            if (tgPorts) {
                port = tgPorts[0]
            } else {
                tgPorts = sw.traffGens*.switchPort.findAll { allowedPorts.contains(it) }
                if (tgPorts) {
                    port = tgPorts[0]
                }
            }
        }
        return port
    }

    @Memoized
    static SwitchDto nbFormat(Switch sw) {
        northbound.get().getSwitch(sw.dpId)
    }

    @Memoized
    static Set<SwitchFeature> getFeatures(Switch sw) {
        database.get().getSwitch(sw.dpId).features
    }

    static List dumpAllSwitches() {
        database.get().dumpAllSwitches().collect { new SwitchDbData((it.data))}
    }

    static List<Long> getDefaultCookies(Switch sw) {
        def swProps = northbound.get().getSwitchProperties(sw.dpId)
        def multiTableRules = []
        def devicesRules = []
        def server42Rules = []
        def vxlanRules = []
        def lacpRules = []
        def toggles = northbound.get().getFeatureToggles()
        multiTableRules = [MULTITABLE_PRE_INGRESS_PASS_THROUGH_COOKIE, MULTITABLE_INGRESS_DROP_COOKIE,
                           MULTITABLE_POST_INGRESS_DROP_COOKIE, MULTITABLE_EGRESS_PASS_THROUGH_COOKIE,
                           MULTITABLE_TRANSIT_DROP_COOKIE, LLDP_POST_INGRESS_COOKIE, LLDP_POST_INGRESS_ONE_SWITCH_COOKIE,
                           ARP_POST_INGRESS_COOKIE, ARP_POST_INGRESS_ONE_SWITCH_COOKIE]
        def unifiedCookies = [DROP_RULE_COOKIE, VERIFICATION_BROADCAST_RULE_COOKIE,
                              VERIFICATION_UNICAST_RULE_COOKIE, DROP_VERIFICATION_LOOP_RULE_COOKIE]


        boolean isVxlanSupported = sw.features.contains(NOVIFLOW_PUSH_POP_VXLAN) || sw.features.contains(KILDA_OVS_PUSH_POP_MATCH_VXLAN)

        if (isVxlanSupported) {
            multiTableRules.addAll([LLDP_POST_INGRESS_VXLAN_COOKIE, ARP_POST_INGRESS_VXLAN_COOKIE])
            vxlanRules << VERIFICATION_UNICAST_VXLAN_RULE_COOKIE
        }

        if (swProps.switchLldp) {
            devicesRules.addAll([LLDP_INPUT_PRE_DROP_COOKIE, LLDP_TRANSIT_COOKIE, LLDP_INGRESS_COOKIE])
        }
        if (swProps.switchArp) {
            devicesRules.addAll([ARP_INPUT_PRE_DROP_COOKIE, ARP_TRANSIT_COOKIE, ARP_INGRESS_COOKIE])
        }

        northbound.get().getSwitchFlows(sw.dpId).each { flow ->
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

        def relatedLinks =  northbound.get().getLinks(sw.dpId, null, null, null)
        relatedLinks.each {
            if (isVxlanSupported) {
                multiTableRules.add(new PortColourCookie(CookieType.MULTI_TABLE_ISL_VXLAN_EGRESS_RULES, it.source.portNo).getValue())
                multiTableRules.add(new PortColourCookie(CookieType.MULTI_TABLE_ISL_VXLAN_TRANSIT_RULES, it.source.portNo).getValue())
            }
            multiTableRules.add(new PortColourCookie(CookieType.MULTI_TABLE_ISL_VLAN_EGRESS_RULES, it.source.portNo).getValue())
            multiTableRules.add(new PortColourCookie(CookieType.PING_INPUT, it.source.portNo).getValue())
        }

        if (toggles.server42IslRtt && doesSwSupportS42(swProps) && (swProps.server42IslRtt == "ENABLED" ||
                swProps.server42IslRtt == "AUTO" && !sw.features.contains(SwitchFeature.NOVIFLOW_COPY_FIELD))) {
            devicesRules.add(SERVER_42_ISL_RTT_TURNING_COOKIE)
            devicesRules.add(SERVER_42_ISL_RTT_OUTPUT_COOKIE)
            relatedLinks.each {
                devicesRules.add(new PortColourCookie(CookieType.SERVER_42_ISL_RTT_INPUT, it.source.portNo).getValue())
            }
        }

        if (swProps.server42FlowRtt) {
            server42Rules << SERVER_42_FLOW_RTT_OUTPUT_VLAN_COOKIE
            if (isVxlanSupported) {
                server42Rules << SERVER_42_FLOW_RTT_OUTPUT_VXLAN_COOKIE
            }
        }
        if (toggles.server42FlowRtt) {
            if (sw.features.contains(SwitchFeature.NOVIFLOW_SWAP_ETH_SRC_ETH_DST) || sw.features.contains(SwitchFeature.KILDA_OVS_SWAP_FIELD)) {
                server42Rules << SERVER_42_FLOW_RTT_TURNING_COOKIE
                server42Rules << SERVER_42_FLOW_RTT_VXLAN_TURNING_COOKIE
            }
        }

        def lacpPorts = northboundV2.get().getLagLogicalPort(sw.dpId).findAll { it.lacpReply }
        if (!lacpPorts.isEmpty()) {
            lacpRules << new ServiceCookie(ServiceCookieTag.DROP_SLOW_PROTOCOLS_LOOP_COOKIE).getValue()
            lacpPorts.each {
                lacpRules << new PortColourCookie(CookieType.LACP_REPLY_INPUT, it.logicalPortNumber).getValue()
            }
        }
        if (sw.noviflow && !sw.wb5164) {
            return ([CATCH_BFD_RULE_COOKIE, ROUND_TRIP_LATENCY_RULE_COOKIE]
                    + unifiedCookies + vxlanRules + multiTableRules + devicesRules + server42Rules + lacpRules)
        } else if ((sw.noviflow || sw.nbFormat().manufacturer == "E") && sw.wb5164) {
            return ([CATCH_BFD_RULE_COOKIE]
                    + unifiedCookies + vxlanRules + multiTableRules + devicesRules + server42Rules + lacpRules)
        } else if (sw.ofVersion == "OF_12") {
            return [VERIFICATION_BROADCAST_RULE_COOKIE]
        } else {
            return (unifiedCookies + vxlanRules + multiTableRules + devicesRules + server42Rules + lacpRules)
        }
    }

    static boolean doesSwSupportS42(SwitchPropertiesDto swProps) {
        swProps.server42Port != null && swProps.server42MacAddress != null && swProps.server42Vlan != null
    }

    static List<Long> getDefaultMeters(Switch sw) {
        if (sw.ofVersion == "OF_12") {
            return []
        }
        def swProps = northbound.get().getSwitchProperties(sw.dpId)
        List<MeterId> result = []
        result << MeterId.createMeterIdForDefaultRule(VERIFICATION_BROADCAST_RULE_COOKIE) //2
        result << MeterId.createMeterIdForDefaultRule(VERIFICATION_UNICAST_RULE_COOKIE) //3
        if (sw.features.contains(NOVIFLOW_PUSH_POP_VXLAN) || sw.features.contains(KILDA_OVS_PUSH_POP_MATCH_VXLAN)) {
            result << MeterId.createMeterIdForDefaultRule(VERIFICATION_UNICAST_VXLAN_RULE_COOKIE) //7
        }
        result << MeterId.createMeterIdForDefaultRule(LLDP_POST_INGRESS_COOKIE) //16
        result << MeterId.createMeterIdForDefaultRule(LLDP_POST_INGRESS_ONE_SWITCH_COOKIE) //18
        result << MeterId.createMeterIdForDefaultRule(ARP_POST_INGRESS_COOKIE) //22
        result << MeterId.createMeterIdForDefaultRule(ARP_POST_INGRESS_ONE_SWITCH_COOKIE) //24
        if (sw.features.contains(NOVIFLOW_PUSH_POP_VXLAN) || sw.features.contains(KILDA_OVS_PUSH_POP_MATCH_VXLAN)) {
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
        def lacpPorts = northboundV2.get().getLagLogicalPort(sw.dpId).findAll {
            it.lacpReply
        }
        if (!lacpPorts.isEmpty()) {
            result << MeterId.LACP_REPLY_METER_ID //31
        }

        return result*.getValue().sort()
    }

    static boolean isCentec(Switch sw) {
        sw.nbFormat().manufacturer.toLowerCase().contains("centec")
    }

    static boolean isNoviflow(Switch sw) {
        sw.nbFormat().manufacturer.toLowerCase().contains("noviflow")
    }

    static boolean isVirtual(Switch sw) {
        sw.nbFormat().manufacturer.toLowerCase().contains("nicira")
    }

    /**
     * A hardware with 100GB ports. Due to its nature sometimes requires special actions from Kilda
     */
    static boolean isWb5164(Switch sw) {
        sw.nbFormat().hardware =~ "WB5164"
    }

    @Memoized
    static String getDescription(SwitchId sw) {
        northbound.get().activeSwitches.find { it.switchId == sw }.description
    }

    /**
     * The same as direct northbound call, but additionally waits that default rules and default meters are indeed
     * reinstalled according to config
     */
    SwitchPropertiesDto updateSwitchProperties(Switch sw, SwitchPropertiesDto switchProperties) {
        cleanupManager.addAction(OTHER, {northbound.get().updateSwitchProperties(sw.dpId, getCachedSwProps(sw.dpId))})
        def response = northbound.get().updateSwitchProperties(sw.dpId, switchProperties)
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            def actualHexCookie = []
            for (long cookie : northbound.get().getSwitchRules(sw.dpId).flowEntries*.cookie) {
                actualHexCookie.add(new Cookie(cookie).toString())
            }
            def expectedHexCookie = []
            for (long cookie : sw.defaultCookies) {
                expectedHexCookie.add(new Cookie(cookie).toString())
            }
            expectedHexCookie.forEach { item ->
                assertThat sw.toString(), actualHexCookie, hasItem(item)
            }


            def actualDefaultMetersIds = northbound.get().getAllMeters(sw.dpId).meterEntries*.meterId.findAll {
                MeterId.isMeterIdOfDefaultRule((long) it)
            }
            assert actualDefaultMetersIds.sort() == sw.defaultMeters.sort()
        }
        return response
    }

    static SwitchFlowsPerPortResponse getFlowsV2(Switch sw, List<Integer> portIds = []){
        return northboundV2.get().getSwitchFlows(new SwitchId(sw.getDpId().getId()), portIds)
    }

    static List<Integer> getUsedPorts(SwitchId switchId) {
        return northboundV2.get().getSwitchFlows(switchId, []).flowsByPort.keySet().asList()
    }

    /**
     * This method calculates expected burst for different types of switches. The common burst equals to
     * `rate * burstCoefficient`. There are couple exceptions though:<br>
     * <b>Noviflow</b>: Does not use our common burst coefficient and overrides it with its own coefficient (see static
     * variables at the top of the class).<br>
     * <b>Centec</b>: Follows our burst coefficient policy, except for restrictions for the minimum and maximum burst.
     * In cases when calculated burst is higher or lower of the Centec max/min - the max/min burst value will be used
     * instead.
     *
     * @param sw switchId where burst is being calculated. Needed to get the switch manufacturer and apply special
     * calculations if required
     * @param rate meter rate which is used to calculate burst
     * @return the expected burst value for given switch and rate
     */
    def getExpectedBurst(SwitchId sw, long rate) {
        def descr = getDescription(sw).toLowerCase()
        def hardware = northbound.get().getSwitch(sw).hardware
        if (descr.contains("noviflow") || hardware =~ "WB5164") {
            return (rate * NOVIFLOW_BURST_COEFFICIENT - 1).setScale(0, RoundingMode.CEILING)
        } else if (descr.contains("centec")) {
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

    static boolean isDefaultMeter(MeterInfoDto dto) {
        return MeterId.isMeterIdOfDefaultRule(dto.getMeterId())
    }

    static boolean isDefaultMeter(MeterInfoDtoV2 dto) {
        return MeterId.isMeterIdOfDefaultRule(dto.getMeterId())
    }

    /**
     * Verifies that actual and expected burst size are the same.
     */
    static void verifyBurstSizeIsCorrect(Switch sw, Long expected, Long actual) {
        if (sw.isWb5164()) {
            assert Math.abs(expected - actual) <= expected * 0.01
        } else {
            assert Math.abs(expected - actual) <= 1
        }
    }

    static void verifyRateSizeIsCorrect(Switch sw, Long expected, Long actual) {
        if (sw.isWb5164()) {
            assert Math.abs(expected - actual) <= expected * 0.01
        } else {
            assert Math.abs(expected - actual) <= 1
        }
    }

    static SwitchProperties getDummyServer42Props() {
        return new SwitchProperties(true, 33, "00:00:00:00:00:00", 1, null)
    }

    /**
     * Waits for certain switch to appear/disappear from switch list in certain floodlights.
     * Useful when knocking out switches
     *
     * @deprecated use 'northboundV2.getSwitchConnections(switchId)' instead
     */
    @Deprecated
    static void waitForSwitchFlConnection(Switch sw, boolean shouldBePresent, List<Floodlight> floodlights) {
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
     * @param sw switch which is going to be disconnected
     * @param waitForRelatedLinks make sure that all switch related ISLs are FAILED
     */
    List<FloodlightResourceAddress> knockoutSwitch(Switch sw, FloodlightConnectMode mode, boolean waitForRelatedLinks, double timeout = WAIT_OFFSET) {
        def blockData = lockKeeper.knockoutSwitch(sw, mode)
        cleanupManager.addAction(REVIVE_SWITCH, { reviveSwitch(sw, blockData, true) }, CleanupAfter.TEST)
        Wrappers.wait(timeout) {
            assert northbound.get().getSwitch(sw.dpId).state == SwitchChangeType.DEACTIVATED
        }
        if (waitForRelatedLinks) {
            def swIsls = topology.get().getRelatedIsls(sw)
            Wrappers.wait(discoveryTimeout + timeout * 2) {
                def allIsls = northbound.get().getAllLinks()
                swIsls.each { assert islUtils.getIslInfo(allIsls, it).get().state == IslChangeType.FAILED }
            }
        }

        return blockData
    }

    List<FloodlightResourceAddress> knockoutSwitch(Switch sw, FloodlightConnectMode mode) {
        knockoutSwitch(sw, mode, false)
    }

    List<FloodlightResourceAddress> knockoutSwitch(Switch sw, List<String> regions) {
        def blockData = lockKeeper.knockoutSwitch(sw, regions)
        cleanupManager.addAction(REVIVE_SWITCH, { reviveSwitch(sw, blockData, true) }, CleanupAfter.TEST)
        return blockData
    }

    List<FloodlightResourceAddress> knockoutSwitchFromStatsController(Switch sw){
        def blockData = lockKeeper.knockoutSwitch(sw, FloodlightConnectMode.RO)
        cleanupManager.addAction(REVIVE_SWITCH, { reviveSwitch(sw, blockData, true) }, CleanupAfter.TEST)
        return blockData
    }

    /**
     * Connect a switch to controller either adding controller settings inside an OVS switch
     * or setting proper iptables to allow access to floodlight for a hardware switch.
     *
     * @param sw switch which is going to be connected
     * @param waitForRelatedLinks make sure that all switch related ISLs are DISCOVERED
     */
    void reviveSwitch(Switch sw, List<FloodlightResourceAddress> flResourceAddress, boolean waitForRelatedLinks) {
        lockKeeper.reviveSwitch(sw, flResourceAddress)
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.get().getSwitch(sw.dpId).state == SwitchChangeType.ACTIVATED
        }
        if (waitForRelatedLinks) {
            def swIsls = topology.get().getRelatedIsls(sw)

            Wrappers.wait(discoveryInterval + WAIT_OFFSET * 2) {
                def allIsls = northbound.get().getAllLinks()
                swIsls.each {
                    assert islUtils.getIslInfo(allIsls, it).get().state == IslChangeType.DISCOVERED
                    assert islUtils.getIslInfo(allIsls, it.reversed).get().state == IslChangeType.DISCOVERED
                }
            }
        }
    }

    void reviveSwitch(Switch sw, List<FloodlightResourceAddress> flResourceAddress) {
        reviveSwitch(sw, flResourceAddress, false)
    }

    static void verifySectionInSwitchValidationInfo(SwitchValidationV2ExtendedResult switchValidateInfo,
                                                    List<String> sections = ["groups", "meters", "logical_ports", "rules"]) {
        sections.each { String section ->
            assertThat(switchValidateInfo."$section", notNullValue())
        }

    }

    static void verifySectionsAsExpectedFields(SwitchValidationV2ExtendedResult switchValidateInfo,
                                               List<String> sections = ["groups", "meters", "logical_ports", "rules"]) {
        boolean result = true;
        sections.each { String section ->
            if (!switchValidateInfo."$section".asExpected) {
                result = false
            }
        }
        assert result == switchValidateInfo.asExpected
    }

    static SwitchSyncExtendedResult synchronize(SwitchId switchId, boolean removeExcess=true) {
        return northbound.get().synchronizeSwitch(switchId, removeExcess)
    }

    /**
     * Synchronizes each switch from the list and returns a map of SwitchSyncExtendedResults, where the key is
     * SwitchId and the value is result of synchronization if there were entries which had to be fixed.
     * I.e. if all the switches were in expected state, then empty list is returned. If there were only
     * two switches in unexpected state, than resulting map will have only two entries, etc.
     * @param switchesToSynchronize SwitchIds which should be synchronized
     * @return Map of SwitchIds and SwitchSyncExtendedResult for switches which weren't in expected state before
     * the synchronization
     */
    static Map<SwitchId, SwitchSyncExtendedResult> synchronizeAndCollectFixedDiscrepancies(List<SwitchId> switchesToSynchronize) {
        return withPool {
            switchesToSynchronize.collectParallel { [it, northbound.get().synchronizeSwitch(it, true)] }
            .collectEntries { [(it[0]): it[1]] }
                    .findAll {
                        [it.getValue().getRules().getMissing(),
                        it.getValue().getRules().getMisconfigured(),
                        it.getValue().getRules().getExcess(),
                        it.getValue().getMeters().getMissing(),
                        it.getValue().getMeters().getMisconfigured(),
                        it.getValue().getMeters().getExcess()].any {!it.isEmpty()}
                    }
        }
    }

    /**
     * Synchronizes the switch and returns an optional SwitchSyncExtendedResult if the switch was in an unexpected state
     * before the synchronization.
     * @param switchToSynchronize SwitchId to synchronize
     * @return optional SwitchSyncExtendedResult if the switch was in an unexpected state
     * before the synchronization
     */
    static Optional<SwitchSyncExtendedResult> synchronizeAndCollectFixedDiscrepancies(SwitchId switchToSynchronize) {
        return Optional.ofNullable(synchronizeAndCollectFixedDiscrepancies([switchToSynchronize]).get(switchToSynchronize))
    }

    /**
     * Alias for the method for the same name but accepts Set instead of List
     * @param switchesToSynchronize
     * @return
     */
    static Map<SwitchId, SwitchSyncExtendedResult> synchronizeAndCollectFixedDiscrepancies(Set<SwitchId> switchesToSynchronize) {
        return synchronizeAndCollectFixedDiscrepancies(switchesToSynchronize as List)
    }

    static Map<SwitchId, SwitchValidationV2ExtendedResult> validateAndCollectFoundDiscrepancies(List<SwitchId> switchesToValidate) {
        return withPool {
            switchesToValidate.collectParallel { [it, northboundV2.get().validateSwitch(it)] }
                    .collectEntries { [(it[0]): it[1]] }
                    .findAll { !it.getValue().isAsExpected() }
        }
    }

    static Optional<SwitchValidationV2ExtendedResult> validateAndCollectFoundDiscrepancies(SwitchId switchToValidate) {
        return Optional.ofNullable(validateAndCollectFoundDiscrepancies([switchToValidate]).get(switchToValidate))
    }

    static void synchronizeAndValidateRulesInstallation(Switch srcSwitch, Switch dstSwitch) {
        synchronizeAndCollectFixedDiscrepancies([srcSwitch.dpId, dstSwitch.dpId])
        [srcSwitch, dstSwitch].each { sw ->
            Wrappers.wait(RULES_INSTALLATION_TIME) {
                validate(sw.dpId).verifyRuleSectionsAreEmpty()
            }
        }
    }

    static SwitchValidationV2ExtendedResult validate(SwitchId switchId, String include = null, String exclude = null) {
        return northboundV2.get().validateSwitch(switchId, include, exclude)
    }

    static SwitchValidationExtendedResult validateV1(SwitchId switchId) {
        return northbound.get().validateSwitch(switchId)
    }

    static List<SwitchValidationV2ExtendedResult> validate(List<SwitchId> switchesToValidate) {
        return switchesToValidate.collect { validate(it) }
                    .findAll {!it.isAsExpected()}
    }

    SwitchDto setSwitchMaintenance(SwitchId switchId, boolean maintenance, boolean evacuate) {
        cleanupManager.addAction(RESET_SWITCH_MAINTENANCE, {northbound.get().setSwitchMaintenance(switchId, false, false)})
        northbound.get().setSwitchMaintenance(switchId, maintenance, evacuate)
    }

    List<Long> deleteSwitchRules(SwitchId switchId, DeleteRulesAction deleteAction) {
        cleanupManager.addAction(SYNCHRONIZE_SWITCH, {northbound.get().synchronizeSwitch(switchId, true)})
        return northbound.get().deleteSwitchRules(switchId, deleteAction)
    }

    List<Long> deleteSwitchRules(SwitchId switchId, Long cookieId) {
        cleanupManager.addAction(SYNCHRONIZE_SWITCH, {northbound.get().synchronizeSwitch(switchId, true)})
        return northbound.get().deleteSwitchRules(switchId, cookieId)
    }

    List<Long> deleteSwitchRules(SwitchId switchId, Integer inPort, Integer inVlan, String encapsulationType,
                                 Integer outPort) {
        cleanupManager.addAction(SYNCHRONIZE_SWITCH, {northbound.get().synchronizeSwitch(switchId, true)})
        return northbound.get().deleteSwitchRules(switchId, inPort, inVlan, encapsulationType, outPort)
    }

    List<Long> deleteSwitchRules(SwitchId switchId, int priority) {
        cleanupManager.addAction(SYNCHRONIZE_SWITCH, {northbound.get().synchronizeSwitch(switchId, true)})
        return northbound.get().deleteSwitchRules(switchId, priority)
    }

    LagPortResponse createLagLogicalPort(SwitchId swichtId, Set<Integer> portNumbers, boolean lacpReply = null) {
        cleanupManager.addAction(DELETE_LAG_LOGICAL_PORT, {safeDeleteAllLogicalLagPorts(swichtId)})
        northboundV2.get().createLagLogicalPort(swichtId, new LagPortRequest(portNumbers , lacpReply))
    }

    def deleteSwitch(SwitchId switchId, Boolean force = false) {
        return northbound.get().deleteSwitch(switchId, force)
    }

    def addMirrorPoint(String flowId, FlowMirrorPointPayload mirrorPointPayload) {
        cleanupManager.addAction(DELETE_MIRROR, {safeDeleteMirrorPoint(flowId, mirrorPointPayload.mirrorPointId)})
        return northboundV2.get().createMirrorPoint(flowId, mirrorPointPayload)
    }

    def safeDeleteMirrorPoint(String flowId, String mirrorPointId) {
        if (mirrorPointId in northboundV2.get().getMirrorPoints(flowId).points*.mirrorPointId) {
            return northboundV2.get().deleteMirrorPoint(flowId, mirrorPointId)
        }
    }

    def safeDeleteAllLogicalLagPorts(SwitchId swichtId) {
        return northboundV2.get().getLagLogicalPort(swichtId).each {
            northboundV2.get().deleteLagLogicalPort(swichtId, it.getLogicalPortNumber())
        }
    }

    def setPortDiscovery(SwitchId switchId, Integer port, boolean expectedStatus) {
        if (!expectedStatus) {
            cleanupManager.addAction(RESTORE_ISL, {setPortDiscovery(switchId, port, true)})
        }
        return northboundV2.get().updatePortProperties(switchId, port,
                new PortPropertiesDto(discoveryEnabled: expectedStatus))
    }

    @Memoized
    static boolean isVxlanEnabled(SwitchId switchId) {
        return getCachedSwProps(switchId).supportedTransitEncapsulation
                .contains(FlowEncapsulationType.VXLAN.toString().toLowerCase())
    }

    @Memoized
    static List<SwitchPropertiesDto> getCachedAllSwProps() {
        return northboundV2.get().getAllSwitchProperties().switchProperties
    }

    @Memoized
    static SwitchPropertiesDto getCachedSwProps(SwitchId switchId) {
        return getCachedAllSwProps().find { it.switchId == switchId }
    }

    def setServer42FlowRttForSwitch(Switch sw, boolean isServer42FlowRttEnabled, boolean isS42ToggleOn = true) {
        def originalProps = northbound.get().getSwitchProperties(sw.dpId)
        if (originalProps.server42FlowRtt != isServer42FlowRttEnabled) {
            def s42Config = sw.prop
            def requiredProps = originalProps.jacksonCopy().tap {
                server42FlowRtt = isServer42FlowRttEnabled
                server42MacAddress = s42Config ? s42Config.server42MacAddress : null
                server42Port = s42Config ? s42Config.server42Port : null
                server42Vlan = s42Config ? s42Config.server42Vlan : null
            }

            cleanupManager.addAction(RESTORE_SWITCH_PROPERTIES, {
                northbound.get().updateSwitchProperties(sw.dpId, requiredProps.jacksonCopy().tap { server42FlowRtt = sw?.prop?.server42FlowRtt })
            })

            northbound.get().updateSwitchProperties(sw.dpId, requiredProps)
        }
        int expectedNumberOfS42Rules = (isS42ToggleOn && isServer42FlowRttEnabled) ? getExpectedS42SwitchRulesBasedOnVxlanSupport(sw.dpId) : 0
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            assert getS42SwitchRules(sw.dpId).size() == expectedNumberOfS42Rules
        }
        return originalProps.server42FlowRtt
    }

    def setServer42IslRttForSwitch(Switch sw, boolean isServer42IslRttEnabled) {
        String requiredState = isServer42IslRttEnabled ? RttState.ENABLED.toString() : RttState.DISABLED.toString()
        def originalProps = getCachedSwProps(sw.dpId)
        if (originalProps.server42IslRtt != requiredState) {
            def requiredProps = originalProps.jacksonCopy().tap {
                server42IslRtt = requiredState
                def props = sw.prop ?: dummyServer42Props
                server42MacAddress = props.server42MacAddress
                server42Port = props.server42Port
                server42Vlan = props.server42Vlan
            }

            cleanupManager.addAction(RESTORE_SWITCH_PROPERTIES, {
                northbound.get().updateSwitchProperties(sw.dpId, requiredProps.jacksonCopy().tap {
                    server42IslRtt = (sw?.prop?.server42IslRtt == null ? "AUTO" : (sw?.prop?.server42IslRtt ? "ENABLED" : "DISABLED"))
                })
            })

            northbound.get().updateSwitchProperties(sw.dpId, requiredProps)
        }

        return originalProps.server42IslRtt
    }

    def setServer42IslRttAndWaitForRulesInstallation(Switch sw, boolean isServer42IslRttEnabled, boolean isS42ToggleOn) {
        setServer42IslRttForSwitch(sw, isServer42IslRttEnabled)
        waitForS42IslRulesSetUp(sw, isServer42IslRttEnabled, isS42ToggleOn)
    }
    static List<FlowEntry> getS42SwitchRules(SwitchId swId) {
        northbound.get().getSwitchRules(swId).flowEntries
                .findAll { it.cookie in [SERVER_42_FLOW_RTT_OUTPUT_VLAN_COOKIE, SERVER_42_FLOW_RTT_OUTPUT_VXLAN_COOKIE] }
    }

    static int getExpectedS42SwitchRulesBasedOnVxlanSupport(SwitchId swId) {
        //one rule per vlan/vxlan
        isVxlanEnabled(swId) ? 2 : 1
    }

    static void waitForS42SwRulesSetup(boolean isS42ToggleOn = true) {
        List<SwitchPropertiesDto> switchDetails = northboundV2.get().getAllSwitchProperties().switchProperties
                .findAll { it.switchId in getTopology().get().switches.dpId }
        withPool {
            Wrappers.wait(RULES_INSTALLATION_TIME + WAIT_OFFSET) {
                switchDetails.eachParallel { sw ->
                    def expectedRulesNumber = (isS42ToggleOn && sw.server42FlowRtt) ? getExpectedS42SwitchRulesBasedOnVxlanSupport(sw.switchId) : 0
                    assert getS42SwitchRules(sw.switchId).size() == expectedRulesNumber
                }
            }
        }
    }

    static void waitForS42IslRulesSetUp(Switch sw, boolean isServer42IslRttEnabled, boolean isS42ToggleOn) {
        def countOfRules = isS42ToggleOn && isServer42IslRttEnabled ?
                (northbound.get().getLinks(sw.dpId, null, null, null).size() + 2) : 0

        Wrappers.wait(RULES_INSTALLATION_TIME) {
            def actualS42IslRulesOnSwitch = northbound.get().getSwitchRules(sw.dpId).flowEntries
                    .findAll {(new Cookie(it.cookie).getType() in [CookieType.SERVER_42_ISL_RTT_INPUT] ||
                                it.cookie in [SERVER_42_ISL_RTT_TURNING_COOKIE, SERVER_42_ISL_RTT_OUTPUT_COOKIE])}
            assert actualS42IslRulesOnSwitch.size() == countOfRules
        }
    }

    void shapeSwitchesTraffic(List<Switch> switches, TrafficControlData tcData) {
        cleanupManager.addAction(OTHER, {lockKeeper.cleanupTrafficShaperRules(switches*.regions.flatten())})
        lockKeeper.shapeSwitchesTraffic(switches, tcData)
    }

    void cleanupTrafficShaperRules(List<Switch> switches) {
        lockKeeper.cleanupTrafficShaperRules(switches*.regions.flatten())
    }

    def addConnectedDevice(TraffExamService examService, TraffGen tg, List<Integer> vlanId) {
        cleanupManager.addAction(OTHER, {database.get().removeConnectedDevices(tg.getSwitchConnected().dpId)})
        def device = new ConnectedDevice(examService, tg, vlanId)
        cleanupManager.addAction(OTHER, {device.close()})
        return device
    }

    def partialUpdate(SwitchId switchId, SwitchPatchDto updateDto) {
        def initialSettings = northbound.get().getSwitch(switchId)
        cleanupManager.addAction(RESTORE_SWITCH_PROPERTIES,
                {northboundV2.get().partialSwitchUpdate(switchId,
                        new SwitchPatchDto(initialSettings.pop, initialSettings.location as SwitchLocationDtoV2))
                }
        )
        return northboundV2.get().partialSwitchUpdate(switchId, updateDto)
    }

    static boolean isServer42Supported(SwitchId switchId) {
        def swProps = northbound.get().getSwitchProperties(switchId)
        def featureToggles = northbound.get().getFeatureToggles()
        def isServer42 = swProps.server42FlowRtt && featureToggles.server42FlowRtt
        return isServer42
    }

    static int randomVlan() {
        return randomVlan([])
    }

    static int randomVlan(List<Integer> exclusions) {
        return (KILDA_ALLOWED_VLANS - exclusions).shuffled().first()
    }

    static List<Integer> availableVlanList(List<Integer> exclusions) {
        return (KILDA_ALLOWED_VLANS - exclusions).shuffled()
    }
}