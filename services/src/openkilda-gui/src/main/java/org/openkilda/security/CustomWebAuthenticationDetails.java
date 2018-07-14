package org.openkilda.security;

import org.springframework.security.web.authentication.WebAuthenticationDetails;

import javax.servlet.http.HttpServletRequest;

public class CustomWebAuthenticationDetails extends WebAuthenticationDetails {

    private static final long serialVersionUID = 6662611705477130485L;

    private String verificationCode;
    private String configure2Fa;

    public CustomWebAuthenticationDetails(final HttpServletRequest request) {
        super(request);
        verificationCode = request.getParameter("code");
        configure2Fa = request.getParameter("configure2Fa");
    }

    public String getVerificationCode() {
        return verificationCode;
    }

    public boolean isConfigure2Fa() {
        return configure2Fa != null  && configure2Fa.equalsIgnoreCase("TRUE") ? true : false;
    }
}
