package org.openkilda.entity;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * The Class Role.
 * 
 * @author sumitpal.singh
 */
@Entity
@Table(name = "role")
public class Role implements Serializable {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;

    /** The user role id. */
    @Id
    @Column(name = "user_role_id", nullable = false)
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long userRoleId;

    /** The role. */
    @Column(name = "user_role", nullable = false)
    private String role;

    /**
     * Gets the user role id.
     *
     * @return the user role id
     */
    public Long getUserRoleId() {
        return userRoleId;
    }

    /**
     * Sets the user role id.
     *
     * @param userRoleId the new user role id
     */
    public void setUserRoleId(Long userRoleId) {
        this.userRoleId = userRoleId;
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

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "Role [userRoleId=" + userRoleId + ", role=" + role + "]";
    }

}
