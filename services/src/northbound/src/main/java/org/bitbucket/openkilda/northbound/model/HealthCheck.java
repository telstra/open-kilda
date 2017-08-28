package org.bitbucket.openkilda.northbound.model;

import static com.google.common.base.MoreObjects.toStringHelper;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.Serializable;
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
     */
    @JsonCreator
    public HealthCheck(final String name, final String version, final String description) {
        setName(name);
        setServiceVersion(version);
        setDescription(description);
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
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this)
                .add("name", name)
                .add("version", version)
                .add("description", description)
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
                && Objects.equals(this.getDescription(), that.getDescription());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(name, version, description);
    }
}
