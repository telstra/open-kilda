package org.openkilda.functionaltests.helpers.model


import org.openkilda.model.SwitchId

import groovy.transform.EqualsAndHashCode
import groovy.transform.TupleConstructor

@TupleConstructor
@EqualsAndHashCode
class SwitchPortVlan {
    SwitchId sw
    Integer port
    Integer vlan

    @Override
    String toString() {
        return "$sw:$port-$vlan"
    }
}
