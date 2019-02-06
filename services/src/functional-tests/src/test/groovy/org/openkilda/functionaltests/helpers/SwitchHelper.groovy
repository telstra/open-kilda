package org.openkilda.functionaltests.helpers

import org.openkilda.model.Cookie
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.northbound.NorthboundService

import groovy.transform.Memoized
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

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
}
