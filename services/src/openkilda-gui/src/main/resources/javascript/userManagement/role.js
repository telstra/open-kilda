class Role {
    constructor(name, desc, permissions) {
        this.name = name;
        this.description = desc;
        this.permission_id = permissions;
    }
}

var roleService = (function() {
    var role_id;
    /*** Get roles ***/
    function getRoles() {
        return $.ajax({
            url: './role',
            type: 'GET',
            dataType: "json"
        });
    }

    /*** Add role ***/
    function addRole($event) {
        $event.preventDefault();
        var name = document.roleForm.rname.value;
        var desc = document.roleForm.description.value;
        var selectedPermission = [];
        selectedPermission = $('select#rolePermissionSelect').val();
        var role_id = $("#role_id").val();

        if (name == "" || name == null) {
            $('#rnameError').show();
            $('input[name="rname"').addClass("errorInput");
        }
        if (desc == "" || desc == null) {
            $('#descriptionError').show();
            $('textarea[name="description"').addClass("errorInput");
        }
        if (selectedPermission === undefined || selectedPermission.length == 0) {
            $('#permissionError').show();
            $('ul.select2-choices').addClass("errorInput");
        }
        if ((name == "" || name == null) || (desc == "" || desc == null)) {
            return false;
        }
        $('#roleForm').hide();
        $('#roleTabData').show();
        var roleData;
        if (role_id) {
            roleData = {
                name: name,
                description: desc,
                permission_id: selectedPermission
            };
            $.ajax({
                url: './role/' + role_id,
                contentType: 'application/json',
                dataType: "json",
                type: 'PUT',
                data: JSON.stringify(roleData)
            }).then(function(response) {
                $("#role-details").html('');
                roleService.getRoles().then(function(allRoles) {
                    if (allRoles && allRoles.length) {
                        $("#loading").css("display", "none");
                        roleService.showAllRoles(allRoles);
                    }
                });
                common.infoMessage('Role updated successfully.', 'success');
            }, function(error) {
                common.infoMessage(error.responseJSON['error-auxiliary-message'], 'error');
            });
        } else {
            roleData = new Role(name, desc, selectedPermission);
            $.ajax({
                url: './role',
                contentType: 'application/json',
                dataType: "json",
                type: 'POST',
                data: JSON.stringify(roleData)
            }).then(function(response) {
                $("#role-details").html('');
                roleService.getRoles().then(function(allRoles) {
                    if (allRoles && allRoles.length) {
                        $("#loading").css("display", "none");
                        roleService.showAllRoles(allRoles);
                    }
                });
                common.infoMessage('Role added successfully.', 'success');
            }, function(error) {
                $("#loading").css("display", "none");
                common.infoMessage(error.responseJSON['error-auxiliary-message'], 'error');
            });
        }
    }
    /*** Get role ***/
    function getRole(id) {
        return $.ajax({
            url: './role/' + id,
            type: 'GET',
            dataType: "json"
        });
    }
    /*** Show all roles ***/
    function showAllRoles(response) {
        var tableRowData = [];
        if (!response || response.length == 0) {
            response = []
            common.infoMessage('No Users Available', 'info');
        }
        if (response) {
            for (var i = 0; i < response.length; i++) {
                var role = "response";
                var PermissionList = [];
                var tableCol1 = "<td class='divTableCell' title ='" + ((response[i].name === "" || response[i].name == undefined) ? "-" : response[i].name) + "'>" + ((response[i].name === "" || response[i].name == undefined) ? "-" : response[i].name) + "</td>";
                var tableCol2 = "<td class='divTableCell wrapPermission'><div class='permissionCol'>";
                if (response[i].permissions) {
                    for (var p = 0; p < response[i].permissions.length; p++) {
                        if (response[i].permissions[p].status == 'Active') {
                            tableCol2 += "<span class='badge'>" + response[i].permissions[p].name + "</span>";
                        } else {
                            tableCol2 += "<span class='badge btn-danger active'>" + response[i].permissions[p].name + "</span>";
                        }
                    }
                }
                tableCol2 += "</div></td>";
                var tableCol3 = "<td class='divTableCell'>" +
                    "<i title='edit' class='fa icon-edit' onclick='roleService.editRole(" + response[i].role_id + ")' permission='um_role_edit'></i>" +
                    "<i title='assignRoleToUsers' class='fa icon-group' onClick='roleService.assignRoleToUsers(" + response[i].role_id + ")' permission='um_assign_role_to_users'></i>" +
                    "<i title='viewUsersWithRole' class='fa icon-user' onClick='roleService.viewUsersByRole(" + response[i].role_id + ")' permission='um_role_view_users'></i>" +
                    "<i title='delete' class='fa icon-trash' onclick='openConfirmationModal(" + response[i].role_id + ",\"deleteRole\")' permission='um_role_delete'></i>" +
                    "</td>";

                //$("#roleTable").append(tableRow);
                tableRowData.push([tableCol1, tableCol2, tableCol3]);
                if (response[i].state && (response[i].state == "ACTIVATED")) {
                    $("#div_" + (i + 1)).addClass('up-state');
                } else {
                    $("#div_" + (i + 1)).addClass('down-state');
                }
            }
        }
        var tableVar = $('#roleTable').DataTable({
            data: tableRowData,
            "drawCallback": function(settings) {
                $('.paginate_button, .sorting, .sorting_asc, .sorting_desc').on("click", function() {
                    hasPermission();
                });
            },
            "iDisplayLength": 10,
            "aLengthMenu": [
                [10, 20, 35, 50, -1],
                [10, 20, 35, 50, "All"]
            ],
            "responsive": true,
            "bSortCellsTop": true,
            language: {
                searchPlaceholder: "Search"
            },
            "autoWidth": false,
            destroy: true,
            'aoColumnDefs': [{
                'bSortable': false,
                'aTargets': [-1] /* 1st one, start by the right */
            }],
            "aoColumns": [{
                    sWidth: '20%',
                    sClass: 'divTableCell'
                },
                {
                    sWidth: '50%',
                    sClass: 'divTableCell'
                },
                {
                    sWidth: '30%',
                    sClass: 'divTableCell'
                }

            ]
        });

        tableVar.columns().every(function() {
            var that = this;
            $('input', this.header()).on('keyup change', function() {

                if (that.search() !== this.value) {
                    that.search(this.value).draw();
                }
            });
        });

        $('#roleTable').show();
        hasPermission();
    }
    /* permission to select used in edit action */
    function permissionsToSelect(id) {
        permissionService.getPermissions().then(function(permissionRes) {
            var selectRoleElements = selectedRole = '';
            if (id > 0 && id != undefined) {
                roleService.getRole(id).then(function(roleRes) {
                    var rolePermissionArray = [];
                    if (roleRes.permissions) {
                        for (var a = 0; a < roleRes.permissions.length; a++) {
                            rolePermissionArray.push(roleRes.permissions[a].permission_id);
                        }
                    }
                    for (var i = 0; i < permissionRes.length; i++) {
                        if (roleRes.permissions) {
                            if (jQuery.inArray(permissionRes[i].permission_id, rolePermissionArray) != -1) {
                                selectedRole = "selected";
                            } else {
                                selectedRole = '';
                            }
                        }
                        selectRoleElements += "<option value='" + permissionRes[i].permission_id + "'  " + selectedRole + ">" + permissionRes[i].name + "</option>";
                    }

                    $("#rolePermissionSelect").html(selectRoleElements);
                    $('#rolePermissionSelect').select2({
                        width: "100%"
                    });
                }, function(error) {
                    common.infoMessage(error.responseJSON['error-auxiliary-message'], 'error');
                });
            } else {
                if (permissionRes) {
                    for (var k = 0; k < permissionRes.length; k++) {
                        selectRoleElements += "<option value='" + permissionRes[k].permission_id + "'>" + permissionRes[k].name + "</option>";
                    }
                }
                $("#rolePermissionSelect").html(selectRoleElements);
                $('#rolePermissionSelect').select2({
                    placeholder: "Select Permission",
                    width: "100%"
                });
            }
        }, function(err) {
            common.infoMessage(error.responseJSON['error-auxiliary-message'], 'error');
        });
    }
    /* edit roles */
    function editRole(id) {
        /* Reset Form */
        $("#addRoleForm").find(".errorInput").removeClass("errorInput");
        $("#addRoleForm").find(".error").hide();
        $("#role_id").parent().remove(); //remove existing hidden role id input
        $("#addRoleForm").append("<div class='form-group row'><input type='hidden' id='role_id' value='" + id + "' /></div>"); //add hidden permission id input
        $("#roleTabData").hide();
        $("#roleForm").show();
        $("#addRoleForm").attr("onsubmit", "roleService.addRole(event)");
        $("#roleUsersAssign").hide();
        $("#rolePermissionAssign").show();
        $("#rname, #rdescription").attr("readonly", false);
        $("#add_update_btn").text("Update Role");
        roleService.getRole(id).then(function(response) {
            document.roleForm.rname.value = response.name;
            document.roleForm.description.value = response.description;
            role_id = response.role_id;
            roleService.permissionsToSelect(role_id);
        }, function(err) {
            common.infoMessage(error.responseJSON['error-auxiliary-message'], 'error');
        });
    }
    /* users to select used in assign role to users action */
    function selectUserByRole(roleId) {
        userService.getUsers().then(function(userRes) {
            var selectRoleElements = selectedRole = '';
            if (roleId > 0 && roleId != undefined) {
                roleService.getRole(roleId).then(function(roleRes) {
                    userRes.forEach((element, index, array) => {
                        if (element.roles) {
                            if (jQuery.inArray(roleRes.name, element.roles) != -1) {
                                selectedRole = "selected";
                            } else {
                                selectedRole = '';
                            }
                            selectRoleElements += "<option value='" + element.user_id + "'  " + selectedRole + ">" + element.name + "</option>";
                        } else {
                            selectRoleElements += "<option value='" + element.user_id + "'>" + element.name + "</option>";
                        }
                    });
                    $("#roleUserSelect").html(selectRoleElements);
                    $('#roleUserSelect').select2({
                        width: "100%"
                    });
                }, function(error) {
                    common.infoMessage(error.responseJSON['error-auxiliary-message'], 'error');
                });
            } else {
                common.infoMessage(error.responseJSON['error-auxiliary-message'], 'error');

            }
        }, function(err) {
            common.infoMessage(error.responseJSON['error-auxiliary-message'], 'error');
        });
    }
    /* Assign roles to users */
    function assignRoleToUsers(roleId) {
        $("#addRoleForm").find(".errorInput").removeClass("errorInput");
        $("#addRoleForm").find(".error").hide();
        $("#role_id").parent().remove(); //remove existing hidden role id input
        $("#addRoleForm").append("<div class='form-group row'><input type='hidden' id='role_id' value='" + roleId + "' /></div>"); //add hidden permission id input
        $("#roleTabData").hide();
        $("#roleForm").show();
        $("#addRoleForm").attr("onsubmit", "roleService.updateRoleToUsers(event)");
        $("#rolePermissionAssign").hide();
        $("#roleUsersAssign").show();
        $("#rname, #rdescription").attr("readonly", true);
        $("#add_update_btn").text("Assign Role");
        roleService.getRole(roleId).then(function(response) {
            document.roleForm.rname.value = response.name;
            document.roleForm.description.value = response.description;
            role_id = response.role_id;
            roleService.selectUserByRole(role_id);
        }, function(err) {
            common.infoMessage(error.responseJSON['error-auxiliary-message'], 'error');
        });
    }
    /****getUsersByRole*****/
    function getUsersByRole(roleId) {
        return $.ajax({
            url: './user/role/' + roleId,
            type: 'GET',
            dataType: "json"
        });
    }
    /*** view Users by Role ***/
    function viewUsersByRole(RoleId) {
        $("#roleTabData").show();
        $("#roleForm").hide();
        roleService.getUsersByRole(RoleId).then(function(response) {
            if (response) {
                $("#roleTabData").html('');
                var html = '<div class="viewLayout"><div class="row viewDetail">' +
                    '<label class="col-sm-2 col-form-label text-right">Name:</label>' +
                    '<div class="col-sm-6"><input type="text" class="form-control" value="' + response.name + '" disabled/></div>' +
                    '</div>' +
                    '<div class="row viewDetail">' +
                    '<label class="col-sm-2 col-form-label text-right">Description:</label>' +
                    '<div class="col-sm-6"><input type="text" class="form-control" value="' + response.description + '" disabled/></div>' +
                    '</div>' +
                    '<div class="row viewDetail">' +
                    '<label class="col-sm-2 col-form-label text-right">Users:</label>';
                if (response.users.length > 0) {
                    var users = [];
                    response.users.forEach(function(user) {
                        users.push(user.name);
                    });
                    html += '<div class="col-sm-6"><input type="text" class="form-control" value="' + users.join(' | ') + '" disabled/></div>'
                } else {
                    html += '<div class="col-sm-6"><input type="text" class="form-control" value= No users assigned. disabled/></div>'
                }
                html += '</div><div class="row viewDetail"><label class="col-sm-2 col-form-label"></label><div class="col-sm-6 text-right"><a class="backLoadTabData btn kilda_btn" href="#roleTab" data-toggle="tab" data-url="#roleTab">Back</a></div></div></div>';
                $("#roleTabData").html(html);
            } else {
                // Error Handling
                common.infoMessage(response['error-auxiliary-message'], 'error');
            }
        }, function(err) {
            common.infoMessage(error.responseJSON['error-auxiliary-message'], 'error');
        });
    }
    /*** Update role to users ***/
    function updateRoleToUsers($event) {
        $event.preventDefault();

        var selectedUsers = [];
        selectedUser = $('select#roleUserSelect').val();
        selectedUser.forEach(function(element, index) {
            selectedUsers[index] = {
                'user_id': element
            };
        });

        if (selectedUser === undefined || selectedUser.length == 0) {
            $('#userError').show();
        }
        if (selectedUser === undefined || selectedUser.length == 0) {
            return false;
        }
        $('#roleForm').hide();
        $('#roleTabData').show();
        var roleData = {
            'users': selectedUsers
        };

        if (role_id) {
            $.ajax({
                url: './user/role/' + role_id,
                contentType: 'application/json',
                dataType: "json",
                type: 'PUT',
                data: JSON.stringify(roleData)
            }).then(function(response) {
                $("#role-details").html('');
                roleService.getRoles().then(function(allRoles) {
                    if (allRoles && allRoles.length) {
                        $("#loading").css("display", "none");
                        roleService.showAllRoles(allRoles);
                    }
                });
                common.infoMessage('Role updated successfully.', 'success');
            }, function(error) {
                $("#loading").css("display", "none");
                common.infoMessage(error.responseJSON['error-auxiliary-message'], 'error');
            });
        }
    }
    /* remove role */
    function removeRole() {
        $("#confirmModal").modal('hide');
        var id = document.getElementById("inputId").value;
        if (id != undefined) {
            $.ajax({
                url: './role/' + id,
                type: 'DELETE'
            }).then(function(response) {
                $("#role-details").html('');
                roleService.getRoles().then(function(allRoles) {
                    if (allRoles && allRoles.length) {
                        $("#loading").css("display", "none");
                        roleService.showAllRoles(allRoles);
                    }
                });
                common.infoMessage('Role removed successfully.', 'success');
            }, function(error) {
                $("#loading").css("display", "none");
                common.infoMessage(error.responseJSON['error-auxiliary-message'], 'error');
            });
        }
    }
    /*** return function ***/
    return {
        getRoles: getRoles,
        addRole: addRole,
        getRole: getRole,
        showAllRoles: showAllRoles,
        editRole: editRole,
        removeRole: removeRole,
        permissionsToSelect: permissionsToSelect,
        getUsersByRole: getUsersByRole,
        viewUsersByRole: viewUsersByRole,
        selectUserByRole: selectUserByRole,
        assignRoleToUsers: assignRoleToUsers,
        updateRoleToUsers: updateRoleToUsers
    }
})()