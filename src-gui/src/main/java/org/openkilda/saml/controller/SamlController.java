/* Copyright 2020 Telstra Open Source
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

package org.openkilda.saml.controller;

import org.openkilda.constants.IConstants;
import org.openkilda.constants.Status;
import org.openkilda.controller.BaseController;
import org.openkilda.saml.model.SamlConfig;

import org.apache.log4j.Logger;

import org.opensaml.saml2.core.NameID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.saml.SAMLCredential;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.usermanagement.dao.entity.RoleEntity;
import org.usermanagement.model.UserInfo;
import org.usermanagement.service.RoleService;
import org.usermanagement.service.UserService;
import org.usermanagement.util.MessageUtils;

import java.util.Set;

import javax.servlet.http.HttpServletRequest;

@Controller
@RequestMapping(value = "/saml")
public class SamlController extends BaseController {

    private static final Logger LOGGER = Logger.getLogger(SamlController.class);

    @Autowired
    private UserService userService;
    
    @Autowired
    private RoleService roleService;
    
    @Autowired
    private MessageUtils messageUtil;
    
    /**
     * Saml Authenticate.
     *
     * @param request the request
     * @return the model and view
     */
    @RequestMapping(value = "/authenticate")
    public ModelAndView samlAuthenticate(final HttpServletRequest request, RedirectAttributes redir) {
        
        ModelAndView modelAndView = null;
        String error = null;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (null != authentication) {
            boolean isValid = (authentication.isAuthenticated()
                    && !(authentication instanceof AnonymousAuthenticationToken));
            if (isValid) {
                SAMLCredential saml = (SAMLCredential) authentication.getCredentials();
                SamlConfig samlConfig = samlService.getConfigByEntityId(saml.getRemoteEntityID());
                NameID nameId = (NameID) authentication.getPrincipal();
                String username = nameId.getValue();
                UserInfo userInfo = userService.getUserInfoByUsername(username);
                if (userInfo != null
                        && userInfo.getStatus().equalsIgnoreCase(Status.ACTIVE.name())) {
                    userService.populateUserInfo(userInfo, username);
                    request.getSession().setAttribute(IConstants.SESSION_OBJECT, userInfo);
                    userService.updateLoginDetail(username);
                    modelAndView = new ModelAndView(IConstants.View.REDIRECT_HOME);
                } else if (userInfo != null
                        && userInfo.getStatus().equalsIgnoreCase(Status.INACTIVE.name())) {
                    error = messageUtil.getAttributeUserInactive();
                    request.getSession(false);
                    modelAndView = new ModelAndView(IConstants.View.REDIRECT_LOGIN);
                } else if (userInfo == null && samlConfig.isUserCreation()) {
                    Set<RoleEntity> roleEntities = roleService.getRoleByIds(samlConfig.getRoles());
                    userService.createSamlUser(nameId.getValue(), roleEntities);
                    UserInfo userInfo1 = getLoggedInUser(request);
                    userService.populateUserInfo(userInfo1, username);
                    userService.updateLoginDetail(username);
                    modelAndView = new ModelAndView(IConstants.View.REDIRECT_HOME);
                }  else {
                    error = messageUtil.getAttributeUserDoesNotExist();
                    LOGGER.warn("User is not logged in, redirected to login page. Requested view name: ");
                    request.getSession(false);
                    modelAndView = new ModelAndView(IConstants.View.REDIRECT_LOGIN);
                }
            }
        } else {
            error = messageUtil.getAttributeAuthenticationFailure();
            LOGGER.warn("User is not logged in, redirected to login page. Requested view name: ");
            modelAndView = new ModelAndView(IConstants.View.LOGIN);
        } 
        if (error != null) {
            redir.addFlashAttribute("error", error);
        }
        return modelAndView;
    }
}
