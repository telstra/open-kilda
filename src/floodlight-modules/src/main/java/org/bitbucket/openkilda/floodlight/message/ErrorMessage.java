package org.bitbucket.openkilda.floodlight.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.bitbucket.openkilda.floodlight.message.error.ErrorData;
/**
 * Created by jonv on 3/4/17.
 */
public class ErrorMessage extends Message {
    @JsonProperty("data")
    private ErrorData data;

    public ErrorMessage() {

    }

    @JsonProperty("data")
    public ErrorData getData() {
        return data;
    }

    @JsonProperty("data")
    public void setData(ErrorData data) {
        this.data = data;
    }

    public ErrorMessage withData(ErrorData data) {
        setData(data);
        return this;
    }

}
