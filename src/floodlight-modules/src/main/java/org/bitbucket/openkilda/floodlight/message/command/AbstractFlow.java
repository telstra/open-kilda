package org.bitbucket.openkilda.floodlight.message.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Represents general flow info.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "command",
        "destination",
        "cookie",
        "switch_id"
})
public class AbstractFlow extends CommandData {
    /** The flow cookie. It is a mandatory parameter. */
    protected String cookie;
    /** The switch id to manage flow on. It is a mandatory parameter. */
    protected String switchId;

    public AbstractFlow() {}

    /**
     * Constructs an abstract flow installation command.
     *
     * @param cookie      Flow cookie
     * @param switchId    Switch id for flow management
     * @throws IllegalArgumentException if any of mandatory parameters is null
     */
    @JsonCreator
    public AbstractFlow(@JsonProperty("cookie") String cookie,
                        @JsonProperty("switch_id") String switchId) {
        setCookie(cookie);
        setSwitchId(switchId);
    }

    /**
     * Returns the flow cookie.
     *
     * @return the flow cookie
     */
    @JsonProperty("cookie")
    public String getCookie() {
        return cookie;
    }

    /**
     * Returns id of the switch.
     *
     * @return ID of the switch
     */
    @JsonProperty("switch_id")
    public String getSwitchId() {
        return switchId;
    }

    /**
     * Sets the flow cookie.
     *
     * @param cookie of the flow
     */
    @JsonProperty("cookie")
    public void setCookie(String cookie) {
        if (cookie == null || cookie.isEmpty()) {
            throw new IllegalArgumentException("cookie must be set");
        }
        this.cookie = cookie;
    }

    /**
     * Sets id of the switch.
     *
     * @param switchId of the switch
     */
    @JsonProperty("switch_id")
    public void setSwitchId(String switchId) {
        if (switchId == null) {
            throw new IllegalArgumentException("need to set a switch_id");
        } else if (!Utils.checkSwitchId(switchId)) {
            throw new IllegalArgumentException("need to set valid value for switch_id");
        }
        this.switchId = switchId;
    }
}
