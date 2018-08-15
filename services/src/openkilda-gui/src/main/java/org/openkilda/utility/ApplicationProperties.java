package org.openkilda.utility;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * ApplicationProperties: is used to read properties from external file. Externalized Configuration
 * is being done with the reference of: https://docs.spring
 * .io/spring-boot/docs/current/reference/html/boot-features-external -config.html
 *
 * @author Gaurav Chugh
 *
 */
@Component
public class ApplicationProperties {

    /** The application properties. */

    @Value("${GET_SWITCHES}")
    private String switches;

    @Value("${GET_SWITCH_RULES}")
    private String switchRules;

    @Value("${GET_LINKS}")
    private String links;

    @Value("${GET_LINK_PROPS}")
    private String linkProps;

    @Value("${nb.base.url}")
    private String nbBaseUrl;

    @Value("${GET_FLOW}")
    private String flows;

    @Value("${GET_PATH_FLOW}")
    private String pathFlow;

    @Value("${GET_FLOW_STATUS}")
    private String flowStatus;

    @Value("${GET_FLOW_REROUTE}")
    private String flowReroute;

    @Value("${GET_FLOW_VALIDATE}")
    private String flowValidate;

    @Value("${opentsdb.base.url}")
    private String openTsdbBaseUrl;

    @Value("${OPEN_TSDB_QUERY}")
    private String openTsdbQuery;

    @Value("${kilda.username}")
    private String kildaUsername;

    @Value("${kilda.password}")
    private String kildaPassword;

    @Value("${switch.data.file.path}")
    private String switchDataFilePath;
    
    @Value("${UPDATE_FLOW}")
    private String updateFlow;
    
    @Value("${GET_FLOW_PATH}")
    private String flowPath;

    public String getSwitches() {
        return switches;
    }

    public String getLinks() {
        return links;
    }

    public String getLinkProps() {
        return linkProps;
    }

    public String getNbBaseUrl() {
        return nbBaseUrl;
    }

    public String getFlows() {
        return flows;
    }

    public String getPathFlow() {
        return pathFlow;
    }

    public String getFlowStatus() {
        return flowStatus;
    }

    public String getOpenTsdbBaseUrl() {
        return openTsdbBaseUrl;
    }

    public String getOpenTsdbQuery() {
        return openTsdbQuery;
    }

    public String getKildaUsername() {
        return kildaUsername;
    }

    public String getKildaPassword() {
        return kildaPassword;
    }

    public String getSwitchDataFilePath() {
        return switchDataFilePath;
    }

    public String getFlowReroute() {
        return flowReroute;
    }

    public String getFlowValidate() {
        return flowValidate;
    }

    public String getSwitchRules() {
        return switchRules;
    }

	public String getUpdateFlow() {
		return updateFlow;
	}

    public String getFlowPath() {
        return flowPath;
    }
}
