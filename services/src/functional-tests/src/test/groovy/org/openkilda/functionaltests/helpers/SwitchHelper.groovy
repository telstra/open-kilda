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

    static List<Long> getDefaultCookies(Switch sw) {
        if (sw.noviflow) {
            return [Cookie.DROP_RULE_COOKIE, Cookie.VERIFICATION_BROADCAST_RULE_COOKIE,
                    Cookie.VERIFICATION_UNICAST_RULE_COOKIE, Cookie.DROP_VERIFICATION_LOOP_RULE_COOKIE,
                    Cookie.CATCH_BFD_RULE_COOKIE]
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
        sw.description.toLowerCase().contains("noviflow")
    }

    static boolean isVirtual(Switch sw) {
        sw.description.toLowerCase().contains("nicira")
    }

    @Memoized
    String getDescription(SwitchId sw) {
        northbound.activeSwitches.find { it.switchId == sw }.description
    }

    def getExpectedBurst(SwitchId sw, long rate) {
        def descr = getDescription(sw).toLowerCase()
        if (descr.contains("noviflow")) {
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

    void verifyMeterSectionsAreEmpty(SwitchValidationResult switchValidateInfo,
                                     List<String> sections = ["missing", "misconfigured", "proper", "excess"]) {
        sections.each {
            assert switchValidateInfo.meters."$it".empty
        }
    }

    void verifyRuleSectionsAreEmpty(SwitchValidationResult switchValidateInfo,
                                    List<String> sections = ["missing", "proper", "excess"]) {
        sections.each {
            assert switchValidateInfo.rules."$it".empty
        }
    }
}
