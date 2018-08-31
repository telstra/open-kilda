/* Copyright 2018 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

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
