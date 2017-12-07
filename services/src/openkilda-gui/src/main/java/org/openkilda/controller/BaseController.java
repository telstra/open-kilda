package org.openkilda.controller;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.openkilda.utility.IConstants;
import org.openkilda.web.SessionObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ErrorController;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;


/**
 * BaseController: all the common functionality of web controllers will be lied here. All common
 * requests will be written here
 * 
 * @author Gaurav chugh
 *
 */
public class BaseController implements ErrorController {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(BaseController.class);

    /** The request. */
    @Autowired
    HttpServletRequest request;

    /** The Constant VIEW_ERROR. */
    static final String VIEW_ERROR = "error";

    /** The Constant VIEW_403. */
    static final String VIEW_403 = "403";

    /** The Constant VIEW_LOGIN. */
    static final String VIEW_LOGIN = "login";

    /**
     * Error.
     *
     * @param model the model
     * @return the model and view
     */
    @RequestMapping("/403")
    public ModelAndView error(Model model) {
        return new ModelAndView(VIEW_403);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.springframework.boot.autoconfigure.web.ErrorController#getErrorPath()
     */
    @Override
    @RequestMapping("/error")
    public String getErrorPath() {
        return VIEW_ERROR;
    }

    /**
     * Gets the session.
     *
     * @return the session
     */
    public HttpSession getSession() {
        HttpSession session = request.getSession();
        return session;
    }

    /**
     * Session object.
     *
     * @return the session object
     */
    protected SessionObject getSessionObject() {
        HttpSession session = getSession();
        SessionObject sessionObject = null;

        try {
            sessionObject = (SessionObject) session.getAttribute(IConstants.SESSION_OBJECT);
        } catch (IllegalStateException ise) {
            LOG.info("getSessionObject(). SessionObject had IllegalState, made new");
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
