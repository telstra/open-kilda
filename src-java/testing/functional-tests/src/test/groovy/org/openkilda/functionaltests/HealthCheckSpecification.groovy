package org.openkilda.functionaltests

import static groovyx.gpars.GParsExecutorsPool.withPool
import static org.openkilda.bluegreen.Signal.SHUTDOWN
import static org.openkilda.bluegreen.Signal.START
import static org.openkilda.model.MeterId.MAX_SYSTEM_RULE_METER_ID
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.bluegreen.Signal
import org.openkilda.functionaltests.exception.IslNotFoundException
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.model.SwitchFeature
import org.openkilda.testing.model.topology.TopologyDefinition.Status
import org.openkilda.testing.tools.SoftAssertions

import groovy.util.logging.Slf4j
import org.apache.zookeeper.ZooKeeper

@Slf4j
class HealthCheckSpecification extends HealthCheckBaseSpecification {

    static Throwable healthCheckError
    static boolean healthCheckRan = false


    def healthCheck() {
        //TODO: https://github.com/telstra/open-kilda/issues/5136
        Closure healthCheckEndpoint = {
            assert northbound.getHealthCheck().components["kafka"] == "operational"
        }
        Closure allZookeeperNodesInExpectedState = {
            def zk = new ZooKeeper(zkConnectString, 5000, {})
            def zkAssertions = new SoftAssertions()
            def activeColor = getActiveNetworkColor(zk)
            def floodlightRegions = flHelper.getFls()*.region
            def pathsToCheck = ["connecteddevices", "floodlightrouter", "flowhs", "isllatency", "nbworker",
                                "network", "flowmonitoring", "opentsdb", "ping", "portstate",
                                "reroute", "server42-control", "stats", "swmanager", "history"].collect { "/${it}/$activeColor" } +
                    floodlightRegions.collect { "/floodlight/$it" }
            withPool {
                pathsToCheck.each {String path ->
                    zkAssertions.checkSucceeds {
                        assert new String(zk.getData("$path/state", null, null)) ==
                                new String(zk.getData("$path/expected_state", null, null)), path
                    }
                }
            }
            zkAssertions.verify()
        }

        Closure allSwitchesAreActive = {
            Wrappers.wait(WAIT_OFFSET) {
                assert northbound.activeSwitches.size() == topology.switches.findAll { it.status != Status.Inactive }.size()
            }
        }

        Closure allLinksAreActive = {
            def links = null
            verifyAll {
                Wrappers.wait(WAIT_OFFSET) {
                    links = northbound.getAllLinks()
                    assert links.findAll { it.state != IslChangeType.DISCOVERED }.empty
                }
                def topoLinks = topology.islsForActiveSwitches.collectMany { isl ->
                    [islUtils.getIslInfo(links, isl).orElseThrow { new IslNotFoundException(isl.toString()) },
                     islUtils.getIslInfo(links, isl.reversed).orElseThrow {
                         new IslNotFoundException(isl.reversed.toString())
                     }]
                }
                def missingLinks = links.findAll { it.state == IslChangeType.DISCOVERED } - topoLinks
                assert missingLinks.empty, "These links are missing in topology.yaml"
            }
        }

        Closure noFlowsLeft = {
            assert northboundV2.allFlows.empty, "There are flows left from previous tests"
        }

        Closure noLinkPropertiesLeft = {
            assert northbound.allLinkProps.empty
        }

        Closure linksBandwidthAndSpeedMatch = {
            def speedBwAssertions = new SoftAssertions()
            def links = northbound.getAllLinks()
            speedBwAssertions.checkSucceeds { assert links.findAll { it.availableBandwidth != it.speed }.empty }
            speedBwAssertions.verify()
        }

        Closure noExcessRulesMeters = {
            def excessRulesAssertions = new SoftAssertions()
            withPool {
                topology.activeSwitches.eachParallel { sw ->
                    def rules = northbound.validateSwitchRules(sw.dpId)
                    excessRulesAssertions.checkSucceeds { assert rules.excessRules.empty, sw }
                    excessRulesAssertions.checkSucceeds { assert rules.missingRules.empty, sw }
                    if (!sw.virtual && sw.ofVersion != "OF_12") {
                        excessRulesAssertions.checkSucceeds {
                            assert northbound.getAllMeters(sw.dpId).meterEntries.findAll {
                                it.meterId > MAX_SYSTEM_RULE_METER_ID
                            }.isEmpty(), "Switch has meters above system max ones"
                        }
                    }
                }
                excessRulesAssertions.verify()
            }
        }

        Closure allSwitchesConnectedToExpectedRegion = {
            def regionVerifications = new SoftAssertions()
            flHelper.fls.forEach { fl ->
                def expectedSwitchIds = topology.activeSwitches.findAll { fl.region in it.regions }*.dpId
                if (!expectedSwitchIds.empty) {
                    regionVerifications.checkSucceeds {
                        assert fl.floodlightService.switches*.switchId.sort() == expectedSwitchIds.sort()
                    }
                }
            }
            regionVerifications.verify()
        }

        Closure featureTogglesInExpectedState = {
            verifyAll(northbound.getFeatureToggles()) {
                flowsRerouteOnIslDiscoveryEnabled
                createFlowEnabled
                updateFlowEnabled
                deleteFlowEnabled
                useBfdForIslIntegrityCheck
                floodlightRoutePeriodicSync
                server42FlowRtt
                server42IslRtt
                //for below props any value allowed. dependent tests will skip themselves or adjust if feature is off
                //flowsRerouteUsingDefaultEncapType
                //collectGrpcStats
            }
        }
        Closure switchesConfigurationIsCorrect = {
            withPool {
                topology.activeSwitches.eachParallel { sw ->
                    verifyAll(northbound.getSwitchProperties(sw.dpId)) {
                        multiTable == useMultitable && sw.features.contains(SwitchFeature.MULTI_TABLE)
                        //server42 props can be either on or off
                    }
                }
            }
        }
        withPool {
            [healthCheckEndpoint,
             allZookeeperNodesInExpectedState,
             allSwitchesAreActive,
             allLinksAreActive,
             noFlowsLeft,
             noLinkPropertiesLeft,
             linksBandwidthAndSpeedMatch,
             noExcessRulesMeters,
             allSwitchesConnectedToExpectedRegion,
             featureTogglesInExpectedState,
             switchesConfigurationIsCorrect].eachParallel { it() }
        }
    }

    String getActiveNetworkColor(ZooKeeper zk) {
        def blue = zk.exists("/network/blue/signal", false)
                ? Signal.valueOf(new String(zk.getData("/network/blue/signal", null, null)))
                : SHUTDOWN
        def green = zk.exists("/network/green/signal", false)
                ? Signal.valueOf(new String(zk.getData("/network/green/signal", null, null)))
                : SHUTDOWN
        assert [blue, green].count { it == START } == 1
        return blue == START ? "blue" : "green"
    }

    boolean getHealthCheckRan() { healthCheckRan }

    Throwable getHealthCheckError() { healthCheckError }

    void setHealthCheckRan(boolean hcRan) { healthCheckRan = hcRan }

    void setHealthCheckError(Throwable t) { healthCheckError = t }

}
