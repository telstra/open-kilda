package org.openkilda.wfm;

import org.openkilda.wfm.error.ConfigurationException;

import java.util.Properties;

public class PropertiesReader {
    private Properties payload;
    private String prefixes[];

    public PropertiesReader(Properties payload) {
        this(payload, "");
    }

    public PropertiesReader(Properties payload, String ...prefixes) {
        this.payload = payload;
        this.prefixes = new String[prefixes.length];

        int idx;
        for (idx = 0; idx < prefixes.length; idx++) {
            String value = prefixes[idx];
            if (! value.equals("")) {
                value += ".";
            }
            this.prefixes[idx] = value;
        }
    }

    public String getString(String key) throws ConfigurationException {
        return getKey(key);
    }

    public Integer getInteger(String key) throws ConfigurationException {
        return new Integer(getKey(key));
    }

    public Float getFloat(String key) throws ConfigurationException {
        return new Float(getKey(key));
    }

    public Boolean getBoolean(String key) throws ConfigurationException {
        return Boolean.valueOf(getKey(key));
    }

    private String getKey(String key) throws ConfigurationException {
        String value = null;

        for (String head : prefixes) {
            value = payload.getProperty(head + key);
            if (value != null) {
                break;
            }
        }

        if (value == null) {
            throw new ConfigurationException(String.format("There is no property %s", key));
        }

        return value;
    }
}
