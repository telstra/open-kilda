package org.bitbucket.openkilda.messaging.command.flow;

import static org.bitbucket.openkilda.messaging.Utils.TRANSACTION_ID;

import org.bitbucket.openkilda.messaging.Utils;
import org.bitbucket.openkilda.messaging.command.CommandData;

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
        TRANSACTION_ID,
        "id",
        "cookie",
        "switch_id"})
public class BaseFlow extends CommandData {
    /**
     * Transaction id.
     */
    @JsonProperty(TRANSACTION_ID)
    protected Long transactionId;

    /**
     * The flow id.
     */
    @JsonProperty("id")
    protected String id;

    /**
     * Cookie allocated for flow.
     */
    @JsonProperty("cookie")
    protected Long cookie;

    /**
     * The switch id to manage flow on. It is a mandatory parameter.
     */
    @JsonProperty("switch_id")
    protected String switchId;

    /**
     * Instance constructor.
     *
     * @param transactionId transaction id
     * @param id            flow id
     * @param cookie        cookie of the flow
     * @param switchId      switch id for flow management
     * @throws IllegalArgumentException if any of mandatory parameters is null
     */
    @JsonCreator
    public BaseFlow(@JsonProperty(TRANSACTION_ID) final Long transactionId,
                    @JsonProperty("id") final String id,
                    @JsonProperty("cookie") final Long cookie,
                    @JsonProperty("switch_id") final String switchId) {
        setTransactionId(transactionId);
        setId(id);
        setCookie(cookie);
        setSwitchId(switchId);
    }

    /**
     * Returns transaction id of the request.
     *
     * @return transaction id of the request
     */
    public Long getTransactionId() {
        return transactionId;
    }

    /**
     * Sets transaction id of the request.
     *
     * @param transactionId transaction id of the request
     */
    public void setTransactionId(final Long transactionId) {
        this.transactionId = transactionId;
    }

    /**
     * Returns the flow id.
     *
     * @return the flow id
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the flow id.
     *
     * @param id flow id
     */
    public void setId(final String id) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("flow id must be set");
        }
        this.id = id;
    }

    /**
     * Returns cookie of the flow.
     *
     * @return cookie of the flow
     */
    public Long getCookie() {
        return cookie;
    }

    /**
     * Sets cookie for the flow.
     *
     * @param cookie for the flow
     */
    public void setCookie(final Long cookie) {
        this.cookie = cookie;
    }

    /**
     * Returns id of the switch.
     *
     * @return ID of the switch
     */
    public String getSwitchId() {
        return switchId;
    }

    /**
     * Sets id of the switch.
     *
     * @param switchId of the switch
     */
    public void setSwitchId(String switchId) {
        if (switchId == null) {
            throw new IllegalArgumentException("need to set a switch_id");
        } else if (!Utils.validateSwitchId(switchId)) {
            throw new IllegalArgumentException("need to set valid value for switch_id");
        }
        this.switchId = switchId;
    }
}
