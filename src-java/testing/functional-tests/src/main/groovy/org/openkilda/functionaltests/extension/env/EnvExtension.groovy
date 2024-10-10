package org.openkilda.functionaltests.extension.env

import static groovyx.gpars.GParsExecutorsPool.withPool
import static org.openkilda.testing.Constants.SWITCHES_ACTIVATION_TIME
import static org.openkilda.testing.Constants.TOPOLOGY_DISCOVERING_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.openkilda.testing.Constants.HEALTH_CHECK_TIME

import org.openkilda.functionaltests.exception.IslNotFoundException
import org.openkilda.functionaltests.extension.spring.SpringContextListener
import org.openkilda.functionaltests.extension.spring.SpringContextNotifier
import org.openkilda.functionaltests.helpers.SwitchHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.info.event.SwitchChangeType
import org.openkilda.messaging.model.system.FeatureTogglesDto
import org.openkilda.model.FlowEncapsulationType
import org.openkilda.model.SwitchFeature
import org.openkilda.northbound.dto.v1.links.LinkParametersDto
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Status
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.floodlight.model.Floodlight
import org.openkilda.testing.service.labservice.LabService
import org.openkilda.testing.service.lockkeeper.LockKeeperService
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.northbound.NorthboundServiceV2
import org.openkilda.testing.tools.IslUtils
import org.openkilda.testing.tools.SoftAssertions
import org.openkilda.testing.tools.TopologyPool

