package org.openkilda.integration.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.io.Serializable;
import java.util.List;

/**
 * The Class ISLStatsRequestBody.
 *
 * @author sumitpal.singh
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"start", "queries", "end"})
public class IslStats implements Serializable {

    private final static long serialVersionUID = -5664522661231030709L;

    @JsonProperty("start")
    private String start;

    @JsonProperty("queries")
    private List<Query> queries = null;

    @JsonProperty("end")
    private String end;


    /**
     * Gets the start.
     *
     * @return the start
     */
    public String getStart() {
        return start;
    }

    /**
     * Sets the start.
     *
     * @param start the new start
     */
    public void setStart(final String start) {
        this.start = start;
    }

    /**
     * Gets the queries.
     *
     * @return the queries
     */
    public List<Query> getQueries() {
        return queries;
    }

    /**
     * Sets the queries.
     *
     * @param queries the new queries
     */
    public void setQueries(final List<Query> queries) {
        this.queries = queries;
    }

    /**
     * Gets the end.
     *
     * @return the end
     */
    public String getEnd() {
        return end;
    }

    /**
     * Sets the end.
     *
     * @param end the new end
     */
    public void setEnd(final String end) {
        this.end = end;
    }

    @Override
    public String toString() {
        return "ISLStatsRequestBody [start=" + start + ", queries=" + queries + ", end=" + end
                + "]";
    }

}
