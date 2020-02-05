package org.openkilda.functionaltests.helpers

import static org.openkilda.model.Cookie.LLDP_INGRESS_COOKIE
import static org.openkilda.model.Cookie.LLDP_INPUT_PRE_DROP_COOKIE
import static org.openkilda.model.Cookie.LLDP_POST_INGRESS_COOKIE
import static org.openkilda.model.Cookie.LLDP_POST_INGRESS_ONE_SWITCH_COOKIE
import static org.openkilda.model.Cookie.LLDP_POST_INGRESS_VXLAN_COOKIE
import static org.openkilda.model.Cookie.LLDP_TRANSIT_COOKIE
import static org.openkilda.model.Cookie.MULTITABLE_EGRESS_PASS_THROUGH_COOKIE
import static org.openkilda.model.Cookie.MULTITABLE_INGRESS_DROP_COOKIE
import static org.openkilda.model.Cookie.MULTITABLE_POST_INGRESS_DROP_COOKIE
import static org.openkilda.model.Cookie.MULTITABLE_PRE_INGRESS_PASS_THROUGH_COOKIE
import static org.openkilda.model.Cookie.MULTITABLE_TRANSIT_DROP_COOKIE

import org.openkilda.messaging.model.SpeakerSwitchDescription
import org.openkilda.model.Cookie
import org.openkilda.model.MeterId
import org.openkilda.model.SwitchFeature
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.v1.switches.SwitchPropertiesDto
import org.openkilda.northbound.dto.v1.switches.SwitchValidationResult
import org.openkilda.testing.Constants
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.northbound.NorthboundService

import groovy.transform.Memoized
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

import java.math.RoundingMode

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
class SwitchHelper {
    static NorthboundService northbound
    static Database database

    //below values are manufacturer-specific and override default Kilda values on firmware level
    static NOVIFLOW_BURST_COEFFICIENT = 1.005 // Driven by the Noviflow specification
    static CENTEC_MIN_BURST = 1024 // Driven by the Centec specification
    static CENTEC_MAX_BURST = 32000 // Driven by the Centec specification

    @Value('${burst.coefficient}')
    double burstCoefficient

    @Autowired
    SwitchHelper(NorthboundService northbound, Database database) {
        this.northbound = northbound
        this.database = database
    }

    @Memoized
    static SpeakerSwitchDescription getDetails(Switch sw) {
        northbound.getSwitch(sw.dpId).switchView.description
    }

    @Memoized
    static String getDescription(Switch sw) {
        northbound.getSwitch(sw.dpId).description
    }

    @Memoized
    static Set<SwitchFeature> getFeatures(Switch sw) {
        database.getSwitch(sw.dpId).features
    }

    static List<Long> getDefaultMeters(Switch sw) {
        if (!sw.features.contains(SwitchFeature.METERS)) {
            return []
        }

        def meters = [
                MeterId.createMeterIdForDefaultRule(Cookie.VERIFICATION_BROADCAST_RULE_COOKIE),
                MeterId.createMeterIdForDefaultRule(Cookie.VERIFICATION_UNICAST_RULE_COOKIE)]

        if (sw.features.contains(SwitchFeature.NOVIFLOW_COPY_FIELD)) {
            meters.add(MeterId.createMeterIdForDefaultRule(Cookie.VERIFICATION_UNICAST_VXLAN_RULE_COOKIE))
        }

        def swProps = northbound.getSwitchProperties(sw.dpId)
        if (swProps.multiTable) {
            meters.add(MeterId.createMeterIdForDefaultRule(LLDP_POST_INGRESS_COOKIE))
            meters.add(MeterId.createMeterIdForDefaultRule(LLDP_POST_INGRESS_ONE_SWITCH_COOKIE)
            )
            if (sw.features.contains(SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN)) {
                meters.add(MeterId.createMeterIdForDefaultRule(LLDP_POST_INGRESS_VXLAN_COOKIE))
            }
        }
        if (swProps.switchLldp) {
            meters.addAll(
                    MeterId.createMeterIdForDefaultRule(LLDP_INPUT_PRE_DROP_COOKIE),
                    MeterId.createMeterIdForDefaultRule(LLDP_TRANSIT_COOKIE),
                    MeterId.createMeterIdForDefaultRule(LLDP_INGRESS_COOKIE))
        }

        return meters*.value
    }

