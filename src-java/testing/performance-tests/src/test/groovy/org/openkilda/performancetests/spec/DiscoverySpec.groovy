package org.openkilda.performancetests.spec

import static org.hamcrest.CoreMatchers.equalTo
import static org.openkilda.testing.service.floodlight.model.FloodlightConnectMode.RW
import static org.openkilda.testing.service.lockkeeper.LockKeeperVirtualImpl.DUMMY_CONTROLLER

import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.Wrappers.WaitTimeoutException
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.SwitchChangeType
import org.openkilda.performancetests.BaseSpecification
import org.openkilda.performancetests.model.CustomTopology

import groovy.util.logging.Slf4j
import org.junit.Assume
import org.springframework.beans.factory.annotation.Value
import spock.lang.Unroll

@Slf4j
class DiscoverySpec extends BaseSpecification {

    @Value("#{'\${floodlight.regions}'.split(',')}")
    List<String> regions

    @Unroll
    def "System is able to discover a huge topology at once#debugText"() {
        Assume.assumeThat(preset.debug, equalTo(debug))
        def islsAmount = preset.switchesAmount * 2

        setup: "Prepare potential topology"
        def topo = new CustomTopology()
        preset.switchesAmount.times {
            def fls = flHelper.fls.findAll { it.mode == RW }
            def fl = fls[it % fls.size()]
            topo.addCasualSwitch(fl.openflow, [fl.region])
        }
        islsAmount.times {
            def src = topo.pickRandomSwitch()
            def dst = topo.pickRandomSwitch([src])
            topo.addIsl(src, dst)
        }

        when: "Create the topology"
        def lab = labService.createLab(topo)

        then: "Topology is discovered in reasonable time"
        Wrappers.wait(preset.switchesAmount * 3, 5) {
            topoHelper.verifyTopology(topo)
        }

        cleanup: "purge topology"
        topo && topoHelper.purgeTopology(topo, lab)

        where:
        preset << [
                //around 55 switches for local 32GB setup and ~110 switches for stage
                [
                        debug         : true,
                        switchesAmount: 30
                ],
                [
                        debug         : false,
                        switchesAmount: 60
                ]
        ]
        debugText = preset.debug ? " (debug mode)" : ""
    }

    /**
     * Push the system to its limits until it fails to discover new isls or switches. Measure system's capabilities
     */
    @Unroll
    def "System is able to continuously discover new switches and ISLs#debugText"() {
        Assume.assumeThat(preset.debug, equalTo(debug))

        //unattainable amount that system won't be able to handle for sure
        def switchesAmount = preset.minimumSwitchesRequirement * 1.5
        def islsAmount = switchesAmount * 3
        def allowedDiscoveryTime = 60 //seconds

        setup: "Create topology not connected to controller"
        def topo = new CustomTopology()
        switchesAmount.times { topo.addCasualSwitch(DUMMY_CONTROLLER, [regions[it % regions.size()]]) }
        islsAmount.times {
            def src = topo.pickRandomSwitch()
            def dst = topo.pickRandomSwitch([src])
            topo.addIsl(src, dst)
        }
        def lab = labService.createLab(topo)
        sleep(5000) //TODO(rtretiak): make createLab request to be synchronous

        when: "Start connecting switches to Kilda controller one by one"
        def switchesCreated = 0
        topo.switches.eachWithIndex { sw, i ->
            log.debug("Adding sw #${switchesCreated + 1} with id $sw.dpId")
            def fls = flHelper.fls.findAll { it.mode == RW }
            lockKeeper.setController(sw, fls[i % fls.size()].openflow)
            Wrappers.wait(allowedDiscoveryTime) {
                assert northbound.getSwitch(sw.dpId).state == SwitchChangeType.ACTIVATED
                Wrappers.timedLoop(3) { //verify that system remains stable for some time
                    assert northbound.getAllSwitches().findAll {
                        it.switchId in topo.switches*.dpId && it.state != SwitchChangeType.ACTIVATED
                    }.empty
                    assert northbound.getAllLinks().findAll { it.state != IslChangeType.DISCOVERED }.empty
                    sleep(200)
                }
            }
            switchesCreated++
        }

        then: "Amount of discovered switches within allowed time is acceptable"
        def waitFailure = thrown(WaitTimeoutException)
        log.info("Performance report: Kilda was able to discover $switchesCreated switches.\nFailed with $waitFailure")
        switchesCreated > preset.minimumSwitchesRequirement

        cleanup: "purge topology"
        topo && topoHelper.purgeTopology(topo, lab)

        where:
        preset << [
                //around 55 switches for local 32GB setup and ~110 switches for stage
                [
                        debug                     : true,
                        minimumSwitchesRequirement: 50,
                        allowedDiscoveryTime      : 60
                ],
                [
                        debug                     : false,
                        minimumSwitchesRequirement: 100,
                        allowedDiscoveryTime      : 60
                ]
        ]
        debugText = preset.debug ? " (debug mode)" : ""
    }
}
