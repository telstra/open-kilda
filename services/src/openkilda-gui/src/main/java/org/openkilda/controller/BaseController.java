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
import org.openkilda.web.SessionObject;


/**
 * BaseController: all the common functionality of web controllers will be lied here. All common
 * requests will be written here
 *
 * @author Gaurav chugh
 *
 */
public class BaseController implements ErrorController {

    /** The Constant LOG. */
    private static final Logger LOGGER = LoggerFactory.getLogger(BaseController.class);


    public ModelAndView validateAndRedirect(final HttpServletRequest request, final String viewName) {
        ModelAndView modelAndView;
        if (isUserLoggedIn()) {
            SessionObject sessionObject = getSessionObject(request);

            if (sessionObject.getRole().equalsIgnoreCase(IConstants.Role.USER)) {
                modelAndView = new ModelAndView(IConstants.View.REDIRECT_HOME);
            } else {
                modelAndView = new ModelAndView(viewName);
            }
        } else {
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
     * Session object.
     *
     * @return the session object
     */
    protected SessionObject getSessionObject(final HttpServletRequest request) {
        HttpSession session = request.getSession();
        SessionObject sessionObject = null;

        try {
            sessionObject = (SessionObject) session.getAttribute(IConstants.SESSION_OBJECT);
        } catch (IllegalStateException ise) {
            LOGGER.info("getSessionObject(). SessionObject had IllegalState, made new");
        } finally {
            if (sessionObject == null) {
                session = request.getSession(false);
                sessionObject = new SessionObject();
                session.setAttribute(IConstants.SESSION_OBJECT, sessionObject);
            }
        }
        return sessionObject;
    }


    /**
     * Checks if is user logged in.
     *
     * @return true, if is user logged in
     */
    protected static boolean isUserLoggedIn() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (null != authentication) {
            return (authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken));
        } else {
            return false;
        }
    }
}
