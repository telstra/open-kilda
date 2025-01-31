package org.openkilda.functionaltests.helpers.model

import org.openkilda.model.Switch.SwitchData
import org.openkilda.model.SwitchFeature
import org.openkilda.model.SwitchId
import org.openkilda.model.SwitchStatus

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

import java.time.Instant

@ToString
@EqualsAndHashCode(excludes = "timeModified")
class SwitchDbData {

    SwitchId switchId
    SwitchStatus status
    IpSocketAddress socketAddress
    String hostname
    String controller
    String description
    String version
    String manufacturer
    String hardware
    String software
    String serialNumber
    String dataPath
    Set<SwitchFeature> features
    boolean underMaintenance
    Instant timeCreated
    Instant timeModified
    String pop
    double latitude
    double longitude
    String street
    String city
    String country

    SwitchDbData(SwitchData switchData) {
        this.switchId =  switchData.switchId
        this.status = switchData.status
        this.socketAddress = new IpSocketAddress(switchData.socketAddress.address, switchData.socketAddress.port)
        this.hostname = switchData.hostname
        this.controller = switchData.controller
        this.description = switchData.description
        this.version = switchData.ofVersion
        this.manufacturer = switchData.ofDescriptionManufacturer
        this.hardware = switchData.ofDescriptionHardware
        this.software = switchData.ofDescriptionSoftware
        this.serialNumber = switchData.ofDescriptionSerialNumber
        this.dataPath = switchData.ofDescriptionDatapath
        this.features = switchData.features
        this.underMaintenance = switchData.underMaintenance
        this.timeCreated = switchData.timeCreate
        this.timeModified = switchData.timeModify
        this.pop = switchData.pop
        this.latitude = switchData.latitude
        this.longitude = switchData.longitude
        this.street = switchData.street
        this.city = switchData.city
        this.country = switchData.country
    }

    @ToString
    @EqualsAndHashCode(excludes = "port")
    class IpSocketAddress {
        String address
        int port

        IpSocketAddress(String address, int port) {
            this.address = address
            this.port = port
        }
    }
}
