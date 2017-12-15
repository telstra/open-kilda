package org.openkilda.controller;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;
import org.openkilda.dao.UserRepository;
import org.openkilda.entity.Role;
import org.openkilda.entity.User;
import org.openkilda.utility.IConstants;
import org.openkilda.web.SessionObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

/**
 *
 * The Class LoginController : entertain requests of login module.
 * 
 * @author Gaurav Chugh
 * 
 */
@Controller
public class LoginController extends BaseController {

	/** The Constant log. */
	private static final Logger log = Logger.getLogger(LoginController.class);

	/** The Constant VIEW_HOME. */
	static final String VIEW_HOME = "home";

	/** The Constant VIEW_TOPOLOGY. */
	static final String VIEW_TOPOLOGY = "topology";

	/** The Constant VIEW_LOGOUT. */
	static final String VIEW_LOGOUT = "logout";

	/** The Constant REDIRECT_HOME. */
	static final String REDIRECT_HOME = "redirect:/home";

	/** The Constant VIEW_SWITCH. */
	static final String VIEW_SWITCH = "switchport";

	/** The Constant VIEW_ISL. */
	static final String VIEW_ISL = "isldetails";
	
	/** The Constant PORT_DETAILS. */
	static final String VIEW_PORT_DETAILS = "portdetails";

	/** The authentication manager. */
	@Autowired
	private AuthenticationManager authenticationManager;

	/** The user repository. */
	@Autowired
	private UserRepository userRepository;

	/**
	 * Login.
	 *
	 * @param model
	 *            the model
	 * @return the model and view
	 */
	@RequestMapping(value = { "/", "/login" })
	public ModelAndView login(Model model) {
		log.info("Inside LoginController method login");
		ModelAndView modelAndView;

		if (isUserLoggedIn()) {
			modelAndView = new ModelAndView(REDIRECT_HOME);
		} else {
			modelAndView = new ModelAndView(VIEW_LOGIN);
		}
		log.info("exit LoginController method login");
		return modelAndView;
	}

	/**
	 * Home.
	 *
	 * @param model
	 *            the model
	 * @param request
	 *            the request
	 * @return the string
	 */

	@RequestMapping(value = "/home")
	public String home(ModelMap model, HttpServletRequest request) {
		log.info("Inside LoginController method home");
		return VIEW_HOME;
	}

	/**
	 * Topology.
	 *
	 * @param model
	 *            the model
	 * @param request
	 *            the request
	 * @return the model and view
	 */
	@RequestMapping(value = "/topology")
	public ModelAndView topology(ModelMap model, HttpServletRequest request) {
		log.info("Inside LoginController method topology");
		ModelAndView modelAndView;
		if (isUserLoggedIn()) {
			SessionObject sessionObject = getSessionObject();

			if (sessionObject.getRole().equalsIgnoreCase(IConstants.USER_ROLE)) {
				modelAndView = new ModelAndView(REDIRECT_HOME);
			} else {
				modelAndView = new ModelAndView(VIEW_TOPOLOGY);
			}
		} else {
			modelAndView = new ModelAndView(VIEW_LOGIN);
		}
		log.info("exit LoginController method topology");
		return modelAndView;

	}

	/**
	 * Logout.
	 *
	 * @param model
	 *            the model
	 * @return the model and view
	 */
	@RequestMapping("/logout")
	public ModelAndView logout(Model model) {
		log.info("Inside LoginController method logout");
		return new ModelAndView(VIEW_LOGOUT);
	}

	@RequestMapping(value = "/switchport")
	public ModelAndView switchDetails(ModelMap model, HttpServletRequest request) {

		ModelAndView modelAndView;

		if (isUserLoggedIn()) {
			SessionObject sessionObject = getSessionObject();

			if (sessionObject.getRole().equalsIgnoreCase(IConstants.USER_ROLE)) {
				modelAndView = new ModelAndView(REDIRECT_HOME);
			} else {
				modelAndView = new ModelAndView(VIEW_SWITCH);
			}
		} else {
			modelAndView = new ModelAndView(VIEW_LOGIN);
		}

		return modelAndView;

	}

	/**
	 * Isl details.
	 *
	 * @param model
	 *            the model
	 * @param request
	 *            the request
	 * @return the model and view
	 */
	@RequestMapping(value = "/isldetails")
	public ModelAndView islDetails(ModelMap model, HttpServletRequest request) {

		ModelAndView modelAndView;

		if (isUserLoggedIn()) {
			SessionObject sessionObject = getSessionObject();

			if (sessionObject.getRole().equalsIgnoreCase(IConstants.USER_ROLE)) {
				modelAndView = new ModelAndView(REDIRECT_HOME);
			} else {
				modelAndView = new ModelAndView(VIEW_ISL);
			}
		} else {
			modelAndView = new ModelAndView(VIEW_LOGIN);
		}

		return modelAndView;

	}
	
	
	
	@RequestMapping(value = "/portdetails")
	public ModelAndView portDetails(ModelMap model, HttpServletRequest request) {

		ModelAndView modelAndView;

		if (isUserLoggedIn()) {
			SessionObject sessionObject = getSessionObject();

			if (sessionObject.getRole().equalsIgnoreCase(IConstants.USER_ROLE)) {
				modelAndView = new ModelAndView(REDIRECT_HOME);
			} else {
				modelAndView = new ModelAndView(VIEW_PORT_DETAILS);
			}
		} else {
			modelAndView = new ModelAndView(VIEW_LOGIN);
		}

		return modelAndView;

	}

	/**
	 * Authenticate.
	 *
	 * @param username
	 *            the username
	 * @param password
	 *            the password
	 * @param request
	 *            the request
	 * @return the model and view
	 */
	@RequestMapping(value = "/authenticate", method = RequestMethod.POST)
	public ModelAndView authenticate(@RequestParam("username") String username,
			@RequestParam("password") String password,
			HttpServletRequest request) {
		log.info("Inside LoginController method authenticate");
		ModelAndView modelAndView = new ModelAndView(VIEW_LOGIN);
		List<String> errors = new ArrayList<String>();
		try {
			UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
					username, password);
			Authentication authenticate = authenticationManager
					.authenticate(token);
			if (authenticate.isAuthenticated()) {
				modelAndView.setViewName(REDIRECT_HOME);

				User user = userRepository.findByUsername(username);

				Set<Role> set = user.getRoles();
				Iterator<?> iterator = set.iterator();
				Role role = null;
				while (iterator.hasNext()) {
					role = (Role) iterator.next();
				}

				SessionObject sessionObject = getSessionObject();
				sessionObject.setUserId(user.getUserId().intValue());
				sessionObject.setUsername(user.getUsername());
				sessionObject.setName(user.getName());
				if (role != null) {
					sessionObject.setRole(role.getRole());
				}

				SecurityContextHolder.getContext().setAuthentication(
						authenticate);
			} else {
				errors.add("authenticate() Authentication failure with username{} and password{}");
				log.warn("authenticate() Authentication failure with username{} and password{}");
				modelAndView.setViewName("redirect:/login");
			}

		} catch (Exception e) {
			log.warn("authenticate() Authentication failure");
			errors.add("authenticate() Authentication failure");
			modelAndView.setViewName("redirect:/login");

		}
		if (errors.size() > 0) {
			modelAndView.addObject("error", errors);
		}
		log.info("exit LoginController method authenticate");
		return modelAndView;
	}

}
