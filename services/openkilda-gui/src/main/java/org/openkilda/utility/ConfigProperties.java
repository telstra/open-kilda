package org.openkilda.utility;

import java.io.IOException;
import java.util.Properties;

/**
 * Created by pushpdeep.
 */
public class ConfigProperties {

    /** The config properties. */
    private static Properties configProperties;

    static {
        configProperties = new Properties();

        try {
            configProperties.load(ConfigProperties.class.getClassLoader().getResourceAsStream(
                    "application.properties"));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** The Constant SWITCH_DATA_URL. */
    public static final String SWITCH_DATA_URL = configProperties.getProperty("SWITCH_DATA_URL");

    /** The Constant GET_SWITCH_DATA_URL. */
    public static final String GET_SWITCH_DATA_URL = configProperties
            .getProperty("GET_SWITCH_DATA_URL");

    /** The Constant GET_LINK_DATA_URL. */
    public static final String GET_LINK_DATA_URL = configProperties
            .getProperty("GET_LINK_DATA_URL");

    /** The Constant GET_PORT_DATA_URL. */
    public static final String GET_PORT_DATA_URL = configProperties
            .getProperty("GET_PORT_DATA_URL");

    /** The Constant GET_SWITCH_PORT_DATA_URL. */
    public static final String GET_SWITCH_PORT_DATA_URL = configProperties
            .getProperty("GET_SWITCH_PORT_DATA_URL");

    /** The Constant GET_FLOW_DATA_URL. */
    public static final String GET_FLOW_DATA_URL = configProperties
            .getProperty("GET_FLOW_DATA_URL");

    /** The Constant GET_FLOW_STATUS_URL. */
    public static final String GET_FLOW_STATUS_URL = configProperties
            .getProperty("GET_FLOW_STATUS_URL");


}
