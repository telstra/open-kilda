package org.openkilda.service.impl;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.http.HttpResponse;
import org.apache.log4j.Logger;
import org.openkilda.helper.RestClientManager;
import org.openkilda.service.StatsService;
import org.openkilda.utility.ApplicationProperties;
import org.openkilda.utility.IoUtils;
import org.openkilda.utility.StatsUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

/**
 * The Class StatsServiceImpl.
 * 
 * @author Gaurav Chugh
 */
@Service
public class StatsServiceImpl implements StatsService {

	/** The Constant logger. */
	static final Logger log = Logger.getLogger(StatsServiceImpl.class);

	/** The stats util. */
	@Autowired
	private StatsUtil statsUtil;

	/** The application properties. */
	@Autowired
	private ApplicationProperties applicationProperties;

	/** The rest client manager. */
	@Autowired
	private RestClientManager restClientManager;

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.openkilda.service.StatsService#getStats(java.lang.String,
	 * java.lang.String, java.lang.String,
	 * javax.servlet.http.HttpServletRequest)
	 */
	@Override
	public Object getStats(String startDate, String endDate, String metric,
			HttpServletRequest request) {

		log.info("Inside getStats");
		String responseEntity = "";
		Map<String, String> query_pairs = statsUtil.getQuery_pairs(request);
		String requestBody = statsUtil.getRequestBody(startDate, endDate,
				metric, query_pairs);

		if (log.isInfoEnabled())
			log.info("Inside getStats: : startDate: " + startDate
					+ ": endDate: " + endDate + ": metric: " + metric);

		long startTime = System.currentTimeMillis();
		try {
			if (log.isInfoEnabled())
				log.info("Inside getStats:  requestBody: " + requestBody);
			HttpResponse httpResponse = restClientManager.invoke(
					applicationProperties.getOpenTsdbQuery(), HttpMethod.POST,
					requestBody, "", "");
			if (RestClientManager.isValidResponse(httpResponse)) {
				responseEntity = IoUtils.getData(httpResponse.getEntity()
						.getContent());
			}
		} catch (Exception ex) {
			log.error("Inside getStats: Exception: " + ex.getMessage());
		}

		if (log.isInfoEnabled())
			log.info("Inside getStats: : Method Execution Time in Milliseconds is:"
					+ (System.currentTimeMillis() - startTime));

		return responseEntity;
	}

}
