package org.openkilda.performancetests.spec

import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.performancetests.BaseSpecification
import org.openkilda.performancetests.model.CustomTopology

class DiscoverySpec extends BaseSpecification {

    def "System is able to discover a huge topology"() {
        setup: "Prepare potential topology"
        def switchesAmount = 150
        def islsAmount = switchesAmount * 2
        def topo = new CustomTopology()
        switchesAmount.times { topo.addCasualSwitch(controllerHosts[0]) }
        islsAmount.times {
            def src = topo.pickRandomSwitch()
            def dst = topo.pickRandomSwitch([src])
            topo.addIsl(src, dst)
        }
        topo.setControllers(controllerHosts)

        when: "Create the topology"
        def lab = labService.createLab(topo)

        then: "Topology is discovered in reasonable time"
        Wrappers.wait(180, 5) {
            topoHelper.verifyTopology(topo)
        }

        and: "Cleanup: purge topology"
        topoHelper.purgeTopology(topo, lab)
    }
}
