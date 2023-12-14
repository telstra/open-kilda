package org.openkilda.functionaltests

import static groovyx.gpars.GParsExecutorsPool.withPool
import static org.openkilda.bluegreen.Signal.SHUTDOWN
import static org.openkilda.bluegreen.Signal.START

import org.openkilda.bluegreen.Signal
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.testing.tools.SoftAssertions

import groovy.util.logging.Slf4j
import org.apache.zookeeper.ZooKeeper
import org.springframework.beans.factory.annotation.Value

@Slf4j
class HealthCheckSpecification extends HealthCheckBaseSpecification {

    private static final int WAIT_FOR_LAB_TO_BE_OPERATIONAL = 210 //sec
    static Throwable healthCheckError
    static boolean healthCheckRan = false
    @Value('${health_check.verifier:true}')
    boolean enable


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

        if(enable) {
            log.info("Starting basic Health Check before specs execution")
            Wrappers.wait(WAIT_FOR_LAB_TO_BE_OPERATIONAL) {
                withPool {
                    [healthCheckEndpoint,
                     allZookeeperNodesInExpectedState,
                     featureTogglesInExpectedState].eachParallel { it() }
                }
            }
            log.info("Basic Health Check before specs execution passed.")
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
