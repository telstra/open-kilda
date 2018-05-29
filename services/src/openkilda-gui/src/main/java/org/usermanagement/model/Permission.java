package org.usermanagement.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

/**
 * The Class PermissionResponse.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"permission_id", "name", "description", "status", "roles"})
public class Permission {

    @JsonProperty("permission_id")
    private Long permissionId;

    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;

    @JsonProperty("isEditable")
    private Boolean isEditable = true;

    @JsonProperty("isAdminPermission")
    private Boolean isAdminPermission = false;

    @JsonProperty("status")
    private String status;

    @JsonProperty("roles")
    private List<Role> roles;

    /**
     * Gets the permission id.
     *
     * @return the permission id
     */
    public Long getPermissionId() {
        return permissionId;
    }

    /**
     * Sets the permission id.
     *
     * @param permissionId the new permission id
     */
    public void setPermissionId(final Long permissionId) {
        this.permissionId = permissionId;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(final String status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public List<Role> getRoles() {
        return roles;
    }

    public void setRoles(final List<Role> roles) {
        this.roles = roles;
    }

    public Boolean getIsEditable() {
        return isEditable;
    }

    public void setIsEditable(final Boolean isEditable) {
        this.isEditable = isEditable;
    }

    public Boolean getIsAdminPermission() {
        return isAdminPermission;
    }

    public void setIsAdminPermission(final Boolean isAdminPermission) {
        this.isAdminPermission = isAdminPermission;
    }

    @Override
    public String toString() {
        return "Permission [permissionId=" + permissionId + ", name=" + name + ", description="
                + description + ", status=" + status + ", roles=" + roles + "]";
    }
}
