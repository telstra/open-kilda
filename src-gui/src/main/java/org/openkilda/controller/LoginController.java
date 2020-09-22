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
import org.openkilda.exception.InvalidOtpException;
import org.openkilda.exception.OtpRequiredException;
import org.openkilda.exception.TwoFaKeyNotSetException;
import org.openkilda.security.CustomWebAuthenticationDetails;
import org.openkilda.security.TwoFactorUtility;

import org.apache.log4j.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.usermanagement.dao.entity.UserEntity;
import org.usermanagement.model.UserInfo;
import org.usermanagement.service.UserService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * The Class LoginController : entertain requests of login module.
 *
 * @author Gaurav Chugh
 *
 */

@Controller
public class LoginController extends BaseController {

    private static final Logger LOGGER = Logger.getLogger(LoginController.class);

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserService userService;
    
    @Value("${application.name}")
    private String applicationName;

    /**
     * Login.
     *
     * @return the model and view
     */
    @RequestMapping(value = { "/", "/login" })
    public ModelAndView login(final HttpServletRequest request) {
        return validateAndRedirect(request, IConstants.View.LOGIN);
    }
    
    /**
     * Logout.
     *
     * @param model the model
     * @return the model and view
     */
    @RequestMapping("/logout")
    public ModelAndView logout(final Model model) {
        return new ModelAndView(IConstants.View.LOGOUT);
    }

    /**
     * Authenticate.
     *
     * @param username the username
     * @param password the password
     * @param request the request
     * @return the model and view
     */
    
    @RequestMapping(value = "/authenticate", method = RequestMethod.POST)
    public ModelAndView authenticate(@RequestParam("username") String username,
            @RequestParam("password") final String password, final HttpServletRequest request, 
            RedirectAttributes redir) {
        ModelAndView modelAndView = new ModelAndView(IConstants.View.LOGIN);
        String error = null;
        username = username != null ? username.toLowerCase() : null;
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(username, password);
        CustomWebAuthenticationDetails customWebAuthenticationDetails = new CustomWebAuthenticationDetails(request);
        token.setDetails(customWebAuthenticationDetails);
        try {
            HttpSession sessionOld = request.getSession(false);
            if (sessionOld != null && !sessionOld.isNew()) {
                sessionOld.invalidate();
            }
            Authentication authenticate = authenticationManager.authenticate(token);
            if (authenticate.isAuthenticated()) {
                modelAndView.setViewName(IConstants.View.REDIRECT_HOME);
                UserInfo userInfo = getLoggedInUser(request);
                userService.populateUserInfo(userInfo, username);
                request.getSession(true).setAttribute(IConstants.SESSION_OBJECT, userInfo);
                SecurityContextHolder.getContext().setAuthentication(authenticate);
                userService.updateLoginDetail(username);
            } else {
                error = "Invalid email or password";
                LOGGER.warn("Authentication failure for user: '" + username + "'");
                modelAndView.setViewName(IConstants.View.REDIRECT_LOGIN);
            }
        } catch (TwoFaKeyNotSetException e) {
            LOGGER.warn("2 FA Key not set for user: '" + username + "'");
            modelAndView.addObject("username", username);
            modelAndView.addObject("password", password);

            String secretKey = TwoFactorUtility.getBase32EncryptedKey();
            modelAndView.addObject("key", secretKey);
            userService.updateUser2FaKey(username, secretKey);
            modelAndView.addObject("applicationName", applicationName);
            modelAndView.setViewName(IConstants.View.TWO_FA_GENERATOR);
        } catch (OtpRequiredException e) {
            LOGGER.warn("OTP required for user: '" + username + "'");
            modelAndView.addObject("username", username);
            modelAndView.addObject("password", password);
            modelAndView.addObject("applicationName", applicationName);
            modelAndView.setViewName(IConstants.View.OTP);
        } catch (InvalidOtpException e) {
            LOGGER.warn("Authentication code is invalid for user: '" + username + "'");
            error = "Authentication code is invalid";
            modelAndView.addObject("username", username);
            modelAndView.addObject("password", password);
            modelAndView.addObject("applicationName", applicationName);
            if (customWebAuthenticationDetails.isConfigure2Fa()) {
                UserEntity userInfo = userService.getUserByUsername(username);
                modelAndView.addObject("key", userInfo.getTwoFaKey());
                modelAndView.setViewName(IConstants.View.TWO_FA_GENERATOR);
            } else {
                modelAndView.setViewName(IConstants.View.OTP);
            }
        } catch (BadCredentialsException e) {
            LOGGER.warn("Authentication failure", e);
            error = "Invalid email or password";
            modelAndView.setViewName(IConstants.View.REDIRECT_LOGIN);
        } catch (LockedException e) {
            error = e.getMessage();
            modelAndView.setViewName(IConstants.View.REDIRECT_LOGIN);
        } catch (Exception e) {
            LOGGER.warn("Authentication failure", e);
            error = "Login Failed. Error: " + e.getMessage() + ".";
            modelAndView.setViewName(IConstants.View.REDIRECT_LOGIN);
        }
        if (error != null) { 
            redir.addFlashAttribute("error", error);
        }
        return modelAndView;
    }
    
}
