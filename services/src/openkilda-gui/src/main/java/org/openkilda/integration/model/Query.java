package org.openkilda.integration.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.io.Serializable;
import java.util.List;

/**
 * The Class Query.
 *
 * @author sumitpal.singh
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"rate", "aggregator", "downsample", "metric", "filters"})
public class Query implements Serializable {

    private final static long serialVersionUID = 5051126705056803873L;

    @JsonProperty("rate")
    private boolean rate;

    @JsonProperty("aggregator")
    private String aggregator;

    @JsonProperty("downsample")
    private String downsample;

    @JsonProperty("metric")
    private String metric;

    @JsonProperty("filters")
    private List<Filter> filters = null;


    /**
     * Checks if is rate.
     *
     * @return true, if is rate
     */
    public boolean isRate() {
        return rate;
    }

    /**
     * Sets the rate.
     *
     * @param rate the new rate
     */
    public void setRate(final boolean rate) {
        this.rate = rate;
    }

    /**
     * Gets the aggregator.
     *
     * @return the aggregator
     */
    public String getAggregator() {
        return aggregator;
    }

    /**
     * Sets the aggregator.
     *
     * @param aggregator the new aggregator
     */
    public void setAggregator(final String aggregator) {
        this.aggregator = aggregator;
    }

    /**
     * Gets the downsample.
     *
     * @return the downsample
     */
    public String getDownsample() {
        return downsample;
    }

    /**
     * Sets the downsample.
     *
     * @param downsample the new downsample
     */
    public void setDownsample(final String downsample) {
        this.downsample = downsample;
    }

    /**
     * Gets the metric.
     *
     * @return the metric
     */
    public String getMetric() {
        return metric;
    }

    /**
     * Sets the metric.
     *
     * @param metric the new metric
     */
    public void setMetric(final String metric) {
        this.metric = metric;
    }

    /**
     * Gets the filters.
     *
     * @return the filters
     */
    public List<Filter> getFilters() {
        return filters;
    }

    /**
     * Sets the filters.
     *
     * @param filters the new filters
     */
    public void setFilters(final List<Filter> filters) {
        this.filters = filters;
    }

    @Override
    public String toString() {
        return "Query [rate=" + rate + ", aggregator=" + aggregator + ", downsample=" + downsample
                + ", metric=" + metric + ", filters=" + filters + "]";
    }

}
