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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.usermanagement.dao.entity.PermissionEntity;
import org.usermanagement.dao.entity.RoleEntity;
import org.usermanagement.dao.entity.UserEntity;
import org.usermanagement.dao.repository.PermissionRepository;
import org.usermanagement.model.UserInfo;
import org.usermanagement.service.UserService;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

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

    @Autowired
    private PermissionRepository permissionRepository;
    
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
            @RequestParam("password") final String password, final HttpServletRequest request) {
        ModelAndView modelAndView = new ModelAndView(IConstants.View.LOGIN);
        String error = null;
        username = username != null ? username.toLowerCase() : null;
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(username, password);
        CustomWebAuthenticationDetails customWebAuthenticationDetails = new CustomWebAuthenticationDetails(request);
        token.setDetails(customWebAuthenticationDetails);

        try {
            Authentication authenticate = authenticationManager.authenticate(token);
            if (authenticate.isAuthenticated()) {
                modelAndView.setViewName(IConstants.View.REDIRECT_HOME);
                UserInfo userInfo = getLoggedInUser(request);
                populateUserInfo(userInfo, username);
                request.getSession().setAttribute(IConstants.SESSION_OBJECT, userInfo);
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
        } catch (UsernameNotFoundException | BadCredentialsException e) {
            LOGGER.warn("Authentication failure", e);
            error = "Invalid email or password";
            modelAndView.setViewName(IConstants.View.REDIRECT_LOGIN);
        } catch (Exception e) {
            LOGGER.warn("Authentication failure", e);
            error = "Login Failed. Error: '" + e.getMessage() + "'.";
            modelAndView.setViewName(IConstants.View.REDIRECT_LOGIN);
        }

        if (error != null) {
            modelAndView.addObject("error", error);
        }
        return modelAndView;
    }

    /**
     * Add user information in session.
     *
     * @param request HttpServletRequest to add user information in session.
     * @param userName who's information is added in session.
     * @return user information
     */
    private void populateUserInfo(final UserInfo userInfo, final String username) {
        UserEntity user = userService.getUserByUsername(username);
        Set<RoleEntity> roleEntities = user.getRoles();
        Set<String> roles = new HashSet<String>();
        Set<String> permissions = new HashSet<String>();
        for (RoleEntity roleEntity : roleEntities) {
            roles.add(roleEntity.getName());
            userInfo.setRole("ROLE_ADMIN");
            if (user.getUserId() != 1) {
                Set<PermissionEntity> permissionEntities = roleEntity.getPermissions();
                for (PermissionEntity permissionEntity : permissionEntities) {
                    if (permissionEntity.getStatusEntity().getStatusCode().equalsIgnoreCase(Status.ACTIVE.getCode())
                            && !permissionEntity.getIsAdminPermission()) {
                        permissions.add(permissionEntity.getName());
                    }
                }
            }
        }
        if (user.getUserId() == 1) {
            List<PermissionEntity> permissionEntities = permissionRepository.findAll();
            for (PermissionEntity permissionEntity : permissionEntities) {
                permissions.add(permissionEntity.getName());
            }
        }
        userInfo.setUserId(user.getUserId());
        userInfo.setUsername(user.getUsername());
        userInfo.setName(user.getName());
        userInfo.setRoles(roles);
        userInfo.setPermissions(permissions);
        userInfo.setIs2FaEnabled(user.getIs2FaEnabled());
    }
}
