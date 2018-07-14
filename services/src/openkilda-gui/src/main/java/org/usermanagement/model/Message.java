package org.usermanagement.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"message"})
public class Message {

    @JsonProperty("message")
    private String msg;

    private String subject;

    public Message(final String msg) {
        super();
        this.msg = msg;
    }

    public Message(final String msg, final String subject) {
        super();
        this.msg = msg;
        this.subject = subject;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(final String subject) {
        this.subject = subject;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(final String msg) {
        this.msg = msg;
    }

    @Override
    public String toString() {
        return "[msg=" + msg + ", subject=" + subject + "]";
    }



}
