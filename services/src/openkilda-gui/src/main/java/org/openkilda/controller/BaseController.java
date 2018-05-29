package org.openkilda.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.autoconfigure.web.ErrorController;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.openkilda.constants.IConstants;
import org.usermanagement.model.UserInfo;

public abstract class BaseController implements ErrorController {

    private static final Logger LOGGER = LoggerFactory.getLogger(BaseController.class);

    /**
     * Validate request.
     * <ul>
     * <li>If user is logged in and role is user then redirected to Home.</li>
     * <li>If user is logged in and role is not user then redirected to view passed as an argument.
     * </li>
     * <li>If user is not logged in then redirected to login page.</li>
     * </ul>
     *
     * @param request HttpServletRequest to check user log in status.
     * @param viewName on which user has to redirect if logged in and not of type user.
     * @return ModelAndView information containing view name on which user is going to be
     * redirected.
     */
    public ModelAndView validateAndRedirect(final HttpServletRequest request,
            final String viewName) {
        LOGGER.info("[validateAndRedirect] - start. Requested view name: " + viewName);
        ModelAndView modelAndView;
        if (isUserLoggedIn()) {
            UserInfo userInfo = getLoggedInUser(request);
            LOGGER.info("[validateAndRedirect] Logged in user. User name: " + userInfo.getName()
                    + ", Roles: " + userInfo.getRoles());

            modelAndView = new ModelAndView(viewName);
        } else {
            LOGGER.info("[validateAndRedirect] User in not logged in, redirected to login page");
            modelAndView = new ModelAndView(IConstants.View.LOGIN);
        }
        return modelAndView;
    }

    /**
     * Error.
     *
     * @param model the model
     * @return the model and view
     */
    @RequestMapping("/403")
    public ModelAndView error(final Model model) {
        return new ModelAndView(IConstants.View.ERROR_403);
    }

    /*
     * (non-Javadoc)
     *
     * @see org.springframework.boot.autoconfigure.web.ErrorController#getErrorPath()
     */
    @Override
    @RequestMapping("/error")
    public String getErrorPath() {
        return IConstants.View.ERROR;
    }

    /**
     * Return logged in user information.
     *
     * @param request HttpServletRequest to retrieve logged in user information.
     * @return logged in user information.
     */
    protected UserInfo getLoggedInUser(final HttpServletRequest request) {
        LOGGER.info("[getLoggedInUser] - start");
        HttpSession session = request.getSession();
        UserInfo userInfo = null;
        try {
            userInfo = (UserInfo) session.getAttribute(IConstants.SESSION_OBJECT);
        } catch (IllegalStateException ex) {
            LOGGER.info(
                    "[getLoggedInUser] Exception while retrieving user information from session. Exception: "
                            + ex.getLocalizedMessage(),
                    ex);
        } finally {
            if (userInfo == null) {
                session = request.getSession(false);
                userInfo = new UserInfo();
                session.setAttribute(IConstants.SESSION_OBJECT, userInfo);
            }
        }
        return userInfo;
    }


    /**
     * Returns true if user is logged in, false otherwise.
     *
     * @return true, if is user logged in
     */
    protected static boolean isUserLoggedIn() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (null != authentication) {
            return (authentication.isAuthenticated()
                    && !(authentication instanceof AnonymousAuthenticationToken));
        } else {
            return false;
        }
    }
}