    static List<Long> getDefaultCookies(Switch sw) {
        def swProps = northbound.getSwitchProperties(sw.dpId)
        def multiTableRules = []
        def switchLldpRules = []
        if (swProps.multiTable) {
            multiTableRules = [MULTITABLE_PRE_INGRESS_PASS_THROUGH_COOKIE, MULTITABLE_INGRESS_DROP_COOKIE,
                    MULTITABLE_POST_INGRESS_DROP_COOKIE, MULTITABLE_EGRESS_PASS_THROUGH_COOKIE,
                    MULTITABLE_TRANSIT_DROP_COOKIE, LLDP_POST_INGRESS_COOKIE, LLDP_POST_INGRESS_ONE_SWITCH_COOKIE]
            if (sw.features.contains(SwitchFeature.NOVIFLOW_COPY_FIELD)) {
                multiTableRules.add(LLDP_POST_INGRESS_VXLAN_COOKIE)
            }
            northbound.getLinks(sw.dpId, null, null, null).each {
                if (sw.features.contains(SwitchFeature.NOVIFLOW_COPY_FIELD)) {
                    multiTableRules.add(Cookie.encodeIslVxlanEgress(it.source.portNo))
                    multiTableRules.add(Cookie.encodeIslVxlanTransit(it.source.portNo))
                }
                multiTableRules.add(Cookie.encodeIslVlanEgress(it.source.portNo))
            }
            northbound.getSwitchFlows(sw.dpId).each {
                if (it.source.datapath.equals(sw.dpId)) {
                    multiTableRules.add(Cookie.encodeIngressRulePassThrough(it.source.portId))
                    if (swProps.switchLldp || it.source.detectConnectedDevices.lldp) {
                        switchLldpRules.add(Cookie.encodeLldpInputCustomer(it.source.portId))
                    }
                }
                if (it.destination.datapath.equals(sw.dpId)) {
                    multiTableRules.add(Cookie.encodeIngressRulePassThrough(it.destination.portId))
                    if (swProps.switchLldp || it.destination.detectConnectedDevices.lldp) {
                        switchLldpRules.add(Cookie.encodeLldpInputCustomer(it.destination.portId))
                    }
                }
            }
        }
        if (swProps.switchLldp) {
            switchLldpRules.addAll([LLDP_INPUT_PRE_DROP_COOKIE, LLDP_TRANSIT_COOKIE, LLDP_INGRESS_COOKIE])
        }
        if (sw.noviflow && !sw.wb5164) {
            return ([Cookie.DROP_RULE_COOKIE, Cookie.VERIFICATION_BROADCAST_RULE_COOKIE,
                    Cookie.VERIFICATION_UNICAST_RULE_COOKIE, Cookie.DROP_VERIFICATION_LOOP_RULE_COOKIE,
                    Cookie.CATCH_BFD_RULE_COOKIE, Cookie.ROUND_TRIP_LATENCY_RULE_COOKIE,
                    Cookie.VERIFICATION_UNICAST_VXLAN_RULE_COOKIE] + multiTableRules + switchLldpRules)
        } else if((sw.noviflow || sw.details.manufacturer == "E") && sw.wb5164){
            return ([Cookie.DROP_RULE_COOKIE, Cookie.VERIFICATION_BROADCAST_RULE_COOKIE,
                    Cookie.VERIFICATION_UNICAST_RULE_COOKIE, Cookie.DROP_VERIFICATION_LOOP_RULE_COOKIE,
                    Cookie.CATCH_BFD_RULE_COOKIE] + multiTableRules + switchLldpRules)
        } else if (sw.ofVersion == "OF_12") {
            return [Cookie.VERIFICATION_BROADCAST_RULE_COOKIE]
        } else {
            return ([Cookie.DROP_RULE_COOKIE, Cookie.VERIFICATION_BROADCAST_RULE_COOKIE,
                    Cookie.VERIFICATION_UNICAST_RULE_COOKIE, Cookie.DROP_VERIFICATION_LOOP_RULE_COOKIE]
            + multiTableRules + switchLldpRules)
        }
    }

    static boolean isCentec(Switch sw) {
        sw.details.manufacturer.toLowerCase().contains("centec")
    }

