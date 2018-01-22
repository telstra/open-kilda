package org.openkilda.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;
import org.openkilda.constants.IConstants;
import org.openkilda.dao.UserRepository;
import org.openkilda.entity.Role;
import org.openkilda.entity.User;
import org.openkilda.model.UserInfo;

/**
 *
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
    private UserRepository userRepository;

    /**
     * Login.
     *
     * @param model the model
     * @return the model and view
     */
    @RequestMapping(value = {"/", "/login"})
    public ModelAndView login(final HttpServletRequest request) {
        LOGGER.info("[login] - start");
        return validateAndRedirect(request, IConstants.View.REDIRECT_HOME);
    }

    /**
     * Logout.
     *
     * @param model the model
     * @return the model and view
     */
    @RequestMapping("/logout")
    public ModelAndView logout(final Model model) {
        LOGGER.info("[logout] - start");
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
    public ModelAndView authenticate(@RequestParam("username") final String username,
            @RequestParam("password") final String password, final HttpServletRequest request) {
        LOGGER.info("[authenticate] - start");
        ModelAndView modelAndView = new ModelAndView(IConstants.View.LOGIN);
        List<String> errors = new ArrayList<String>();
        try {
            UsernamePasswordAuthenticationToken token =
                    new UsernamePasswordAuthenticationToken(username, password);
            Authentication authenticate = authenticationManager.authenticate(token);
            if (authenticate.isAuthenticated()) {
                modelAndView.setViewName(IConstants.View.REDIRECT_HOME);
                UserInfo userInfo = getLoggedInUser(request);
                populateUserInfo(userInfo, username);
                SecurityContextHolder.getContext().setAuthentication(authenticate);
            } else {
                errors.add("authenticate() Authentication failure with username{} and password{}");
                LOGGER.error("authenticate() Authentication failure with username{} and password{}");
                modelAndView.setViewName(IConstants.View.REDIRECT_LOGIN);
            }

        } catch (Exception e) {
            LOGGER.error("authenticate() Authentication failure", e);
            errors.add("authenticate() Authentication failure");
            modelAndView.setViewName(IConstants.View.REDIRECT_LOGIN);

        }
        if (errors.size() > 0) {
            modelAndView.addObject("error", errors);
        }
        LOGGER.info("exit LoginController method authenticate");
        return modelAndView;
    }

    /**
     * Add user information in session
     *
     * @param request HttpServletRequest to add user information in session.
     * @param userName who's information is added in session.
     * @return user information
     */
    private void populateUserInfo(final UserInfo userInfo, final String username) {
        User user = userRepository.findByUsername(username);

        Set<Role> set = user.getRoles();
        Iterator<?> iterator = set.iterator();
        Role role = null;
        while (iterator.hasNext()) {
            role = (Role) iterator.next();
        }

        userInfo.setUserId(user.getUserId().intValue());
        userInfo.setUsername(user.getUsername());
        userInfo.setName(user.getName());
        if (role != null) {
            userInfo.setRole(role.getRole());
        }
    }
}
