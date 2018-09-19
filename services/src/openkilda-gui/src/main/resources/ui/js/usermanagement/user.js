class User {
    constructor(name, user_name, email, role, password,is2FaEnabled) {
        this.name = name;
        this.user_name = user_name;
        this.email = email;
        this.role_id = role;
        this.password = password;
        this.is2FaEnabled = is2FaEnabled;
    }
}

var userService = (function() {
    var user_id;
    
    /*** Get users ***/
    function getUsers() {
        return $.ajax({
            url: './user',
            type: 'GET',
            dataType: "json"
        });
    }

    /*** Add user ***/
    function addUser($event) {
        $event.preventDefault();
        var name = document.userForm.name.value;
        var email = document.userForm.email.value;
        var roles = $('select#userRoleToSelect').val();
        var user_id = $("#user_id").val();

        if (name == "" || name == null) {
            $('#nameError').show();
            $('input[name="name"').addClass("errorInput");
        }
        if (email == "" || email == null) {
            $('#emailError').show();
            $('input[name="email"').addClass("errorInput");
        }
        if (roles == "" || roles == null) {
            $('#userRoleToSelectError').show();
            $('.select2-selection ').addClass("errorInput");
        }
        if ((name == "" || name == null) || (!validateEmail(email) || email == "" || email == null) || (roles == "" || roles == null)) {
            return false;
        }       
        var $form = $("#addUserForm");
        var data = getFormData($form);
        var is2FaEnabled = (typeof(data.is2FaEnabled)!='undefined') ? true : false;
        var userData = new User(data.name, data.email, data.email, roles, data.password,is2FaEnabled);
        $("#loading").css("display", "block");
        if (user_id) {
            $.ajax({
                url: './user/' + user_id,
                contentType: 'application/json',
                dataType: "json",
                type: 'PUT',
                data: JSON.stringify(userData)
            }).then(function(response) {
                $("#user-details").html('');
                userService.getUsers().then(function(allUsers) {
                    if (allUsers && allUsers.length) { 
                        userService.showAllUsers(allUsers);
                    }
                });

                var userId = USER_SESSION.userId;
                if (userId == user_id) {
                    $("#loggedInUserName").text(data.name);
                }

                common.infoMessage('User updated successfully.', 'success');
            }, function(error) {
                $("#loading").css("display", "none");
                common.infoMessage(error.responseJSON['error-message'], 'error');
            });
        } else {
            $.ajax({
                url: './user',
                contentType: 'application/json',
                dataType: "json",
                type: 'POST',
                data: JSON.stringify(userData)
            }).then(function(response) {
                $("#user-details").html('');
                userService.getUsers().then(function(allUsers) {
                    if (allUsers && allUsers.length) {                      
                        userService.showAllUsers(allUsers);
                    }
                });
                common.infoMessage('User added successfully.', 'success');
            }, function(error) {
                $("#loading").css("display", "none");
                common.infoMessage(error.responseJSON['error-message'], 'error');
            });
        }
    }
    
    /*** Get user ***/
    function getUser(id) {
        return $.ajax({
            url: './user/' + id,
            type: 'GET',
            dataType: "json"
        });
    }
    
    /*** Edit user ***/
    function editUser(id) {
        /* Reset Form */
    	$("#loading").css("display", "block");
        $("#addUserForm").find(".errorInput").removeClass("errorInput");
        $("#addUserForm").find(".error").hide();
        $("#user_id").parent().remove(); //remove existing hidden permission id input
        $("#addUserForm").append("<div class='form-group row'><input type='hidden' id='user_id' value='" + id + "' /></div>");
        $("#addUserForm #email").attr("disabled", "disabled"); //add hidden permission id input
        $("#addUserForm #submitBtn").text("Update User");

        userService.getUser(id).then(function(response) {
            document.userForm.name.value = response.name;
            document.userForm.email.value = response.email;
            document.userForm.is2FaEnabled.value = response.is2FaEnabled;
            document.userForm.is2FaEnabled.checked = response.is2FaEnabled;
            user_id = response.user_id;
            userService.selectedRoles(response.roles);
        }, function(error) {
            common.infoMessage(error.responseJSON['error-message'], 'error');
        });

        $("#userTabData").hide();
        $("#userForm").show();
    }
    
    /*** Delete user ***/
    function removeUser() {
        $("#confirmModal").modal('hide');
        var id = document.getElementById("inputId").value;
        if (id != undefined) {
        	 $("#loading").css("display", "block");
            $.ajax({
                url: './user/' + id,
                type: 'DELETE'
            }).then(function(response) {
                $("#user-details").html('');
                userService.getUsers().then(function(allUsers) {
                    if (allUsers && allUsers.length) {                        
                        userService.showAllUsers(allUsers);
                    }
                });
                common.infoMessage('User removed successfully.', 'success');
            }, function(error) {
                $("#loading").css("display", "none");
                common.infoMessage(error.responseJSON['error-auxiliary-message'], 'error');
            });
        }
    }
    
    /*** Active/DeActive user ***/
    function activeDeactiveUser(id, type) {
        $("#confirmModal").modal('hide');
        var toogleType;
        var data;
        if (type == 1) {
            data = {
                "status": "active"
            };
        } else {
            data = {
                "status": "inactive"
            };
        }
        $("#loading").css("display", "block");
        $.ajax({
            url: './user/' + id,
            contentType: 'application/json',
            dataType: "json",
            type: 'PUT',
            data: JSON.stringify(data)
        }).then(function(response) {
            $("#user-details").html('');
            userService.getUsers().then(function(allUsers) {
                if (allUsers && allUsers.length) {                   
                    userService.showAllUsers(allUsers);
                }
            });
            common.infoMessage('User status changed successfully!', 'success');
        }, function(error) {
            $("#loading").css("display", "none");
            common.infoMessage(error.responseJSON['error-auxiliary-message'], 'error');
        });
    }
    
    /*** Reset 2FA ***/
    function reset2fa() {
        $("#confirmModal").modal('hide');
        var id = document.getElementById("inputId").value;
        if (id != undefined) {
        	$("#loading").css("display", "block");
            $.ajax({
                url: './user/reset2fa/' + id,
                contentType: 'application/json',
                dataType: "json",
                type: 'PUT'
            }).then(function(response) {
                $("#user-details").html('');
                userService.getUsers().then(function(allUsers) {
                    if (allUsers && allUsers.length) {                       
                        userService.showAllUsers(allUsers);
                    }
                });
                common.infoMessage('User 2FA reset successfully!', 'success');
            }, function(error) {
                $("#loading").css("display", "none");
                common.infoMessage(error.responseJSON['error-auxiliary-message'], 'error');
            });
        }
    }


    /*** Show all users ***/
    function showAllUsers(response) {
        var tableRowData = [];
        if (!response || response.length == 0) {
            response = []
            common.infoMessage('No Users Available', 'info');
        }
        for (var i = 0; i < response.length; i++) {
            var status = 1;
            var f_btn = 'fa-toggle-off';
            var toogle_text = 'Active';
            if (response[i].status == 'Active') {
                status = 0;
                f_btn = 'fa-toggle-on';
                toogle_text = 'Inactive';
            }
            var tableCol1 = "<td class='divTableCell' title ='" + ((response[i].email === "" || response[i].email == undefined) ? "-" : response[i].email) + "'><span title='"+response[i].email+"'>" + ((response[i].email === "" || response[i].email == undefined) ? "-" : response[i].email) + "</span></td>";
            var tableCol2 = "<td class='divTableCell' title ='" + ((response[i].name === "" || response[i].name == undefined) ? "-" : response[i].name) + "'><span title='"+response[i].name+"'>" + ((response[i].name === "" || response[i].name == undefined) ? "-" : response[i].name) + "</span></td>";
            var tableCol3 = "<td class='divTableCell' title ='" + ((response[i].roles === "" || response[i].roles == undefined) ? "-" : response[i].roles) + "'><span title='"+response[i].roles+"'>" + ((response[i].roles === "" || response[i].roles == undefined) ? "-" : response[i].roles) + "</span></td>";
            var tableCol4 ='';
            if (response[i].user_id !== USER_SESSION.userId) {
            tableCol4 = "<td class='divTableCell' >" +
                "<i title='Edit'class='fa icon-edit cursor-pointer' onclick='userService.editUser(" + response[i].user_id + ")' permission='um_user_edit'></i>";
            }
            if (response[i].user_id !== USER_SESSION.userId) {
                tableCol4 += "<i title='Delete' onclick='openConfirmationModal(" + response[i].user_id + ", \"deleteUser\")' class='fa icon-trash cursor-pointer' permission='um_user_delete'></i>" +
                    "<i title='" + toogle_text + "' onclick='openConfirmationModal(" + response[i].user_id + ",\"activeUser\"," + status + ")' class='fa cursor-pointer " + f_btn + "' permission='um_user_activate'></i>";
            }
            tableCol4 += "<span title='Reset Password' onclick='openConfirmationModal(" + response[i].user_id + ", \"resetPassword\")' class='fa-passwd-reset fa-stack cursor-pointer' permission='um_user_reset'><i title='Reset Password' class='fa fa-undo fa-stack-2x'></i><i class='fa fa-lock fa-stack-1x'></i></span>" +
                "<span title='Reset Password By Admin' onclick='userService.resetPassword(" + response[i].user_id + ",true)' class='fa-passwd-reset fa-stack cursor-pointer' permission='um_user_reset_admin'><i title='Reset Password By Admin' class='fa fa-key fa-key-2x'></i></span>";
            if (response[i].is2FaEnabled) {
            	tableCol4 += "<span title='Reset 2FA' onclick='openConfirmationModal(" + response[i].user_id + ",\"reset2fa\")' class='fa-passwd-reset fa-stack cursor-pointer' permission='um_user_reset2fa'><i title='Reset 2FA' class='fa fa-undo fa-stack-2x'></i></span>";
            }
            tableCol4 +=  "</td>";


            tableRowData.push([tableCol1, tableCol2, tableCol3, tableCol4]);
            //$("#userTable").append(tableRow);

            if (response[i].state && (response[i].state == "ACTIVATED")) {
                $("#div_" + (i + 1)).addClass('up-state');
            } else {
                $("#div_" + (i + 1)).addClass('down-state');
            }
        }
        var tableVar = $('#userTable').DataTable({
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
                    sWidth: '20%',
                    sClass: 'divTableCell'
                },
                {
                    sWidth: '30%',
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
        $("#loading").css("display", "none");
        $('#userForm').hide();
        $('#userTabData').show();
        $('#userTable').show();
        hasPermission();
    }
    
    /*** Selected Role ***/
    function selectedRoles(roles) {
        var selectRoleElements = "";
        roleService.getRoles().then(function(roleRes) {
            roleRes.forEach((element) => {
                if (roles) {
                    var roleFound = roles.filter(function(elm) {
                        return elm === element.name;
                    });
                    if (roleFound.length > 0) {
                        selectRoleElements += "<option value='" + element.role_id + "' selected>" + element.name + "</option>";
                    } else {
                        selectRoleElements += "<option value='" + element.role_id + "' >" + element.name + "</option>";
                    }
                } else {
                    selectRoleElements += "<option value='" + element.role_id + "' >" + element.name + "</option>";
                }
            });

            $("#userRoleToSelect").html(selectRoleElements);
            $('#userRoleToSelect').select2({
                width: "100%",
                placeholder: "Select Role"
            });
            $("input.select2-search__field").addClass("form-control");
            $("#loading").css("display", "none");
        }, function(err) {
            common.infoMessage(err.responseJSON['error-message'], 'error');
        });
    }
    /*** Role to select ***/
    function rolesToSelect(id) {
        roleService.getRoles().then(function(roleRes) {
            var selectRoleElements = '';
            if (id != undefined) {
                userService.getUser(id).then(function(userRes) {
                    var selectRoleElements = "";
                    roleRes.forEach((element, index, array) => {
                        if (userRes.roles) {
                            if (index < userRes.roles.length && (userRes.roles[index] == element.name)) {
                                selectRoleElements += "<option value='" + element.role_id + "' selected>" + element.name + "</option>";
                            } else {
                                selectRoleElements += "<option value='" + element.role_id + "'>" + element.name + "</option>";
                            }
                        } else {
                            selectRoleElements += "<option value='" + element.role_id + "'>" + element.name + "</option>";
                        }                        
                    });
                    $("#userRoleToSelect").html(selectRoleElements);
                    $('#userRoleToSelect').select2({
                        width: "100%",
                        placeholder: "Select Role"
                    });
                    $("input.select2-search__field").addClass("form-control");
                    $("#loading").css("display", "none");
                }, function(error) {
                	$("#loading").css("display", "none");
                    common.infoMessage(error.responseJSON['error-message'], 'error');
                });
            } else {
                roleRes.forEach((element, index, array) => {
                    selectRoleElements += "<option value='" + element.role_id + "'>" + element.name + "</option>";                    
                });
                $("#userRoleToSelect").html(selectRoleElements);
                $('#userRoleToSelect').select2({
                    width: "100%",
                    placeholder: "Select Role"
                });
                $("#loading").css("display", "none");
                $("input.select2-search__field").addClass("form-control");
            }
        }, function(error) {
        	$("#loading").css("display", "none");
            common.infoMessage(error.responseJSON['error-message'], 'error');
        });
    }
    
    /*** Reset Password ***/
    function resetPassword(id, isByAdmin) {
        var apiUrl = '';
        if (isByAdmin) {
            apiUrl = './user/admin/resetpassword/' + id;
        } else {
            apiUrl = './user/resetpassword/' + id;
        }
        $("#confirmModal").modal('hide');
        var id = document.getElementById("inputId").value;
        if (id != undefined) {
        	 $("#loading").css("display", "block");
            var userData = {
                'user_id': id
            }
            $.ajax({
                url: apiUrl,
                contentType: 'application/json',
                dataType: "json",
                type: 'GET',
                //data: JSON.stringify(userData),
                success: function(response) {
                    if (isByAdmin) {
                        common.infoMessage('Password Reset successfully!', 'success');
                        if (response && response.password) {
                        	$("#loading").css("display", "none");
                            var msg = 'User password is ' + response.password;
                            doConfirmationModal('User New Password', msg, '', '');
                            //alert('User password is '+response.password)
                        }
                    } else {
                    	$("#loading").css("display", "none");
                        common.infoMessage('Reset Password email sent successfully!', 'success');
                    }

                },
                error: function(err) {
                    if (err.status != '200') {
                    	$("#loading").css("display", "none");
                        common.infoMessage(error.responseJSON['error-message'], 'error');
                    }
                }
            })
        }
    }
    /*** Return function ***/
    return {
        getUsers: getUsers,
        addUser: addUser,
        getUser: getUser,
        editUser: editUser,
        removeUser: removeUser,
        activeDeactiveUser: activeDeactiveUser,
        showAllUsers: showAllUsers,
        rolesToSelect: rolesToSelect,
        selectedRoles: selectedRoles,
        resetPassword: resetPassword,
        reset2fa: reset2fa
    }
})()