package org.openkilda.functionaltests.extension.env

import static org.openkilda.testing.Constants.SWITCHES_ACTIVATION_TIME
import static org.openkilda.testing.Constants.TOPOLOGY_DISCOVERING_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.extension.spring.SpringContextExtension
import org.openkilda.functionaltests.extension.spring.SpringContextListener
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.SwitchChangeType
import org.openkilda.messaging.model.system.FeatureTogglesDto
import org.openkilda.messaging.model.system.KildaConfigurationDto
import org.openkilda.northbound.dto.v1.links.LinkParametersDto
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.labservice.LabService
import org.openkilda.testing.service.lockkeeper.LockKeeperService
import org.openkilda.testing.service.northbound.NorthboundService

import groovy.util.logging.Slf4j
import org.spockframework.runtime.extension.AbstractGlobalExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationContext

/**
 * This extension is responsible for creating a virtual topology at the start of the test run.
 */
@Slf4j
class EnvExtension extends AbstractGlobalExtension implements SpringContextListener {

    @Autowired
    TopologyDefinition topology

    @Autowired
    NorthboundService northbound

    @Autowired
    LabService labService

    @Autowired
    LockKeeperService lockKeeper

    @Value('${spring.profiles.active}')
    String profile
    
    @Value('${use.hs}')
    boolean useHs

    @Value('${use.multitable}')
    boolean useMultitable

    @Value('${discovery.timeout}')
    int discoveryTimeout

    @Override
    void start() {
        SpringContextExtension.addListener(this)
    }

    @Override
    void notifyContextInitialized(ApplicationContext applicationContext) {
        applicationContext.autowireCapableBeanFactory.autowireBean(this)
        if (profile == "virtual") {
            log.info("Multi table is enabled by default: $useMultitable")
            northbound.updateKildaConfiguration(new KildaConfigurationDto(useMultiTable: useMultitable))
            buildVirtualEnvironment()
            log.info("Virtual topology is successfully created")
        } else if (profile == "hardware") {
            labService.createHwLab(topology)
            log.info("Successfully redirected to hardware topology")
        } else {
            throw new RuntimeException("Provided profile '$profile' is unknown. Select one of the following profiles:" +
                    " hardware, virtual")
        }
    }

    void buildVirtualEnvironment() {
        def features = FeatureTogglesDto.builder()
                .createFlowEnabled(true)
                .updateFlowEnabled(true)
                .deleteFlowEnabled(true)
                .flowsRerouteOnIslDiscoveryEnabled(true)
                .useBfdForIslIntegrityCheck(true)
                .flowsRerouteViaFlowHs(useHs)
                .floodlightRoutePeriodicSync(true)
                .build()
        northbound.toggleFeature(features)

        labService.flushLabs()
        Wrappers.wait(WAIT_OFFSET + discoveryTimeout) {
            assert northbound.getAllSwitches().findAll { it.state == SwitchChangeType.ACTIVATED }.empty
            assert northbound.getAllLinks().findAll { it.state == IslChangeType.DISCOVERED }.empty
        }
        log.info("Deleting all flows")
        northbound.deleteAllFlows()

        log.info("Deleting all links")
        northbound.getAllLinks().unique {
            [it.source.switchId.toString(), it.source.portNo,
             it.destination.switchId.toString(), it.destination.portNo].sort()
        }.each { it ->
            northbound.deleteLink(new LinkParametersDto(it.source.switchId.toString(), it.source.portNo,
                    it.destination.switchId.toString(), it.destination.portNo))
        }
        Wrappers.wait(WAIT_OFFSET / 2) {
            assert northbound.getAllLinks().empty
        }

        log.info("Deleting all switches")
        northbound.getAllSwitches().each { northbound.deleteSwitch(it.switchId, false) }
        Wrappers.wait(WAIT_OFFSET / 2) {
            assert northbound.getAllSwitches().empty
        }

        labService.createLab(topology)

        //wait until topology is discovered
        Wrappers.wait(TOPOLOGY_DISCOVERING_TIME) {
            assert northbound.getAllLinks().findAll {
                it.state == IslChangeType.DISCOVERED
            }.size() == topology.islsForActiveSwitches.size() * 2
        }
        //wait until switches are activated
        Wrappers.wait(SWITCHES_ACTIVATION_TIME) {
            assert northbound.getAllSwitches().findAll {
                it.state == SwitchChangeType.ACTIVATED
            }.size() == topology.activeSwitches.size()
        }
    }
}
