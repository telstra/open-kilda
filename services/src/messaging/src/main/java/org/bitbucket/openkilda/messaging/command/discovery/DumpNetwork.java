package org.bitbucket.openkilda.messaging.command.discovery;

import static com.google.common.base.MoreObjects.toStringHelper;

import org.bitbucket.openkilda.messaging.command.CommandData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.Objects;

/**
 * Defines the payload payload of a Message representing an command for network dump.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "command",
        "requester"})
public class DumpNetwork extends CommandData {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Requester.
     */
    @JsonProperty("requester")
    protected String requester;

    /**
     * Default constructor.
     */
    public DumpNetwork() {
    }

    /**
     * Instance constructor.
     *
     * @param requester requester
     */
    @JsonCreator
    public DumpNetwork(@JsonProperty("requester") String requester) {
        this.requester = requester;
    }

    /**
     * Returns requester.
     *
     * @return requester
     */
    public String getRequester() {
        return requester;
    }

    /**
     * Sets requester.
     *
     * @param requester requester
     */
    public void setRequester(String requester) {
        this.requester = requester;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this)
                .add("requester", requester)
                .toString();
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

        DumpNetwork that = (DumpNetwork) object;
        return Objects.equals(getRequester(), that.getRequester());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(requester);
    }
}
