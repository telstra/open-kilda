package org.openkilda.functionaltests.helpers

import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.RESTORE_ISL
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

import org.openkilda.functionaltests.model.cleanup.CleanupManager
import org.openkilda.model.SwitchFeature
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.v1.switches.SwitchDto
import org.openkilda.northbound.dto.v2.switches.PortPropertiesDto
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.lockkeeper.LockKeeperService
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.northbound.NorthboundServiceV2
import org.openkilda.testing.tools.IslUtils

import groovy.transform.Memoized
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Scope
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

    @Memoized
    static SwitchDto nbFormat(Switch sw) {
        northbound.get().getSwitch(sw.dpId)
    }

    @Memoized
    static String getHwSwString(Switch sw) {
        "${sw.nbFormat().hardware} ${sw.nbFormat().software}"
    }

    @Memoized
    static Set<SwitchFeature> getFeatures(Switch sw) {
        database.get().getSwitch(sw.dpId).features
    }


    static boolean isCentec(Switch sw) {
        sw.nbFormat().manufacturer.toLowerCase().contains("centec")
    }

    static boolean isNoviflow(Switch sw) {
        sw.nbFormat().manufacturer.toLowerCase().contains("noviflow")
    }

    def setPortDiscovery(SwitchId switchId, Integer port, boolean expectedStatus) {
        if (!expectedStatus) {
            cleanupManager.addAction(RESTORE_ISL, {setPortDiscovery(switchId, port, true)})
        }
        return northboundV2.get().updatePortProperties(switchId, port,
                new PortPropertiesDto(discoveryEnabled: expectedStatus))
    }
}
