package org.usermanagement.controller;

import org.openkilda.security.CustomWebAuthenticationDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.usermanagement.model.ResetPassword;
import org.usermanagement.model.Role;
import org.usermanagement.model.UserInfo;
import org.usermanagement.service.UserService;

@RestController
@RequestMapping(path = "/user", produces = MediaType.APPLICATION_JSON_VALUE)
public class UserController {

    @Autowired
    private UserService userService;

    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/role/{role_id}", method = RequestMethod.GET)
    public Role getUsersByRoleId(@PathVariable("role_id") final Long roleId) {
        Role role = userService.getUserByRoleId(roleId);

        return role;
    }

    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(method = RequestMethod.POST)
    public UserInfo create(@RequestBody final UserInfo request) {
        UserInfo userResponse = userService.createUser(request);

        return userResponse;
    }

    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/{user_id}", method = RequestMethod.PUT)
    public UserInfo updateUser(@RequestBody final UserInfo request, @PathVariable("user_id") final Long userId) {
        request.setUserId(userId);
        UserInfo userResponse = userService.updateUser(request, userId);
        return userResponse;
    }

    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(method = RequestMethod.GET)
    public List<UserInfo> getUserList() {
        List<UserInfo> userResponseList = userService.getAllUsers();
        return userResponseList;
    }

    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/{user_id}", method = RequestMethod.GET)
    public UserInfo getUserById(@PathVariable("user_id") final Long userId) {
        UserInfo userInfo = userService.getUserById(userId);
        return userInfo;
    }


    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequestMapping(value = "/{user_id}", method = RequestMethod.DELETE)
    public void deleteUserById(@PathVariable("user_id") Long userId) {
        userService.deleteUserById(userId);

    }

    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/role/{role_id}", method = RequestMethod.PUT)
    public Role assignUsersByRoleId(@PathVariable("role_id") final Long roleId, @RequestBody Role request) {
        Role role = userService.assignUserByRoleId(roleId, request);

        return role;
    }

    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/changePassword/{user_id}", method = RequestMethod.PUT)
    public ResetPassword changePassword(@RequestBody final UserInfo request, 
    		@PathVariable("user_id") final Long userId,HttpServletRequest httpRequest) {
    	ResetPassword resetPassword = new ResetPassword();
		CustomWebAuthenticationDetails customWebAuthenticationDetails = new CustomWebAuthenticationDetails(httpRequest);
		userService.changePassword(request, userId, customWebAuthenticationDetails);
    	resetPassword.setMsg("Password has been changed successfully.");
        return resetPassword;
    }

    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/resetpassword/{id}", method = RequestMethod.GET)
    public Object resetPassword(@RequestParam(value = "admin", required = false) boolean admin, 
    		@PathVariable("id") final Long userId) {
    	UserInfo response = userService.resetPassword(userId,admin);
    	if(!admin){
	        ResetPassword resetPassword = new ResetPassword();
	        resetPassword.setMsg("Password has been sent to your EmailId");
	        return resetPassword;
    	}else{
            return response;
    	}
    }
    
    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/reset2fa/{user_id}", method = RequestMethod.PUT)
    public ResetPassword resetTwofa(@PathVariable("user_id") final Long userId) {
        ResetPassword resetPassword = new ResetPassword();
        userService.reset2fa(userId);
        resetPassword.setMsg("2FA has been reset for the user.");
        return resetPassword;
    }
}