import groovy.util.logging.Slf4j
import org.spockframework.runtime.extension.AbstractGlobalExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationContext
import spock.lang.Shared

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

    @Autowired
    LabService labService

    @Autowired
    LockKeeperService lockKeeper

    @Autowired
    List<Floodlight> commonFloodlights

    @Autowired @Shared
    IslUtils islUtils

    @Autowired
    Database database

    @Value('${spring.profiles.active}')
    String profile

    @Value('${discovery.timeout}')
    int discoveryTimeout

    @Value('${rebuild.topology_lab:true}')
    boolean isTopologyRebuildRequired

    @Value('${health_check.verifier:true}')
    boolean enable

    @Override
    void start() {
        SpringContextNotifier.addListener(this)
    }

    @Override
    void notifyContextInitialized(ApplicationContext applicationContext) {
        applicationContext.autowireCapableBeanFactory.autowireBean(this)
        if (profile == "virtual") {
            isTopologyRebuildRequired ? buildVirtualEnvironment() : topology.setLabId(labService.getLabs().first().labId)
            isTopologyRebuildRequired ?: collectSwitchesPathsFromDb(topology)
            log.info("Virtual topology is successfully created")
        } else if (profile == "hardware") {
            labService.createHwLab(topology)
            log.info("Successfully redirected to hardware topology")
        } else {
            throw new RuntimeException("Provided profile '$profile' is unknown. Select one of the following profiles:" +
                    " hardware, virtual")
        }
        isHealthCheckRequired(profile, enable, isTopologyRebuildRequired) && verifyTopologyReadiness(topology)
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
                .createHaFlowEnabled(true)
                .modifyHaFlowEnabled(true)
                .deleteHaFlowEnabled(true)
                .syncSwitchOnConnect(true)
                .build()
        northbound.toggleFeature(features)
        log.info("Deleting all flows")
        northboundV2.getAllYFlows().each { northboundV2.deleteYFlow(it.getYFlowId()) }
        northboundV2.getAllHaFlows().each { northboundV2.deleteHaFlow(it.getHaFlowId()) }
        northbound.deleteAllFlows()
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getAllFlows().empty
            assert northboundV2.getAllHaFlows().empty
        }
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
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getAllLinks().empty
        }
        log.info("Deleting all switches")
        northbound.getAllSwitches().each { northbound.deleteSwitch(it.switchId, false) }
        Wrappers.wait(WAIT_OFFSET / 2) { assert northbound.getAllSwitches().empty }

        List<TopologyDefinition> topologies = [topology, topologyPool.topologies].flatten()
        log.info("Creating topologies (${topologies.size()})")
        def unblocked = false
        topologies.each { topo ->
            def lab = labService.createLab(topo) //can't create in parallel, hangs on Jenkins. why?
            topo.setLabId(lab.labId)
            TimeUnit.SECONDS.sleep(3) //container with topology needs some time to fully start, this is async
            !unblocked && lockKeeper.removeFloodlightAccessRestrictions(commonFloodlights*.region)
            unblocked = true

            //wait until topology is discovered
            Wrappers.wait(TOPOLOGY_DISCOVERING_TIME) {
                assert northbound.getAllLinks().findAll {
                    it.source.switchId in topo.switches.dpId && it.state == IslChangeType.DISCOVERED
                }.size() == topo.islsForActiveSwitches.size() * 2
            }
            //wait until switches are activated
            Wrappers.wait(SWITCHES_ACTIVATION_TIME) {
                assert northbound.getAllSwitches().findAll {
                    it.switchId in topo.switches.dpId && it.state == SwitchChangeType.ACTIVATED
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
            verifyTopologyReadiness(topo)
            collectSwitchesPathsFromDb(topo)
        }
    }

    Closure allSwitchesAreActive = { TopologyDefinition topologyDefinition ->
        assert northbound.activeSwitches.switchId.containsAll(topologyDefinition.switches.findAll { it.status != Status.Inactive }.dpId)
    }

    Closure allLinksAreActive = { TopologyDefinition topologyDefinition ->
        def links = northbound.getAllLinks().findAll { it.source.switchId in topologyDefinition.activeSwitches.dpId }
        assert links.findAll { it.state != IslChangeType.DISCOVERED }.empty

        def topoLinks = topologyDefinition.islsForActiveSwitches.collectMany { isl ->
            [islUtils.getIslInfo(links, isl).orElseThrow { new IslNotFoundException(isl.toString()) },
             islUtils.getIslInfo(links, isl.reversed).orElseThrow {
                 new IslNotFoundException(isl.reversed.toString())
             }]
        }
        def missingLinks = links.findAll { it.state == IslChangeType.DISCOVERED } - topoLinks
        assert missingLinks.empty, "These links are missing in topology.yaml"
    }


    Closure noFlowsLeft = { TopologyDefinition topologyDefinition ->
        assert northboundV2.allFlows.findAll { it.source.switchId in topologyDefinition.activeSwitches.dpId }.empty, "There are flows left from previous tests"
    }

    Closure noLinkPropertiesLeft = { TopologyDefinition topologyDefinition ->
        assert northbound.getAllLinkProps().findAll { it.srcSwitch in topologyDefinition.activeSwitches.dpId.collect { it.toString() } }.empty
    }

    Closure linksBandwidthAndSpeedMatch = { TopologyDefinition topologyDefinition ->
        def speedBwAssertions = new SoftAssertions()
        def links = northbound.getAllLinks().findAll { it.source.switchId in topologyDefinition.activeSwitches.dpId }
        speedBwAssertions.checkSucceeds { assert links.findAll { it.availableBandwidth != it.speed }.empty }
        speedBwAssertions.verify()
    }

    Closure noExcessRulesMeters = { TopologyDefinition topologyDefinition ->
        switchHelper.synchronizeAndCollectFixedDiscrepancies(topologyDefinition.activeSwitches*.getDpId())
    }

    Closure allSwitchesConnectedToExpectedRegion = { TopologyDefinition topologyDefinition ->
        def regionVerifications = new SoftAssertions()
        commonFloodlights.forEach { fl ->
            def expectedSwitchIds = topologyDefinition.activeSwitches.findAll { fl.region in it.regions }*.dpId
            if (!expectedSwitchIds.empty) {
                regionVerifications.checkSucceeds {
                    assert fl.floodlightService.switches.switchId.containsAll(expectedSwitchIds)
                }
            }
        }
        regionVerifications.verify()
    }

    Closure switchesConfigurationIsCorrect = { TopologyDefinition topologyDefinition ->
        def switchConfigVerification = new SoftAssertions()
        withPool {
            topologyDefinition.activeSwitches.eachParallel { sw ->
                switchConfigVerification.checkSucceeds {
                    //server42 props can be either on or off
                    assert northbound.getSwitchProperties(sw.dpId).multiTable
                }
            }
        }
        switchConfigVerification.verify()
    }

    boolean isHealthCheckRequired(String profile, Boolean enable, Boolean isTopologyRebuildRequired) {
        return (profile == "hardware" && enable) || (profile == "virtual" && !isTopologyRebuildRequired && enable)
    }

    void verifyTopologyReadiness(TopologyDefinition topologyDefinition) {
            log.info("Starting topology-related HC: lab_id=" + topologyDefinition.labId)
            Wrappers.wait(HEALTH_CHECK_TIME) {
                withPool {
                    [allSwitchesAreActive,
                     allLinksAreActive,
                     profile == "hardware" || !isTopologyRebuildRequired ? noFlowsLeft : null,
                     profile == "hardware" || !isTopologyRebuildRequired ? noLinkPropertiesLeft: null,
                     linksBandwidthAndSpeedMatch,
                     noExcessRulesMeters,
                     allSwitchesConnectedToExpectedRegion,
                     switchesConfigurationIsCorrect].findAll().eachParallel { it(topologyDefinition) }
                }
            }
            log.info("Topology-related HC passed: lab_id=" + topologyDefinition.labId)
    }

    def collectSwitchesPathsFromDb(TopologyDefinition topo) {
        def switchesPaths = [topo.switches.dpId, topo.switches.dpId].combinations()
                .findAll { src, dst -> src != dst } //non-single-switch
                .unique { it.sort() }//no reversed versions of same flows
                .collectEntries{  src, dst ->
                    def paths = database.getPaths(src, dst)*.path
                    [(src.toString() + "-" + dst.toString()):  paths]}
                as HashMap<String, List<List<PathNode>>>

        topo.setSwitchesDbPathsNodes(switchesPaths)
    }
}
