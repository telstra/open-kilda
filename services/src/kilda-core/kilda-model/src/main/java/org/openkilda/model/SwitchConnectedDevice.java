/* Copyright 2019 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.model;

import static java.lang.String.format;
import static org.neo4j.ogm.annotation.Relationship.INCOMING;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.Index;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.Relationship;
import org.neo4j.ogm.annotation.typeconversion.Convert;
import org.neo4j.ogm.typeconversion.InstantStringConverter;

import java.io.Serializable;
import java.time.Instant;

/**
 * Represents a flow connected device.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(exclude = {"entityId", "switchObj"})
@NodeEntity(label = "switch_connected_device")
@ToString(exclude = {"switchObj"})
public class SwitchConnectedDevice implements Serializable {
    private static final long serialVersionUID = 4191175994460517407L;

    // Hidden as needed for OGM only.
    @Id
    @GeneratedValue
    @Setter(AccessLevel.NONE)
    @Getter(AccessLevel.NONE)
    private Long entityId;

    @NonNull
    @Relationship(type = "has", direction = INCOMING)
    private Switch switchObj;

    @Index
    @Property("port_number")
    private int portNumber;

    @Index
    @Property("vlan")
    private int vlan;

    @Property("flow_id")
    private String flowId;

    @Property("source")
    private Boolean source;

    @Index
    @NonNull
    @Property("mac_address")
    private String macAddress;

    @Index
    @NonNull
    @Property(name = "type")
    @Convert(graphPropertyType = String.class)
    private ConnectedDeviceType type;

    @Index
    @NonNull
    @Property("chassis_id")
    private String chassisId;

    @Index
    @NonNull
    @Property("port_id")
    private String portId;

    @Property("ttl")
    private Integer ttl;

    @Property("port_description")
    private String portDescription;

    @Property("system_name")
    private String systemName;

    @Property("system_description")
    private String systemDescription;

    @Property("system_capabilities")
    private String systemCapabilities;

    @Property("management_address")
    private String managementAddress;

    @Property(name = "time_first_seen")
    @Convert(InstantStringConverter.class)
    private Instant timeFirstSeen;

    @Property(name = "time_last_seen")
    @Convert(InstantStringConverter.class)
    private Instant timeLastSeen;

    // Setter hidden as used to imitate unique composite index for non-enterprise Neo4j versions.
    // Getter is used for tests
    @Setter(AccessLevel.NONE)
    @Property(name = "unique_index")
    @Index(unique = true)
    private String uniqueIndex;

    @Builder(toBuilder = true)
    public SwitchConnectedDevice(@NonNull Switch switchObj, int portNumber, int vlan, String flowId, Boolean source,
                                 @NonNull String macAddress, @NonNull ConnectedDeviceType type,
                                 @NonNull String chassisId, @NonNull String portId, Integer ttl, String portDescription,
                                 String systemName, String systemDescription, String systemCapabilities,
                                 String managementAddress, Instant timeFirstSeen, Instant timeLastSeen) {
        this.switchObj = switchObj;
        this.portNumber = portNumber;
        this.vlan = vlan;
        this.flowId = flowId;
        this.source = source;
        this.macAddress = macAddress;
        this.type = type;
        this.chassisId = chassisId;
        this.portId = portId;
        this.ttl = ttl;
        this.portDescription = portDescription;
        this.systemName = systemName;
        this.systemDescription = systemDescription;
        this.systemCapabilities = systemCapabilities;
        this.managementAddress = managementAddress;
        this.timeFirstSeen = timeFirstSeen;
        this.timeLastSeen = timeLastSeen;
        calculateUniqueIndex();
    }

    public void setSwitch(@NonNull Switch switchObj) {
        this.switchObj = switchObj;
        calculateUniqueIndex();
    }

    public void setPortNumber(int portNumber) {
        this.portNumber = portNumber;
        calculateUniqueIndex();
    }

    public void setMacAddress(@NonNull String macAddress) {
        this.macAddress = macAddress;
        calculateUniqueIndex();
    }

    public void setVlan(int vlan) {
        this.vlan = vlan;
        calculateUniqueIndex();
    }

    public void setType(@NonNull ConnectedDeviceType type) {
        this.type = type;
        calculateUniqueIndex();
    }

    public void setChassisId(@NonNull String chassisId) {
        this.chassisId = chassisId;
        calculateUniqueIndex();
    }

    public void setPortId(@NonNull String portId) {
        this.portId = portId;
        calculateUniqueIndex();
    }

    private void calculateUniqueIndex() {
        uniqueIndex = format("%s_%s_%s_%s_%s_%s_%s",
                switchObj.getSwitchId(), portNumber, vlan, macAddress, type, chassisId, portId);
    }
}
