package org.openkilda.performancetests.model

import org.openkilda.model.SwitchId
import org.openkilda.testing.model.topology.TopologyDefinition

import com.fasterxml.jackson.annotation.JsonIgnore
import groovy.transform.InheritConstructors

@InheritConstructors
class CustomTopology extends TopologyDefinition {
    @JsonIgnore
    def r = new Random()

    CustomTopology() {
        super([], [], [], new TraffGenConfig("172.16.80.0", 20))
    }

    /**
     * Add a 'default' switch to this topology. Switch id and name are generated randomly
     *
     * @param controller Which controller the new switch should be connected to
     * @return the added switch
     */
    Switch addCasualSwitch(String controller) {
        def swId = new SwitchId(r.nextLong())
        def sw = Switch.factory("sw${switches.size() + 1}", swId, "OF_13", Status.Active,
                null, null, controller)
        switches << sw
        return sw
    }

    Isl addIsl(Switch src, Switch dst) {
        def srcPorts = getAllowedPortsForSwitch(src)
        def dstPorts = getAllowedPortsForSwitch(dst)
        assert srcPorts, "Not enough free ports on switch $src.dpId"
        assert dstPorts, "Not enough free ports on switch $dst.dpId"
        def isl = Isl.factory(src, srcPorts[0], dst, dstPorts[0], 1000000L, null)
        isls << isl
        return isl
    }

    /**
     * Pick a random switch from this topology.
     *
     * @param exclude Switches to exclude from search when choosing random switch
     * @return random switch from topology
     */
    Switch pickRandomSwitch(List<Switch> exclude = []) {
        def swSelection = (switches - exclude)
        assert !swSelection.empty, "No switches to choose from"
        return swSelection[r.nextInt(swSelection.size())]
    }
}
