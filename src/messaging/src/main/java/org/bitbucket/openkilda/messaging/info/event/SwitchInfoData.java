package org.bitbucket.openkilda.messaging.info.event;

import static com.google.common.base.Objects.toStringHelper;

import org.bitbucket.openkilda.messaging.info.InfoData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.Objects;

/**
 * Defines the payload payload of a Message representing a switch info.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "info",
        "switch_id",
        "state",
        "address",
        "name",
        "description"})
public class SwitchInfoData extends InfoData {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Switch id.
     */
    @JsonProperty("switch_id")
    private String switchId;

    /**
     * Switch ip address.
     */
    @JsonProperty("address")
    private String address;

    /**
     * Switch name.
     */
    @JsonProperty("name")
    private String name;

    /**
     * Switch description.
     */
    @JsonProperty("description")
    private String description;

    /**
     * Switch state.
     */
    @JsonProperty("state")
    private SwitchEventType state;

    /**
     * Default constructor.
     */
    public SwitchInfoData() {
    }

    /**
     * Instance constructor.
     *
     * @param switchId    switch datapath id
     * @param state       switch state
     * @param address     switch ip address
     * @param name        switch name
     * @param description switch description
     */
    @JsonCreator
    public SwitchInfoData(@JsonProperty("switch_id") final String switchId,
                          @JsonProperty("state") final SwitchEventType state,
                          @JsonProperty("address") final String address,
                          @JsonProperty("name") final String name,
                          @JsonProperty("description") final String description) {
        this.switchId = switchId;
        this.state = state;
        this.address = address;
        this.name = name;
        this.description = description;
    }

    /**
     * Returns switch id.
     *
     * @return switch id
     */
    public String getSwitchId() {
        return switchId;
    }

    /**
     * Sets switch id.
     *
     * @param switchId switch id to set
     */
    public void setSwitchId(final String switchId) {
        this.switchId = switchId;
    }

    /**
     * Returns switch state.
     *
     * @return switch state
     */
    public SwitchEventType getState() {
        return state;
    }

    /**
     * Sets switch state.
     *
     * @param state switch state to set
     */
    public void setState(final SwitchEventType state) {
        this.state = state;
    }

    /**
     * Gets switch ip address.
     *
     * @return switch ip address
     */
    public String getAddress() {
        return address;
    }

    /**
     * Sets switch ip address.
     *
     * @param address switch ip address
     */
    public void setAddress(String address) {
        this.address = address;
    }

    /**
     * Gets switch name.
     *
     * @return switch name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets switch name.
     *
     * @param name switch name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets switch description.
     *
     * @return switch description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets switch description.
     *
     * @param description description
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this)
                .add("switch_id", switchId)
                .add("state", state)
                .add("address", address)
                .add("name", name)
                .add("description", description)
                .toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(switchId, state, address, name, description);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }

        SwitchInfoData that = (SwitchInfoData) object;
        return Objects.equals(getSwitchId(), that.getSwitchId())
                && Objects.equals(getState(), that.getState())
                && Objects.equals(getAddress(), that.getAddress())
                && Objects.equals(getName(), that.getName())
                && Objects.equals(getDescription(), that.getDescription());
    }
}
