package org.openkilda.functionaltests.model.switches


import org.openkilda.testing.model.topology.TopologyDefinition.Switch

enum Manufacturer {
    OVS("nicira"),
    CENTEC("centec"),
    NOVIFLOW("noviflow"),
    WB5164("WB5164")

    final String descriptionPart

    Manufacturer(String descriptionPart) {
        this.descriptionPart = descriptionPart
    }

    boolean isSwitchMatch(Switch aSwitch) {
        return aSwitch.nbFormat().hardware =~ descriptionPart ||
                aSwitch.nbFormat().manufacturer.toLowerCase().contains(descriptionPart)
    }
}
