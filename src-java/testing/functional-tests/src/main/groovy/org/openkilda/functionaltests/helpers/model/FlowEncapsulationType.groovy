package org.openkilda.functionaltests.helpers.model

enum FlowEncapsulationType {

    TRANSIT_VLAN("transit_vlan"),
    VXLAN("vxlan")

    private final String encapsulationType

    FlowEncapsulationType(final String encapsulationType) {
        this.encapsulationType = encapsulationType
    }


    String getEncapsulationType() {
        return encapsulationType
    }

    @Override
    String toString() {
        return encapsulationType
    }

    static FlowEncapsulationType getByValue(String encapsulationType) {
        for (FlowEncapsulationType enType : values()) {
            if (enType.encapsulationType == encapsulationType) {
                return enType
            }
        }
        throw new IllegalArgumentException("Invalid value for encapsulation type")
    }
}
