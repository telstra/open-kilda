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
        "message_type",
        "switch_id",
        "state"})
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
     * @param   switchId  switch id
     * @param   state     switch state
     */
    @JsonCreator
    public SwitchInfoData(@JsonProperty("switch_id") final String switchId,
                          @JsonProperty("state") final SwitchEventType state) {
        this.switchId = switchId;
        this.state = state;
    }

    /**
     * Returns switch id.
     *
     * @return  switch id
     */
    @JsonProperty("switch_id")
    public String getSwitchId() {
        return switchId;
    }

    /**
     * Sets switch id.
     *
     * @param   switchId  switch id to set
     */
    @JsonProperty("switch_id")
    public void setSwitchId(final String switchId) {
        this.switchId = switchId;
    }

    /**
     * Returns switch state.
     *
     * @return  switch state
     */
    @JsonProperty("state")
    public SwitchEventType getState() {
        return state;
    }

    /**
     * Sets switch state.
     *
     * @param   state  switch state to set
     */
    @JsonProperty("state")
    public void setState(final SwitchEventType state) {
        this.state = state;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this)
                .add("switch_id", switchId)
                .add("state", state)
                .toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(switchId, state);
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
                && Objects.equals(getState(), that.getState());
    }
}
