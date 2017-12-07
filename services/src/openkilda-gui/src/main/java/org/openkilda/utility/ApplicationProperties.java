package org.openkilda.utility;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * ApplicationProperties: is used to read properties from external file.
 * Externalized Configuration is being done with the reference of:
 * https://docs.spring
 * .io/spring-boot/docs/current/reference/html/boot-features-external
 * -config.html
 * 
 * @author Gaurav Chugh
 * 
 */
@Component
public class ApplicationProperties {

	/**
	 * Properties has to synch with application.properties file
	 */

	/** The application properties. */
	@Value("${base.url}")
	private String baseUrl;

	/** The switch base url. */
	@Value("${switch.base.url}")
	private String switchBaseUrl;

	/** The switch base url. */
	@Value("${GET_SWITCHES}")
	private String switches;

	/** The switch base url. */
	@Value("${GET_SWITCH_PORTS}")
	private String switchPorts;

	/** The Port base url. */
	@Value("${port.base.url}")
	private String portBaseUrl;

	/** The Port base url. */
	@Value("${GET_PORTS}")
	private String ports;

	/** The Link base url. */
	@Value("${link.base.url}")
	private String linkBaseUrl;

	/** The links. */
	@Value("${GET_LINKS}")
	private String links;

	/** The flow base url. */
	@Value("${flow.base.url}")
	private String flowBaseUrl;

	/** The flows. */
	@Value("${GET_FLOW}")
	private String flows;

	/** The flow status. */
	@Value("${GET_FLOW_STATUS}")
	private String flowStatus;

	/** The open tsdb base url. */
	@Value("${opentsdb.base.url}")
	private String openTsdbBaseUrl;

	/** The open tsdb query. */
	@Value("${OPEN_TSDB_QUERY}")
	private String openTsdbQuery;

	/** The kilda username. */
	@Value("${kilda.username}")
	private String kildaUsername;

	/** The kilda password. */
	@Value("${kilda.password}")
	private String kildaPassword;

	/**
	 * Gets the base url.
	 *
	 * @return the base url
	 */
	public String getBaseUrl() {
		return baseUrl;
	}

	/**
	 * Gets the switch base url.
	 *
	 * @return the switch base url
	 */
	public String getSwitchBaseUrl() {
		return switchBaseUrl;
	}

	/**
	 * Gets the switches.
	 *
	 * @return the switches
	 */
	public String getSwitches() {
		return switches;
	}

	/**
	 * Gets the switch ports.
	 *
	 * @return the switch ports
	 */
	public String getSwitchPorts() {
		return switchPorts;
	}

	/**
	 * Gets the port base url.
	 *
	 * @return the port base url
	 */
	public String getPortBaseUrl() {
		return portBaseUrl;
	}

	/**
	 * Gets the ports.
	 *
	 * @return the ports
	 */
	public String getPorts() {
		return ports;
	}

	/**
	 * Gets the link base url.
	 *
	 * @return the link base url
	 */
	public String getLinkBaseUrl() {
		return linkBaseUrl;
	}

	/**
	 * Gets the links.
	 *
	 * @return the links
	 */
	public String getLinks() {
		return links;
	}

	/**
	 * Gets the flow base url.
	 *
	 * @return the flow base url
	 */
	public String getFlowBaseUrl() {
		return flowBaseUrl;
	}

	/**
	 * Gets the flows.
	 *
	 * @return the flows
	 */
	public String getFlows() {
		return flows;
	}

	/**
	 * Gets the flow status.
	 *
	 * @return the flow status
	 */
	public String getFlowStatus() {
		return flowStatus;
	}

	/**
	 * Gets the open tsdb base url.
	 *
	 * @return the open tsdb base url
	 */
	public String getOpenTsdbBaseUrl() {
		return openTsdbBaseUrl;
	}

	/**
	 * Gets the open tsdb query.
	 *
	 * @return the open tsdb query
	 */
	public String getOpenTsdbQuery() {
		return openTsdbQuery;
	}

	/**
	 * Gets the kilda username.
	 *
	 * @return the kilda username
	 */
	public String getKildaUsername() {
		return kildaUsername;
	}

	/**
	 * Gets the kilda password.
	 *
	 * @return the kilda password
	 */
	public String getKildaPassword() {
		return kildaPassword;
	}

}
