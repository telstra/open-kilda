package org.openkilda.functionaltests.extension.env

import static org.openkilda.testing.Constants.SWITCHES_ACTIVATION_TIME
import static org.openkilda.testing.Constants.TOPOLOGY_DISCOVERING_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.extension.spring.SpringContextListener
import org.openkilda.functionaltests.extension.spring.SpringContextNotifier
import org.openkilda.functionaltests.helpers.SwitchHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.SwitchChangeType
import org.openkilda.messaging.model.system.FeatureTogglesDto
import org.openkilda.messaging.model.system.KildaConfigurationDto
import org.openkilda.model.FlowEncapsulationType
import org.openkilda.model.SwitchFeature
import org.openkilda.northbound.dto.v1.links.LinkParametersDto
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.floodlight.model.Floodlight
import org.openkilda.testing.service.labservice.LabService
import org.openkilda.testing.service.lockkeeper.LockKeeperService
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.northbound.NorthboundServiceV2
import org.openkilda.testing.tools.TopologyPool

import groovy.util.logging.Slf4j
import org.spockframework.runtime.extension.AbstractGlobalExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationContext

import java.util.concurrent.TimeUnit

/**
 * This extension is responsible for creating a virtual topology at the start of the test run.
 */
@Slf4j
class EnvExtension extends AbstractGlobalExtension implements SpringContextListener {
    @Autowired //init for getting swFeatures (sw.features)
    SwitchHelper switchHelper

    @Autowired
    TopologyPool topologyPool

    @Autowired
    TopologyDefinition topology

    @Autowired @Qualifier("northboundServiceImpl")
    NorthboundService northbound

    @Autowired @Qualifier("northboundServiceV2Impl")
    NorthboundServiceV2 northboundV2

    @Autowired @Qualifier("islandNb")
    NorthboundService islandNorthbound

    @Autowired
    LabService labService

    @Autowired
    LockKeeperService lockKeeper

    @Autowired
    List<Floodlight> floodlights

    @Value('${spring.profiles.active}')
    String profile

    @Value('${use.multitable}')
    boolean useMultitable

    @Value('${discovery.timeout}')
    int discoveryTimeout

    @Override
    void start() {
        SpringContextNotifier.addListener(this)
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
                .floodlightRoutePeriodicSync(true)
                .collectGrpcStats(true)
                .server42FlowRtt(true)
                .server42IslRtt(true)
                .modifyYFlowEnabled(true)
                .syncSwitchOnConnect(true)
                .build()
        northbound.toggleFeature(features)
        log.info("Deleting all flows")
        northboundV2.getAllYFlows().each { northboundV2.deleteYFlow(it.getYFlowId()) }
        northboundV2.getAllHaFlows().each { northboundV2.deleteHaFlow(it.getHaFlowId()) }
        northbound.deleteAllFlows()
        Wrappers.wait(WAIT_OFFSET) { assert northbound.getAllFlows().empty }
        labService.flushLabs()
        Wrappers.wait(WAIT_OFFSET + discoveryTimeout) {
            assert northbound.getAllSwitches().findAll { it.state == SwitchChangeType.ACTIVATED }.empty
            assert northbound.getAllLinks().findAll { it.state == IslChangeType.DISCOVERED }.empty
        }
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
        Wrappers.wait(WAIT_OFFSET / 2) { assert northbound.getAllSwitches().empty }

        List<TopologyDefinition> topologies = [topology, topologyPool.topologies].flatten()
        def unblocked = false
        topologies.each { topo ->
            def lab = labService.createLab(topo) //can't create in parallel, hangs on Jenkins. why?
            topo.setLabId(lab.labId)
            TimeUnit.SECONDS.sleep(3) //container with topology needs some time to fully start, this is async
            !unblocked && lockKeeper.removeFloodlightAccessRestrictions(floodlights*.region)
            unblocked = true

            //wait until topology is discovered
            Wrappers.wait(TOPOLOGY_DISCOVERING_TIME) {
                assert islandNorthbound.getAllLinks().findAll {
                    it.state == IslChangeType.DISCOVERED
                }.size() == topo.islsForActiveSwitches.size() * 2
            }
            //wait until switches are activated
            Wrappers.wait(SWITCHES_ACTIVATION_TIME) {
                assert islandNorthbound.getAllSwitches().findAll {
                    it.state == SwitchChangeType.ACTIVATED
                }.size() == topo.activeSwitches.size()
            }

            //add 'vxlan' encapsuation as supported in case switch supports it
            topo.getActiveSwitches().each { sw ->
                def supportedEncapsulation = [FlowEncapsulationType.TRANSIT_VLAN.toString()]
                if (sw.features.contains(SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN)
                        || sw.features.contains(SwitchFeature.KILDA_OVS_PUSH_POP_MATCH_VXLAN)) {
                    supportedEncapsulation << FlowEncapsulationType.VXLAN.toString()
                }
                northbound.updateSwitchProperties(sw.dpId, northbound.getSwitchProperties(sw.dpId).tap {
                    supportedTransitEncapsulation = supportedEncapsulation
                })
            }

            //setup server42 configs according to topology description
            topo.getActiveServer42Switches().each { sw ->
                northbound.updateSwitchProperties(sw.dpId, northbound.getSwitchProperties(sw.dpId).tap {
                    server42FlowRtt = sw.prop.server42FlowRtt
                    server42MacAddress = sw.prop.server42MacAddress
                    server42Port = sw.prop.server42Port
                    server42Vlan = sw.prop.server42Vlan
                    server42IslRtt = (sw.prop.server42IslRtt == null ? "AUTO" : (sw.prop.server42IslRtt ? "ENABLED" : "DISABLED"))
                })
            }
        }
    }
}
