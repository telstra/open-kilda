package org.openkilda.web;

import java.io.Serializable;

/**
 * The Class SessionObject.
 * 
 * This entity is used to set all objects in session.
 * 
 * @author Gaurav Chugh
 *
 */
public class SessionObject implements Serializable {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;

    /** The user id. */
    private Integer userId;

    /** The username. */
    private String username;

    /** The name. */
    private String name;

    /** The role. */
    private String role;

    /**
     * Gets the user id.
     *
     * @return the user id
     */
    public Integer getUserId() {
        return userId;
    }

    /**
     * Sets the user id.
     *
     * @param userId the new user id
     */
    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    /**
     * Gets the username.
     *
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * Sets the username.
     *
     * @param username the new username
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Gets the name.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name.
     *
     * @param name the new name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the role.
     *
     * @return the role
     */
    public String getRole() {
        return role;
    }

    /**
     * Sets the role.
     *
     * @param role the new role
     */
    public void setRole(String role) {
        this.role = role;
    }

}
