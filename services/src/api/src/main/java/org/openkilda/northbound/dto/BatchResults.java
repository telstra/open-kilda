package org.openkilda.northbound.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * BatchResults encapsulates the response from a batch process - ie multiple operations.
 *
 * NB: This was copied from LinkPropsResult with no real changes .. drop LinkPropResults.
 */
@JsonSerialize
public class BatchResults {
    private int failures;
    private int successes;
    private String[] messages;

    public BatchResults() {
        this(0,0,null);
    }

    public BatchResults(int failures, int successes, String[] messages) {
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
