package org.openkilda.model;

import java.io.Serializable;

/**
 * The Class UserInfo.
 *
 * This entity is used to set all objects in session.
 *
 * @author Gaurav Chugh
 *
 */
public class UserInfo implements Serializable {

    private static final long serialVersionUID = 5779373512137456449L;

    private Integer userId;
    private String username;
    private String name;
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
    public void setUserId(final Integer userId) {
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
    public void setUsername(final String username) {
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
    public void setName(final String name) {
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
    public void setRole(final String role) {
        this.role = role;
    }
}
