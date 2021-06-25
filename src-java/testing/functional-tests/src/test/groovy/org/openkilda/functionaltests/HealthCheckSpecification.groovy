package org.openkilda.functionaltests

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
        // expect: "Kilda's health check request is successful"
        assert northbound.getHealthCheck().components["kafka"] == "operational"

        // and: "All zookeeper nodes are in expected state"
        def zk = new ZooKeeper(zkConnectString, 5000, {})
        def activeColor = getActiveNetworkColor(zk)
        def zkAssertions = new SoftAssertions()
        ["connecteddevices", "floodlightrouter", "flowhs", "isllatency", "nbworker", "network", "flowmonitoring",
         "opentsdb", "ping", "portstate", "reroute", "server42-control", "stats", "swmanager"].each { component ->
            def expected = new String(zk.getData("/$component/$activeColor/expected_state", null, null))
            def actual = new String(zk.getData("/$component/$activeColor/state", null, null))
            zkAssertions.checkSucceeds { assert actual == expected, component }
        }
        flHelper.getFls()*.region.each { region ->
            def expected = new String(zk.getData("/floodlight/$region/expected_state", null, null))
            def actual = new String(zk.getData("/floodlight/$region/state", null, null))
            zkAssertions.checkSucceeds { assert actual == expected, region }
        }
        zkAssertions.verify()

        // and: "All switches and links are active. No flows and link props are present"
        def links = null
        verifyAll {
            Wrappers.wait(WAIT_OFFSET) {
                links = northbound.getAllLinks()
                assert northbound.activeSwitches.size() == topology.switches.findAll { it.status != Status.Inactive }.size()
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
            northbound.allFlows.empty
            northbound.allLinkProps.empty
        }

        // and: "Link bandwidths and speeds are equal. No excess and missing switch rules are present"
        def speedBwRulesAssertions = new SoftAssertions()
        speedBwRulesAssertions.checkSucceeds { assert links.findAll { it.availableBandwidth != it.speed }.empty }
        topology.activeSwitches.each { sw ->
            def rules = northbound.validateSwitchRules(sw.dpId)
            speedBwRulesAssertions.checkSucceeds { assert rules.excessRules.empty, sw }
            speedBwRulesAssertions.checkSucceeds { assert rules.missingRules.empty, sw }
        }
        speedBwRulesAssertions.checkSucceeds {
            assert topology.activeSwitches.findAll {
                !it.virtual && it.ofVersion != "OF_12" && !northbound.getAllMeters(it.dpId).meterEntries.findAll {
                    it.meterId > MAX_SYSTEM_RULE_METER_ID
                }.isEmpty()
            }.empty
        }
        speedBwRulesAssertions.verify()

        // and: "Every switch is connected to the expected region"
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

        // and: "Feature toggles are in expected state"
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

        // and: "Switches configurations are in expected state"
        topology.activeSwitches.each { sw ->
            verifyAll(northbound.getSwitchProperties(sw.dpId)) {
                multiTable == useMultitable && sw.features.contains(SwitchFeature.MULTI_TABLE)
                //server42 props can be either on or off
            }
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
