package org.openkilda.integration.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.openkilda.constants.OpenTsDB;
import org.openkilda.helper.RestClientManager;
import org.openkilda.integration.exception.IntegrationException;
import org.openkilda.integration.exception.InvalidResponseException;
import org.openkilda.integration.model.Filter;
import org.openkilda.integration.model.IslStats;
import org.openkilda.integration.model.Query;
import org.openkilda.utility.ApplicationProperties;
import org.openkilda.utility.IoUtil;
import org.openkilda.utility.JsonUtil;

/**
 * The Class StatsIntegrationService.
 *
 * @author Gaurav Chugh
 */
@Service
public class StatsIntegrationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StatsIntegrationService.class);

    @Autowired
    private RestClientManager restClientManager;

    @Autowired
    private ApplicationProperties applicationProperties;

    /**
     * Gets the stats.
     *
     * @param requestBody the request body
     * @return the stats
     * @throws IntegrationException
     */
    public String getStats(final String startDate, final String endDate, final String metric,
            final Map<String, String[]> params) throws IntegrationException {
        try {
            String requestBody = getRequestBody(startDate, endDate, metric, params);
            LOGGER.info("Inside getStats:  requestBody: " + requestBody);
            HttpResponse response =
                    restClientManager.invoke(applicationProperties.getOpenTsdbQuery(),
                            HttpMethod.POST, requestBody, "", "");
            if (RestClientManager.isValidResponse(response)) {
                String responseEntity = IoUtil.toString(response.getEntity().getContent());
                return responseEntity;
            } else {
                String content = IoUtil.toString(response.getEntity().getContent());
                throw new InvalidResponseException(response.getStatusLine().getStatusCode(), content);
            }
        } catch (Exception ex) {
            LOGGER.error("Inside getStats: Exception: " + ex.getMessage());
            throw new IntegrationException(ex);
        }
    }

    private String getRequestBody(final String startDate, final String endDate, final String metric,
            final Map<String, String[]> params) {
        List<Query> queryList = new ArrayList<Query>();
        Query query = getQuery(metric, params);
        queryList.add(query);

        IslStats islStatsRequestBody = new IslStats();
        islStatsRequestBody.setStart(formatDate(startDate));
        islStatsRequestBody.setEnd(formatDate(endDate));
        islStatsRequestBody.setQueries(queryList);
        return JsonUtil.toString(islStatsRequestBody);
    }

    private String populateFiltersAndReturnDownsample(final List<Filter> filters,
            final Map<String, String[]> params) {
        String downsample = "";
        for (Map.Entry<String, String[]> param : params.entrySet()) {
            if (param.getKey().equalsIgnoreCase("averageOf")) {
                downsample = param.getValue().toString();
            } else if (param.getValue() != null) {
                Filter filter = new Filter();
                filter.setGroupBy(Boolean.valueOf(OpenTsDB.GROUP_BY));
                filter.setType(OpenTsDB.TYPE);
                filter.setTagk(param.getKey());
                filter.setFilter(param.getValue()[0]);
                filters.add(filter);
            }
        }
        return downsample;
    }

    private Query getQuery(final String metric, final Map<String, String[]> params) {
        List<Filter> filters = new ArrayList<Filter>();
        String downsample = "";
        if (params != null) {
            downsample = populateFiltersAndReturnDownsample(filters, params);
        }
        downsample = downsample + "-avg";

        Query query = new Query();
        query.setAggregator(OpenTsDB.AGGREGATOR);
        query.setRate(Boolean.valueOf(OpenTsDB.RATE));
        query.setDownsample(downsample);
        query.setMetric(metric);
        query.setFilters(filters);
        return query;
    }

    /**
     * Sets the date format.
     *
     * @param date the date
     * @return the string
     */
    private String formatDate(final String date) {
        return date.replaceFirst("-", "/");
    }
}
