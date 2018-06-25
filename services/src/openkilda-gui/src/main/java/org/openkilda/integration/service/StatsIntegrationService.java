package org.openkilda.integration.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpResponse;
import org.openkilda.constants.IConstants.Metrics;
import org.openkilda.constants.OpenTsDB;
import org.openkilda.constants.OpenTsDB.StatsType;
import org.openkilda.helper.RestClientManager;
import org.openkilda.integration.exception.IntegrationException;
import org.openkilda.integration.model.Filter;
import org.openkilda.integration.model.IslStats;
import org.openkilda.integration.model.Query;
import org.openkilda.utility.ApplicationProperties;
import org.openkilda.utility.IoUtil;
import org.openkilda.utility.JsonUtil;
import org.openkilda.utility.StringUtil;

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

    public String getStats(final String startDate, final String endDate, final String downsample,
            final String switchId, final String port, final String flowId, final String srcSwitch, final String srcPort, final String dstSwitch, final String dstPort, final StatsType statsType, final String metric)
            throws IntegrationException {

        LOGGER.info("Inside getStats: switchId: " + switchId);
        try {
            String payload = getOpenTsdbRequestBody(startDate, endDate, downsample, switchId, port,
                    flowId, srcSwitch, srcPort, dstSwitch, dstPort, statsType, metric);

            LOGGER.info("Inside getStats: startDate: " + startDate + ": endDate: "
                    + endDate + ": payload: " + payload);

            HttpResponse response =
                    restClientManager.invoke(applicationProperties.getOpenTsdbQuery(),
                            HttpMethod.POST, payload, "application/json", "");
            if (RestClientManager.isValidResponse(response)) {
                return IoUtil.toString(response.getEntity().getContent());
            }
        } catch (IOException ex) {
            LOGGER.error("Inside getStats Exception is: " + ex.getMessage());
            throw new IntegrationException(ex);
        }
        return null;
    }

    private String populateFiltersAndReturnDownsample(final List<Filter> filters,
            final Map<String, String[]> params,final Integer index) {
        String downsample = "";
        if (params != null) {
            for (Map.Entry<String, String[]> param : params.entrySet()) {
                if (param.getKey().equalsIgnoreCase("averageOf")) {
                    downsample = param.getValue().toString();
                } else if (param.getValue() != null) {
                    Filter filter = new Filter();
                    filter.setGroupBy(Boolean.valueOf(OpenTsDB.GROUP_BY));
                    filter.setType(OpenTsDB.TYPE);
                    filter.setTagk(param.getKey());
                    if(index == 0 && param.getKey().equals("direction")) {
                        filter.setFilter("forward");
                    } else if(index == 1 && param.getKey().equals("direction")) {
                        filter.setFilter("reverse");
                    } else {
                        filter.setFilter(param.getValue()[0]);
                    }
                    filters.add(filter);
                }
            }
        }
        return downsample;
    }

    private Query getQuery(final String downsample, final String metric,
            final Map<String, String[]> params, final Integer index,final StatsType statsType) {
        List<Filter> filters = new ArrayList<Filter>();
        String paramDownSample = "";
        if (params != null) {
        		paramDownSample = populateFiltersAndReturnDownsample(filters, params, index);
        }

        if (!StringUtil.isNullOrEmpty(downsample)) {
            paramDownSample = downsample + "-avg";
        } else if (!StringUtil.isNullOrEmpty(paramDownSample)) {
            paramDownSample = paramDownSample + "-avg";
        }
        Query query = new Query();
        query.setAggregator(OpenTsDB.AGGREGATOR);
        if (!statsType.equals(StatsType.ISL)) {
            query.setRate(Boolean.valueOf(OpenTsDB.RATE));
        }
        if(validateDownSample(paramDownSample, query)) {
            query.setDownsample(paramDownSample);
        }
        query.setMetric(metric);
        query.setFilters(filters);
        return query;
    }

	private boolean validateDownSample(String paramDownSample, final Query query) {
		boolean isValidDownsample = false;
		paramDownSample = paramDownSample.replaceFirst("^0+(?!$)", "");
        if(Character.isDigit(paramDownSample.charAt(0))) {
        	String[] downSampleArr = paramDownSample.split("-");
        	if(downSampleArr != null && downSampleArr.length > 0){
        		String dwnSample = downSampleArr[0];
        		 Pattern pattern = Pattern.compile("[msh]");
        	     Matcher matcher = pattern.matcher(dwnSample);
        	     if (matcher.find()) {
                    isValidDownsample = true;
                }
        	}
        }
        return isValidDownsample;
	}

    /**
     * Sets the date format.
     *
     * @param date the date
     * @return the string
     */
    private String formatDate(final String date) {
        return date.replaceFirst("-", "/").replaceFirst("-", "/");
    }

    private String getOpenTsdbRequestBody(final String startDate, final String endDate,
            final String downsample, final String switchId, final String port, final String flowId,
            final String srcSwitch,final String srcPort, final String dstSwitch, final String dstPort, final StatsType statsType, final String metric) throws JsonProcessingException {
        LOGGER.info("Inside getOpenTsdbRequestBody :");
        Map<String, String[]> params = getParam(statsType, switchId, port, flowId, srcSwitch, srcPort, dstSwitch, dstPort);
        List<Query> queries = new ArrayList<Query>();
        List<String> metricList = new ArrayList<String>();
        if(statsType.equals(StatsType.PORT)) {
            metricList = Metrics.switchValue(metric);
        } else if(statsType.equals(StatsType.FLOW)) {
            metricList = Metrics.flowValue(metric);
        } else if(statsType.equals(StatsType.ISL)) {
            metricList = Metrics.switchValue(metric);
        }
        if(metricList != null && !metricList.isEmpty()){
        	for(int index = 0; index < metricList.size(); index++){
        		String metricName = metricList.get(index);
        		queries.add(getQuery(downsample, metricName, params, index,statsType));
        	}
        }

        return getRequest(startDate, endDate, queries);
    }

    private String getRequest(final String startDate, final String endDate,
            final List<Query> queryList) throws JsonProcessingException {
        IslStats islStatsRequest = new IslStats();
        islStatsRequest.setStart(formatDate(startDate));
        islStatsRequest.setEnd(formatDate(endDate));
        islStatsRequest.setQueries(queryList);
        return JsonUtil.toString(islStatsRequest);
    }

    private Map<String, String[]> getParam(final StatsType statsType, final String switchId,
            final String port, final String flowId, final String srcSwitch, final String srcPort, final String dstSwitch, final String dstPort) {
        Map<String, String[]> params = new HashMap<String, String[]>();

        if (statsType.equals(StatsType.SWITCH)) {
            params.put("switchId", new String[] {switchId});
        } else if (statsType.equals(StatsType.PORT)) {
            params.put("switchid", new String[] {switchId});
            params.put("port", new String[] {port});
        } else if (statsType.equals(StatsType.FLOW)) {
            params.put("flowid", new String[] {flowId});
            params.put("direction", new String[] {});
        } else if(statsType.equals(StatsType.ISL)){
        	params.put("src_switch", new String[] { srcSwitch });
			params.put("src_port", new String[] { srcPort });
			params.put("dst_switch", new String[] { dstSwitch });
			params.put("dst_port", new String[] { dstPort });
        }
        return params;
    }

}
