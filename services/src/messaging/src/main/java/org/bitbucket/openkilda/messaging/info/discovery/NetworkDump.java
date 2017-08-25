package org.bitbucket.openkilda.messaging.info.discovery;

import static com.google.common.base.MoreObjects.toStringHelper;

import org.bitbucket.openkilda.messaging.info.InfoData;
import org.bitbucket.openkilda.messaging.model.Flow;
import org.bitbucket.openkilda.messaging.model.Isl;
import org.bitbucket.openkilda.messaging.model.Switch;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.Objects;
import java.util.Set;

/**
 * Represents flow northbound response.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "message_type",
        "requester",
        "switches",
        "isls",
        "flows"})
public class NetworkDump extends InfoData {
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
     * Switches.
     */
    @JsonProperty("switches")
    private Set<Switch> switches;

    /**
     * ISLs.
     */
    @JsonProperty("isls")
    private Set<Isl> isls;

    /**
     * Flows.
     */
    @JsonProperty("flows")
    private Set<Flow> flows;

    /**
     * Instance constructor.
     *
     * @param requester requester
     * @param switches  switches
     * @param isls      isls
     * @param flows     flows
     */
    @JsonCreator
    public NetworkDump(@JsonProperty("requester") String requester,
                       @JsonProperty("switches") Set<Switch> switches,
                       @JsonProperty("isls") Set<Isl> isls,
                       @JsonProperty("flows") Set<Flow> flows) {
        this.requester = requester;
        this.switches = switches;
        this.isls = isls;
        this.flows = flows;
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
     * Returns switches.
     *
     * @return switches
     */
    public Set<Switch> getSwitches() {
        return switches;
    }

    /**
     * Sets switches.
     *
     * @param switches switches
     */
    public void setSwitches(Set<Switch> switches) {
        this.switches = switches;
    }

    /**
     * Returns isls.
     *
     * @return isls
     */
    public Set<Isl> getIsls() {
        return isls;
    }

    /**
     * Sets isls.
     *
     * @param isls isls
     */
    public void setIsls(Set<Isl> isls) {
        this.isls = isls;
    }

    /**
     * Returns flows.
     *
     * @return flows
     */
    public Set<Flow> getFlows() {
        return flows;
    }

    /**
     * Sets flows.
     *
     * @param flows flows
     */
    public void setFlows(Set<Flow> flows) {
        this.flows = flows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this)
                .add("requester", requester)
                .add("switches", switches)
                .add("isls", isls)
                .add("flows", flows)
                .toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(requester, switches, isls, flows);
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

        NetworkDump that = (NetworkDump) object;
        return Objects.equals(getRequester(), that.getRequester())
                && Objects.equals(getSwitches(), that.getSwitches())
                && Objects.equals(getIsls(), that.getIsls())
                && Objects.equals(getFlows(), that.getFlows());
    }
}
