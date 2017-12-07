package org.openkilda.config;

import javax.servlet.Filter;


import org.openkilda.security.filter.LoggingFilter;
import org.springframework.context.annotation.Bean;

/**
 * The Class FilterConfig.
 */
public class FilterConfig {

	/**
	 * Logging filter.
	 *
	 * @return the filter
	 */
	@Bean
	public Filter loggingFilter() {
		return new LoggingFilter();
	}
}
