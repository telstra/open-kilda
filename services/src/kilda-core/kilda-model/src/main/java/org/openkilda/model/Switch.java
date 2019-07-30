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

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.Index;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.typeconversion.Convert;
import org.neo4j.ogm.typeconversion.InstantStringConverter;

import java.io.Serializable;
import java.time.Instant;
import java.util.regex.Pattern;

/**
 * Represents a switch.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(exclude = {"entityId"})
@NodeEntity(label = "switch")
public class Switch implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final Pattern NOVIFLOW_SOFTWARE_REGEX = Pattern.compile("(.*)NW\\d{3}\\.\\d+\\.\\d+(.*)");
    private static final Pattern E_SWITCH_HARDWARE_DESCRIPTION_REGEX = Pattern.compile("^WB5\\d{3}-E$");
    private static final String E_SWITCH_MANUFACTURER_DESCRIPTION = "E";

    // Hidden as needed for OGM only.
    @Id
    @GeneratedValue
    @Setter(AccessLevel.NONE)
    private Long entityId;

    @NonNull
    @Property(name = "name")
    @Convert(graphPropertyType = String.class)
    @Index(unique = true)
    private SwitchId switchId;

    @NonNull
    @Property(name = "state")
    // Enforce usage of custom converters.
    @Convert(graphPropertyType = String.class)
    private SwitchStatus status;

    private String address;

    private String hostname;

    private String controller;

    private String description;

    private String ofVersion;

    private String ofDescriptionManufacturer;
    private String ofDescriptionHardware;
    private String ofDescriptionSoftware;
    private String ofDescriptionSerialNumber;
    private String ofDescriptionDatapath;

    @Property(name = "under_maintenance")
    private boolean underMaintenance;

    @Property(name = "time_create")
    @Convert(InstantStringConverter.class)
    private Instant timeCreate;

    @Property(name = "time_modify")
    @Convert(InstantStringConverter.class)
    private Instant timeModify;

    @Builder(toBuilder = true)
    public Switch(@NonNull SwitchId switchId, SwitchStatus status, String address,
                  String hostname, String controller, String description, boolean underMaintenance,
                  Instant timeCreate, Instant timeModify) {
        this.switchId = switchId;
        this.status = status;
        this.address = address;
        this.hostname = hostname;
        this.controller = controller;
        this.description = description;
        this.underMaintenance = underMaintenance;
        this.timeCreate = timeCreate;
        this.timeModify = timeModify;
    }

    /**
     * Checks Centec switch by the manufacturer description.
     */
    public static boolean isCentecSwitch(String manufacturerDescription) {
        return StringUtils.contains(manufacturerDescription.toLowerCase(), "centec");
    }

    /**
     * Checks Noviflow switch by the software description.
     */
    public static boolean isNoviflowSwitch(String softwareDescription) {
        return NOVIFLOW_SOFTWARE_REGEX.matcher(softwareDescription).matches();
    }

    /**
     * Checks Noviflow E switch by the manufacturer and hardware description.
     */
    public static boolean isNoviflowESwitch(String manufacturerDescription, String hardwareDescription) {
        return E_SWITCH_MANUFACTURER_DESCRIPTION.equalsIgnoreCase(manufacturerDescription)
                || hardwareDescription != null
                && E_SWITCH_HARDWARE_DESCRIPTION_REGEX.matcher(hardwareDescription).matches();
    }
}
