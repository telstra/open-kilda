package org.bitbucket.openkilda.northbound.model;

import static com.google.common.base.MoreObjects.toStringHelper;

import org.bitbucket.openkilda.messaging.Utils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
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
    private String name;

    /**
     * The service version.
     */
    private String version;

    /**
     * The service description.
     */
    private String description;

    /**
     * Components status.
     */
    private Map<String, String> status;

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
     * @param status      topologies status
     */
    @JsonCreator
    public HealthCheck(String name, String version, String description, Map<String, String> status) {
        setName(name);
        setServiceVersion(version);
        setDescription(description);
        setStatus(status);
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
    public Map<String, String> getStatus() {
        return status;
    }

    /**
     * Sets components statuses.
     *
     * @param status components statuses
     */
    public void setStatus(Map<String, String> status) {
        this.status = status;
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
                .add("topologies-status", status)
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
                && Objects.equals(this.getStatus(), that.getStatus());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(name, version, description, status);
    }

    /**
     * Checks topologies status.
     *
     * @return true in case of any non operational topologies
     */
    public boolean hasNonOperational() {
        return status.values().stream().anyMatch(Utils.HEALTH_CHECK_NON_OPERATIONAL_STATUS::equals);
    }
}
