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

package org.openkilda.handler.error;

import org.openkilda.constants.IConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


/**
 * The Class MyAccessDeniedHandler.
 */
// handle 403 page
@Component
public class MyAccessDeniedHandler implements AccessDeniedHandler {

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(MyAccessDeniedHandler.class);

    /*
     * (non-Javadoc)
     *
     * @see org.springframework.security.web.access.AccessDeniedHandler#handle(javax
     * .servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse,
     * org.springframework.security.access.AccessDeniedException)
     */
    @Override
    public void handle(final HttpServletRequest httpServletRequest,
            final HttpServletResponse httpServletResponse, final AccessDeniedException e) throws IOException,
            ServletException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null) {
            logger.warn("User '" + auth.getName() + "' attempted to access the protected URL: "
                    + httpServletRequest.getRequestURI());
        }

        httpServletResponse.sendRedirect(httpServletRequest.getContextPath()
                + IConstants.View.ERROR_403);
    }
}
