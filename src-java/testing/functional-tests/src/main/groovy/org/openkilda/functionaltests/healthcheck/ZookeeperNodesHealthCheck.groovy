package org.openkilda.functionaltests.healthcheck

import org.apache.zookeeper.ZooKeeper
import org.openkilda.bluegreen.Signal
import org.openkilda.testing.service.floodlight.FloodlightsHelper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import spock.lang.Shared

import static groovyx.gpars.GParsExecutorsPool.withPool
import static org.openkilda.bluegreen.Signal.SHUTDOWN
import static org.openkilda.bluegreen.Signal.START
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

@Component("zookeeperHealthCheck")
@Scope(SCOPE_PROTOTYPE)

class ZookeeperNodesHealthCheck implements HealthCheck {
    @Value('${zookeeper.connect_string}')
    @Shared
    String zkConnectString
    @Autowired
    @Shared
    FloodlightsHelper flHelper

    @Override
    List<HealthCheckException> getPotentialProblems() {
        def zk = new ZooKeeper(zkConnectString, 5000, {})
        def activeColor = getActiveNetworkColor(zk)
        def floodlightRegions = flHelper.getFls()*.region
        def pathsToCheck = ["connecteddevices", "floodlightrouter", "flowhs", "isllatency", "nbworker",
                            "network", "flowmonitoring", "opentsdb", "ping", "portstate",
                            "reroute", "server42-control", "stats", "swmanager", "history"].collect { "/${it}/$activeColor" } +
                floodlightRegions.collect { "/floodlight/$it" }

        return withPool {
            pathsToCheck.collectParallel { String path ->
                def expectedState = new String(zk.getData("$path/expected_state", null, null))
                def actualState = new String(zk.getData("$path/state", null, null))
                return actualState == expectedState ? null : new HealthCheckException(
                        "Component ${path} is in unexpected state in Zookeeper:" +
                                " exptected to be ${expectedState}, but was ${actualState}", path)
            }
        }.findAll { it != null }
    }

    private static String getActiveNetworkColor(ZooKeeper zk) {
        def blue = zk.exists("/network/blue/signal", false)
                ? Signal.valueOf(new String(zk.getData("/network/blue/signal", null, null)))
                : SHUTDOWN
        def green = zk.exists("/network/green/signal", false)
                ? Signal.valueOf(new String(zk.getData("/network/green/signal", null, null)))
                : SHUTDOWN
        assert [blue, green].count { it == START } == 1
        return blue == START ? "blue" : "green"
    }

}
