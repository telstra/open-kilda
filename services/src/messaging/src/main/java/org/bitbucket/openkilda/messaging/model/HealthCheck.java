/* Copyright 2017 Telstra Open Source
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

package org.bitbucket.openkilda.messaging.model;

import static com.google.common.base.MoreObjects.toStringHelper;

import org.bitbucket.openkilda.messaging.Utils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

/**
 * Health-Check model representation class.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HealthCheck implements Serializable {
    /**
     * The constant serialVersionUID.
     */
    private static final long serialVersionUID = 1L;

    /**
     * The service name.
     */
    @JsonProperty("name")
    private String name;

    /**
     * The service version.
     */
    @JsonProperty("version")
    private String version;

    /**
     * The service description.
     */
    @JsonProperty("description")
    private String description;

    /**
     * Components status.
     */
    @JsonProperty("components")
    private Map<String, String> components;

    /**
     * Constructs the health-check model.
     */
    public HealthCheck() {
    }

    /**
     * Constructs the health-check model.
     *
     * @param name        service name
     * @param version     service version
     * @param description service description
     * @param components  components status
     */
    @JsonCreator
    public HealthCheck(@JsonProperty("name") String name,
                       @JsonProperty("version") String version,
                       @JsonProperty("description") String description,
                       @JsonProperty("components") Map<String, String> components) {
        setName(name);
        setServiceVersion(version);
        setDescription(description);
        setComponents(components);
    }

    /**
     * Gets service name.
     *
     * @return service name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets service name.
     *
     * @param name service name
     */
    public void setName(final String name) {
        this.name = name;
    }

    /**
     * Gets service version.
     *
     * @return service version
     */
    public String getVersion() {
        return version;
    }

    /**
     * Sets service version.
     *
     * @param version service version
     */
    public void setServiceVersion(final String version) {
        this.version = version;
    }

    /**
     * Gets service description.
     *
     * @return service description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets service version.
     *
     * @param description service version
     */
    public void setDescription(final String description) {
        this.description = description;
    }

    /**
     * Get components statuses.
     *
     * @return components statuses
     */
    public Map<String, String> getComponents() {
        return components;
    }

    /**
     * Sets components statuses.
     *
     * @param components components statuses
     */
    public void setComponents(Map<String, String> components) {
        this.components = components;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this)
                .add("name", name)
                .add("version", version)
                .add("description", description)
                .add("components", components)
                .toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || !(obj instanceof HealthCheck)) {
            return false;
        }

        HealthCheck that = (HealthCheck) obj;
        return Objects.equals(this.getName(), that.getName())
                && Objects.equals(this.getVersion(), that.getVersion())
                && Objects.equals(this.getDescription(), that.getDescription())
                && Objects.equals(this.getComponents(), that.getComponents());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(name, version, description, components);
    }

    /**
     * Checks topologies status.
     *
     * @return true in case of any non operational topologies
     */
    public boolean hasNonOperational() {
        return components.values().stream().anyMatch(Utils.HEALTH_CHECK_NON_OPERATIONAL_STATUS::equals);
    }
}