    static boolean isNoviflow(Switch sw) {
        sw.details.manufacturer.toLowerCase().contains("noviflow")
    }

    static boolean isVirtual(Switch sw) {
        sw.details.manufacturer.toLowerCase().contains("nicira")
    }

    /**
     * A hardware with 100GB ports. Due to its nature sometimes requires special actions from Kilda
     */
    static boolean isWb5164(Switch sw) {
        sw.details.hardware =~ "WB5164"
    }

    @Memoized
    static String getDescription(SwitchId sw) {
        northbound.activeSwitches.find { it.switchId == sw }.description
    }

    /**
     * The same as direct northbound call, but additionally waits that default rules and default meters are indeed
     * reinstalled according to config
     */
    static SwitchPropertiesDto updateSwitchProperties(Switch sw, SwitchPropertiesDto switchProperties) {
        def response = northbound.updateSwitchProperties(sw.dpId, switchProperties)

        // TODO(snikitin) Remove when feature https://github.com/telstra/open-kilda/issues/2969 will be implemented
        // For now need to clean LLDP meters manually
        def metersToDelete = []
        if (!switchProperties.multiTable) {
            metersToDelete.add(MeterId.createMeterIdForDefaultRule(LLDP_POST_INGRESS_COOKIE).value)
            metersToDelete.add(MeterId.createMeterIdForDefaultRule(LLDP_POST_INGRESS_ONE_SWITCH_COOKIE).value)
            if (sw.features.contains(SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN)) {
                metersToDelete.add(MeterId.createMeterIdForDefaultRule(LLDP_POST_INGRESS_VXLAN_COOKIE).value)
            }
        }
        if (!switchProperties.switchLldp) {
            metersToDelete.add(MeterId.createMeterIdForDefaultRule(LLDP_INGRESS_COOKIE).value)
            metersToDelete.add(MeterId.createMeterIdForDefaultRule(LLDP_TRANSIT_COOKIE).value)
            metersToDelete.add(MeterId.createMeterIdForDefaultRule(LLDP_INPUT_PRE_DROP_COOKIE).value)
        }
        for (long meterId : metersToDelete) {
            northbound.deleteMeter(sw.dpId, meterId)
        }

        Wrappers.wait(Constants.RULES_INSTALLATION_TIME) {
            def actualHexCookie = []
            for (long cookie : northbound.getSwitchRules(sw.dpId).flowEntries*.cookie) {
                actualHexCookie.add(Cookie.decode(cookie).toString())
            }
            def expectedHexCookie = []
            for (long cookie : sw.defaultCookies) {
                expectedHexCookie.add(Cookie.decode(cookie).toString())
            }
            assert actualHexCookie.sort() == expectedHexCookie.sort()

            def actualDefaultMetersIds = northbound.getAllMeters(sw.dpId).meterEntries*.meterId.findAll {
                MeterId.isMeterIdOfDefaultRule((long) it)
            }
            assert actualDefaultMetersIds.sort() == sw.defaultMeters.sort()
        }
        return response
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
        def details = northbound.getSwitch(sw).switchView.description
        if (descr.contains("noviflow") || details.hardware =~ "WB5164") {
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

    static void verifyMeterSectionsAreEmpty(SwitchValidationResult switchValidateInfo,
                                            List<String> sections = ["missing", "misconfigured", "proper", "excess"]) {
        if (switchValidateInfo.meters) {
            sections.each {
                assert switchValidateInfo.meters."$it".empty
            }
        }
    }

    /**
     * Verifies that specified sections in the validation response are empty.
     * NOTE: will filter out default rules, except default flow rules(multiTable flow)
     * Default flow rules for the system looks like as a simple default rule.
     * Based on that you have to use extra filter to detect these rules in missing/excess/misconfigured sections.
     */
    static void verifyRuleSectionsAreEmpty(SwitchValidationResult switchValidateInfo,
                                           List<String> sections = ["missing", "proper", "excess"]) {
        sections.each { String section ->
            if (section == "proper") {
                assert switchValidateInfo.rules.proper.findAll { !Cookie.isDefaultRule(it) }.empty
            } else {
                assert switchValidateInfo.rules."$section".findAll { cookie ->
                    Cookie.isIngressRulePassThrough(cookie) || !Cookie.isDefaultRule(cookie)
                }.empty
            }
        }
    }
}
