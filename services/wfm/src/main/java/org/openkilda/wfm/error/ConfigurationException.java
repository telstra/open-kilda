package org.openkilda.wfm.error;

public class ConfigurationException extends AbstractException {
    public ConfigurationException(String s) {
        super(s);
    }

    public ConfigurationException(String s, Throwable throwable) {
        super(s, throwable);
    }
}
