package org.openkilda.wfm.error;

public class ConfigurationException extends Exception {
    public ConfigurationException(String s) {
        super(s);
    }

    public ConfigurationException(String s, Throwable throwable) {
        super(s, throwable);
    }
}
