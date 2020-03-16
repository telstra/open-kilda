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

package org.openkilda.controller;

import org.openkilda.constants.IConstants;
import org.openkilda.constants.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ErrorController;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import org.usermanagement.dao.entity.UserEntity;
import org.usermanagement.dao.repository.UserRepository;
import org.usermanagement.model.UserInfo;
import org.usermanagement.util.MessageUtils;

import java.nio.file.AccessDeniedException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

public abstract class BaseController implements ErrorController {

    private static final Logger LOGGER = LoggerFactory.getLogger(BaseController.class);

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private MessageUtils messageUtils;

    /**
     * Validate request.
     * <ul>
     * <li>If user is logged in and role is user then redirected to Home.</li>
     * <li>If user is logged in and role is not user then redirected to view
     * passed as an argument.</li>
     * <li>If user is not logged in then redirected to login page.</li>
     * </ul>
     *
     * @param request HttpServletRequest to check user log in status.
     * @param viewName on which user has to redirect if logged in and not of type user.
     * @return ModelAndView information containing view name on which user is going to be redirected.
     */
    public ModelAndView validateAndRedirect(final HttpServletRequest request, final String viewName) {
        ModelAndView modelAndView;
        if (isUserLoggedIn()) {
            UserInfo userInfo = getLoggedInUser(request);
            LOGGER.info("Logged in user. view name: " + viewName + ", User name: "
                    + userInfo.getName());

            modelAndView = new ModelAndView(IConstants.View.REDIRECT_HOME);
        } else {
            LOGGER.warn("User in not logged in, redirected to login page. Requested view name: "
                    + viewName);
            modelAndView = new ModelAndView("login");
        }
        return modelAndView;
    }

    /**
     * Error.
     *
     * @param model the model
     * @throws AccessDeniedException the access denied exception
     */
    @RequestMapping("/401")
    public ModelAndView error(final Model model, HttpServletRequest request) throws AccessDeniedException {
        String referrer = request.getHeader("referer");
        ModelAndView modelAndView = new ModelAndView();
        if (StringUtils.isEmpty(referrer) || referrer.contains("/login")) {
            modelAndView.setViewName("redirect:/");
            return modelAndView;
        }
        throw new AccessDeniedException(messageUtils.getUnauthorizedMessage());
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.springframework.boot.autoconfigure.web.ErrorController#getErrorPath()
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
        HttpSession session = request.getSession();
        UserInfo userInfo = null;
        try {
            userInfo = (UserInfo) session.getAttribute(IConstants.SESSION_OBJECT);
        } catch (IllegalStateException ex) {
            LOGGER.warn("Exception while retrieving user information from session. Exception: "
                    + ex.getLocalizedMessage(), ex);
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
    protected boolean isUserLoggedIn() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (null != authentication) {
            boolean isValid = (authentication.isAuthenticated()
                    && !(authentication instanceof AnonymousAuthenticationToken));
            if (isValid) {
                UserEntity userEntity = (UserEntity) authentication.getPrincipal();
                userEntity = userRepository.findByUserId(userEntity.getUserId());
                if (userEntity != null
                        && userEntity.getStatusEntity().getStatusCode().equalsIgnoreCase(Status.ACTIVE.getCode())) {
                    isValid = true;
                } else {
                    isValid = false;
                }
            }
            return isValid;
        } else {

            return false;
        }
    }
}
