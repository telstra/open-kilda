package org.openkilda.northbound.service;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * LinkPropsResult encapsulates the response from a call to set, or delete, link properties.
 * These calls work against the static link_props table, but are also propagated to any underlying
 * links. Only if the links exist (ie an ISL discovery has happened) will the properties be
 * propgated during the call to set / delete. Otherwise, the properties will propagate after an
 * ISL is dicovered.
 */
@JsonSerialize
public class LinkPropsResult {
    private int failures;
    private int successes;
    private String[] messages;

    public LinkPropsResult(int failures, int successes, String[] messages) {
        this.failures = failures;
        this.successes = successes;
        if (messages != null)
            this.messages = messages;
        else
            this.messages = new String[0];
    }

    public int getFailures() {
        return failures;
    }

    public int getSuccesses() {
        return successes;
    }

    public String[] getMessages() {
        return messages;
    }

}
