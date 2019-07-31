package org.openkilda.functionaltests.helpers

import org.openkilda.model.Cookie
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.v1.switches.SwitchValidationResult
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
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

    static NOVIFLOW_BURST_COEFFICIENT = 1.005 // Driven by the Noviflow specification
    static CENTEC_MIN_BURST = 1024 // Driven by the Centec specification
    static CENTEC_MAX_BURST = 32000 // Driven by the Centec specification

    @Value('${burst.coefficient}')
    double burstCoefficient
    
    @Autowired
    SwitchHelper(NorthboundService northbound) {
        this.northbound = northbound
    }

    @Memoized
    static String getDescription(Switch sw) {
        northbound.getSwitch(sw.dpId).description
    }

    @Memoized
    static String getHardware(Switch sw) {
        northbound.getSwitch(sw.dpId).switchView.description.hardware
    }

    static List<Long> getDefaultCookies(Switch sw) {
        if (sw.noviflow) {
            return [Cookie.DROP_RULE_COOKIE, Cookie.VERIFICATION_BROADCAST_RULE_COOKIE,
                    Cookie.VERIFICATION_UNICAST_RULE_COOKIE, Cookie.DROP_VERIFICATION_LOOP_RULE_COOKIE,
                    Cookie.CATCH_BFD_RULE_COOKIE, Cookie.ROUND_TRIP_LATENCY_RULE_COOKIE,
                    Cookie.VERIFICATION_UNICAST_VXLAN_RULE_COOKIE]
        } else if (sw.ofVersion == "OF_12") {
            return [Cookie.VERIFICATION_BROADCAST_RULE_COOKIE]
        } else {
            return [Cookie.DROP_RULE_COOKIE, Cookie.VERIFICATION_BROADCAST_RULE_COOKIE,
                    Cookie.VERIFICATION_UNICAST_RULE_COOKIE, Cookie.DROP_VERIFICATION_LOOP_RULE_COOKIE]
        }
    }

    static boolean isCentec(Switch sw) {
        sw.description.toLowerCase().contains("centec")
    }

    static boolean isNoviflow(Switch sw) {
        sw.description.toLowerCase().contains("noviflow") && !isWb5164(sw)
    }

    static boolean isWb5164(Switch sw) {
        getHardware(sw) =~ "WB5164"
    }

    static boolean isVirtual(Switch sw) {
        sw.description.toLowerCase().contains("nicira")
    }

    @Memoized
    static String getDescription(SwitchId sw) {
        northbound.activeSwitches.find { it.switchId == sw }.description
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
        def wb5164 = northbound.getSwitch(sw).switchView.description.hardware =~ "WB5164"
        if (descr.contains("noviflow") || wb5164) {
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
        sections.each {
            assert switchValidateInfo.meters."$it".empty
        }
    }

    /**
     * Verifies that specified sections in the validation response are empty.
     * NOTE: will filter out default rules
     */
    static void verifyRuleSectionsAreEmpty(SwitchValidationResult switchValidateInfo,
                                           List<String> sections = ["missing", "proper", "excess"]) {
        sections.each {
            assert switchValidateInfo.rules."$it".findAll { !Cookie.isDefaultRule(it) }.empty
        }
    }
}
