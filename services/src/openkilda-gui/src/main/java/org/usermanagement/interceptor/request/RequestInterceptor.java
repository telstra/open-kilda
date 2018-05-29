package org.usermanagement.interceptor.request;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.openkilda.constants.IConstants;
import org.usermanagement.context.RequestContext;
import org.usermanagement.model.UserInfo;


@Component
public class RequestInterceptor extends HandlerInterceptorAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestInterceptor.class);

    @Autowired
    private RequestContext requestContext;

    @Override
    public boolean preHandle(final HttpServletRequest request, final HttpServletResponse response, final Object handler)
            throws Exception {
        HttpSession session = request.getSession();
        UserInfo userInfo = null;
        try {
            userInfo = (UserInfo) session.getAttribute(IConstants.SESSION_OBJECT);
        } catch (IllegalStateException ex) {
            LOGGER.info("[getLoggedInUser] Exception while retrieving user information from session. Exception: "
                    + ex.getLocalizedMessage(), ex);
        }
        requestContext.setUserInfo(userInfo);

        return true;
    }

}
