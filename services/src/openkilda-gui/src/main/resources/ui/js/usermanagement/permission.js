class Permission {
    constructor(name, desc) {
        this.name = name;
        this.description = desc;
        this.isEditable = true;
    }
}

var permissionService = (function() {
    var permission_id;
    
    /*** Get Permissions ***/
    function getPermissions() {
        return $.ajax({
            url: './permission',
            type: 'GET',
            dataType: "json"
        });
    }
    
    /*** Add Permission ***/
    function addPermission($event) {
        $event.preventDefault();
        var name = document.permissionForm.pname.value;
        var desc = document.permissionForm.description.value;
        var permission_id = $("#permission_id").val();

        if (name == "" || name == null) {
            $('#pnameError').show();
            $('input[name="pname"').addClass("errorInput");
        }
       if ((name == "" || name == null)) {
            return false;
        } 
        var $form = $("#addPermissionForm");
        var data = getFormData($form);
        var permissionData = new Permission(data.pname, data.description);
        $("#loading").css("display", "block");
        if (permission_id) {         	
            $.ajax({
                url: './permission/' + permission_id,
                contentType: 'application/json',
                dataType: "json",
                type: 'PUT',
                data: JSON.stringify(permissionData)
            }).then(function(response) {
                $("#permission-details").html('');
                permissionService.getPermissions().then(function(allPermissions) {
                    if (allPermissions && allPermissions.length) {                    	
                        permissionService.showAllPermissions(allPermissions);
                    }else{
                    	 permissionService.showAllPermissions(allPermissions);
                    }
                });
                common.infoMessage('Permission updated successfully.', 'success');
            }, function(error) {
                $("#loading").css("display", "none");
                common.infoMessage(error.responseJSON['error-message'], 'error');
               
            });
        } else {         	
            $.ajax({
                url: './permission',
                contentType: 'application/json',
                dataType: "json",
                type: 'POST',
                data: JSON.stringify(permissionData)
            }).then(function(response) {
                $("#permission-details").html('');
                permissionService.getPermissions().then(function(allPermissions) {
                    if (allPermissions && allPermissions.length) {                       
                        permissionService.showAllPermissions(allPermissions);
                    }else{
                    	 permissionService.showAllPermissions(allPermissions);
                    }
                });
                common.infoMessage('Permission added successfully.', 'success');
            }, function(error) {
                $("#loading").css("display", "none");
                common.infoMessage(error.responseJSON['error-message'], 'error');
                
            });
        }
    }
    
    /*** Get Permission ***/
    function getPermission(id) {
        return $.ajax({
            url: './permission/' + id,
            type: 'GET',
            dataType: "json"
        });
    }
    
    /*** Show all Permission list ***/
    function showAllPermissions(response) {
        var tableRowData = [];
        $("#loading").css("display", "none");
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
            var tableCol1 = "<td class='divTableCell' title ='" + ((response[i].name === "" || response[i].name == undefined) ? "-" : response[i].name) + "'><span title='"+response[i].name+"'>" + ((response[i].name === "" || response[i].name == undefined) ? "-" : response[i].name) + "</span></td>";
            var tableCol2 = "<td class='divTableCell' title ='" + ((response[i].description === "" || response[i].description == undefined) ? "-" : response[i].description) + "'><span title='"+response[i].description+"'>" + ((response[i].description === "" || response[i].description == undefined) ? "-" : response[i].description) + "</span></td>";
            var tableCol3 = "<td class='divTableCell'>";
            if (response[i].isEditable && response[i].isEditable == true) {
                tableCol3 += "<i title='Edit' class='fa icon-edit' onclick='permissionService.editPermission(" + response[i].permission_id + ")' permission='um_permission_edit'></i>";
            }
            tableCol3 += "<i title='Assign Roles' class='fa icon-group' onClick='permissionService.assignRolesByPermissionId(" + response[i].permission_id + ")' permission='um_assign_permission_to_roles'></i>" +
                "<i title='View Roles' class='fa icon-user' onClick='permissionService.viewRolesByPermissionId(" + response[i].permission_id + ")' permission='um_permission_view_roles'></i>" +
                "<i title='" + toogle_text + "' onclick='openConfirmationModal(" + response[i].permission_id + ",\"activePermission\"," + status + ")' class='fa cursor-pointer " + f_btn + "' permission='um_permission_activate'></i>";
            if (response[i].isEditable && response[i].isEditable == true) {
               // tableCol3 += "<i title='Delete' class='fa icon-trash' onclick='openConfirmationModal(" + response[i].permission_id + ", \"deletePermission\")' permission='um_permission_delete'></i>";
            }
            tableCol3 += "</td>";
            //$("#permissionTable").append(tableRow);
            tableRowData.push([tableCol1, tableCol2, tableCol3]);
            if (response[i].state && (response[i].state == "ACTIVATED")) {
                $("#div_" + (i + 1)).addClass('up-state');
            } else {
                $("#div_" + (i + 1)).addClass('down-state');
            }
        }
        var tableVar = $('#permissionTable').DataTable({
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
        $('#permissionForm').hide();
        $('#permissionTabData').show();
        $('#permissionTable').show();
        hasPermission();
    }

    /* Role to select used in Assign role action */
    function rolesToSelect(id) {
        roleService.getRoles().then(function(roleRes) {
            var selectPermissionElements = selectedPermission = '';
            if (id > 0 && id != undefined) {
                permissionService.getPermission(id).then(function(permissionRes) {
                    var rolesArray = [];
                    if (permissionRes.roles) {
                        for (var i = 0; i < permissionRes.roles.length; i++) {
                            rolesArray.push(permissionRes.roles[i].name);
                        }
                    }
                    roleRes.forEach((element, index, array) => {
                        if (permissionRes.roles) {

                            if (jQuery.inArray(element.name, rolesArray) != -1) {
                                selectedPermission = "selected";
                            } else {
                                selectedPermission = '';
                            }
                            selectPermissionElements += "<option value='" + element.role_id + "'  " + selectedPermission + ">" + element.name + "</option>";
                        } else {
                            selectPermissionElements += "<option value='" + element.role_id + "'>" + element.name + "</option>";
                        }
                    });
                    $("#permissionRoleSelect").html(selectPermissionElements);
                    $('#permissionRoleSelect').select2({
                        width: "100%",
                        placeholder: "Select Role"
                    });
                    $("input.select2-search__field").addClass("form-control");
                    $("#loading").css("display", "none");
                }, function(error) {
                	$("#loading").css("display", "none");
                    common.infoMessage(error.responseJSON['error-auxiliary-message'], 'error');
                });
            }
        }, function(err) {
            common.infoMessage(error.responseJSON['error-auxiliary-message'], 'error');
        });
    }

    /*** View Permission ***/
    function viewRolesByPermissionId(permissionId) {
    	$("#loading").css("display", "block");
        $("#permissionTabData").show();
        $("#permissionForm").hide();
        permissionService.getPermissionByIdWithRoles(permissionId).then(function(response) {
            if (response) {
                $("#permissionTabData").html('');

                var html;
                html = '<div class="viewLayout"><div class="row viewDetail">' +
                    '<label class="col-sm-2 col-form-label text-right">Name:</label>' +
                    '<div class="col-sm-6"><input type="text" class="form-control" value="' + response.name + '" disabled/></div>' +
                    '</div>' +
                    '<div class="row viewDetail">' +
                    '<label class="col-sm-2 col-form-label text-right">Description:</label>' +
                    '<div class="col-sm-6"><input type="text" class="form-control" value="' + response.description + '" disabled/></div>' +
                    '</div>' +
                    '<div class="row viewDetail">' +
                    '<label class="col-sm-2 col-form-label text-right">Roles:</label>';
                if (response.roles.length > 0) {
                    var roles = [];
                    response.roles.forEach(function(role) {
                        roles.push(role.name);
                    });
                    html += '<div class="col-sm-6"><div class="viewRole permissionCol"><span class="badge">'+roles.join("</span> <span class='badge'>")+'</span></div></div>'

                } else {
                    html += '<div class="col-sm-6"><input type="text" class="form-control" value= No roles assigned. disabled/></div>'
                }
                html += '</div><div class="row viewDetail"><label class="col-sm-2 col-form-label"></label><div class="col-sm-6 text-right"><a class="backLoadTabData btn kilda_btn" href="#permissionTab" data-toggle="tab" data-url="#permissionTab">Back</a></div></div></div>';
                $("#loading").css("display", "none");
                $("#permissionTabData").html(html);
            } else {
                // Error Handling
            	 $("#loading").css("display", "none");
                common.infoMessage(response['error-auxiliary-message'], 'error');
            }
        }, function(err) {
        	 $("#loading").css("display", "none");
            common.infoMessage(error.responseJSON['error-auxiliary-message'], 'error');
        });
    }
    
    /*** Assign Roles ***/
    function assignRolesByPermissionId(id) {
        /* Reset Form */
    	$("#loading").css("display", "block");
        $("#permissionForm").find(".errorInput").removeClass("errorInput");
        $("#permissionForm").find(".error").hide();

        $("#permissionTabData").hide();
        $("#permissionForm").show();
        $("#addPermissionForm").attr("onsubmit", "permissionService.updateRolesToPermission(event)");
        $("#pname, #pdescription").attr("readonly", true);
        $("#padmin").hide();
        $("#permissionRoleAssign").show();
        $("#permission_id").parent().remove(); //remove existing hidden permission id input
        $("#addPermissionForm").append("<div class='form-group row'><input type='hidden' id='permission_id' value='" + id + "' /></div>"); //add hidden permission id input
        $("#addPermissionForm #submitBtn").text("Assign Roles");
        permissionService.getPermission(id).then(function(response) {        	
            document.permissionForm.pname.value = response.name;
            document.permissionForm.description.value = response.description;
            permission_id = response.permission_id;
            permissionService.rolesToSelect(permission_id);            
        }, function(err) {
        	$("#loading").css("display", "none");
            common.infoMessage(error.responseJSON['error-auxiliary-message'], 'error');
        });
    }

    function getPermissionByIdWithRoles(permissionId) {
        return $.get('./role/permission/' + permissionId, function(response) {
            this.data = response;
            return this.data;
        });
    }
    
    /*** Edit Permission ***/
    function editPermission(id) {
        /* Reset Form */
    	$("#loading").css("display", "block");
        $("#permissionForm").find(".errorInput").removeClass("errorInput");
        $("#permissionForm").find(".error").hide();

        $("#permissionTabData").hide();
        $("#permissionForm").show();
        $("#addPermissionForm").attr("onsubmit", "permissionService.addPermission(event)");
        $("#pname, #pdescription").attr("readonly", false);
        $("#padmin").show();
        $("#permissionRoleAssign").hide();
        $("#permission_id").parent().remove(); //remove existing hidden permission id input
        $("#addPermissionForm").append("<div class='form-group row'><input type='hidden' id='permission_id' value='" + id + "' /></div>"); //add hidden permission id input
        $("#addPermissionForm #submitBtn").text("Update Permission");
        permissionService.getPermission(id).then(function(response) {
        	$("#loading").css("display", "none");
            document.permissionForm.pname.value = response.name;
            document.permissionForm.description.value = response.description;
            $('#chkAdminPermission').prop('checked', response.isAdminPermission);
            permission_id = response.permission_id;
        }, function(err) {
        	$("#loading").css("display", "none");
            common.infoMessage(error.responseJSON['error-auxiliary-message'], 'error');
        });
    }
    
    /*** Update role to Permission ***/
    function updateRolesToPermission($event) {
        $event.preventDefault();

        var selectedRoles = [];
        selectedRole = $('select#permissionRoleSelect').val();
        selectedRole.forEach(function(element, index) {
            selectedRoles[index] = {
                'role_id': element
            };
        });

        
        var permissionData = {
            'roles': selectedRoles
        };

        if (permission_id) {
        	 $("#loading").css("display", "block");
            $.ajax({
                url: './role/permission/' + permission_id,
                contentType: 'application/json',
                dataType: "json",
                type: 'PUT',
                data: JSON.stringify(permissionData)
            }).then(function(response) {
                $("#permission-details").html('');
                permissionService.getPermissions().then(function(allPermissions) {
                    if (allPermissions && allPermissions.length) {                        
                        permissionService.showAllPermissions(allPermissions);
                    }else{
                    	 permissionService.showAllPermissions(allPermissions);
                    }
                });
                common.infoMessage('Role assigned successfully.', 'success');
            }, function(error) {
            	$("#loading").css("display", "none");
                common.infoMessage(error.responseJSON['error-auxiliary-message'], 'error');
            });
        }
    }
    
    /*** Delete Permission ***/
    function removePermission() {
        $("#confirmModal").modal('hide');
        var id = document.getElementById("inputId").value;
        if (id != undefined) {
        	$("#loading").css("display", "block");
            $.ajax({
                url: './permission/' + id,
                type: 'DELETE'
            }).then(function(response) {
                $("#permission-details").html('');
                permissionService.getPermissions().then(function(allPermissions) {
                    if (allPermissions && allPermissions.length) {                        
                        permissionService.showAllPermissions(allPermissions);
                    }else{
                    	 permissionService.showAllPermissions(allPermissions);
                    }
                });
                common.infoMessage('Permission removed successfully.', 'success');
            }, function(error) {
                $("#loading").css("display", "none");
                common.infoMessage(error.responseJSON['error-auxiliary-message'], 'error');
            });
        }
    }
    
    /*** Active/DeActive Permission ***/
    function activeDeactivePermission(permission_id, type) {
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
            url: './permission/' + permission_id,
            contentType: 'application/json',
            dataType: "json",
            type: 'PUT',
            data: JSON.stringify(data)
        }).then(function(response) {
            $("#permission-details").html('');
            permissionService.getPermissions().then(function(allPermissions) {
                if (allPermissions && allPermissions.length) {                    
                    permissionService.showAllPermissions(allPermissions);
                }else{
                	 permissionService.showAllPermissions(allPermissions);
                }
            });

            if (type == 1) {
                common.infoMessage('Permission activated successfully.', 'success');
            } else {
                common.infoMessage('Permission de-activated successfully.', 'success');
            }
        }, function(error) {
            $("#loading").css("display", "none");
            common.infoMessage(error.responseJSON['error-auxiliary-message'], 'error');
        });
    }
    /*** function return ***/
    return {
        getPermissions: getPermissions,
        addPermission: addPermission,
        getPermission: getPermission,
        showAllPermissions: showAllPermissions,
        rolesToSelect: rolesToSelect,
        assignRolesByPermissionId: assignRolesByPermissionId,
        viewRolesByPermissionId: viewRolesByPermissionId,
        editPermission: editPermission,
        removePermission: removePermission,
        activeDeactivePermission: activeDeactivePermission,
        getPermissionByIdWithRoles: getPermissionByIdWithRoles,
        updateRolesToPermission: updateRolesToPermission
    }
})()