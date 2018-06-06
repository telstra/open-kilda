/*User Management controller*/
function stopParentDelegation(e) {
    e.stopPropagation();
}
function reloadTabData(link, self, activeLink) {
    if (link == "#userTab") {
    	$(".load-text").text('');
    	$("#loading").css("display", "block");
        $(link).load('templates/userManagement/userList.html');
        $("#user-details").html('');
        userService.getUsers().then(function(userData) {
            if (userData && userData.length) {               
                userService.showAllUsers(userData);
                //hasPermission();
            }
        });
        activeLink = 'user';
    } else if (link == "#roleTab") {
    	$(".load-text").text('');
    	$("#loading").css("display", "block");
        $(link).load('templates/userManagement/roleList.html');
        $("#role-details").html('');
        roleService.getRoles().then(function(roleData) {
            if (roleData && roleData.length) {                
                roleService.showAllRoles(roleData);
                //hasPermission();
            }
        });
        activeLink = 'role';
    } else if (link == "#permissionTab") {
    	$(".load-text").text('');
    	$("#loading").css("display", "block");
        $(link).load('templates/userManagement/permissionList.html');
        $("#permission-details").html('');
        permissionService.getPermissions().then(function(permissionData) {
            if (permissionData && permissionData.length) {                
                permissionService.showAllPermissions(permissionData);
                // hasPermission();
            }
        });
        activeLink = 'permission';
    }

}
$(document).ready(function() {	    
	
    $("#userTab").load('templates/userManagement/userList.html');
    $("#user-details").html('');
    userService.getUsers().then(function(userData) {
        if (userData && userData.length) {           
            userService.showAllUsers(userData);
            //hasPermission();
        }
    });
    // add user model
    $(document).on('click', "#addUserBtn", function() {
        /* Reset Form */
    	$("#loading").css("display", "block");
        $("#addUserForm").find(".errorInput").removeClass("errorInput");
        $("#addUserForm").find(".error").hide();
        $("#user_id").parent().remove(); //remove existing hidden permission id input
        $("#addUserForm #submitBtn").text("Add User");
        $("#addUserForm #email").removeAttr("disabled");
        $("input[name=email]").focus();
        //Clear input fields
        document.userForm.name.value = '';
        document.userForm.email.value = '';
        $("#userTabData").hide();
        $("#userForm").show();
        $("input[name=email]").focus();
        userService.rolesToSelect();
        
    });

    // add permission model
    $(document).on('click', "#addPermissionBtn", function() {
        /* Reset Form */
    	$("#loading").css("display", "block");
        $("#permissionForm").find(".errorInput").removeClass("errorInput");
        $("#permissionForm").find(".error").hide();

        //Clear input fields
        document.permissionForm.pname.value = '';
        document.permissionForm.description.value = '';
        $("#permission_id").parent().remove(); //remove existing hidden permission id input
        $("#addPermissionForm #submitBtn").text("Add Permission");
        $("#loading").css("display", "none");
        $("#permissionTabData").hide();
        $("#permissionForm").show();
        $("#addPermissionForm").attr("onsubmit", "permissionService.addPermission(event)");
        $("#pname, #pdescription").attr("readonly", false);
        $("input[name=pname]").focus();
        $("#padmin").show();
        $('#chkAdminPermission').prop('checked', false);
        $("#permissionRoleAssign").hide();
        
    });

    // add role model
    $(document).on('click', "#addRoleBtn", function() {
        /*reset form*/
    	$("#loading").css("display", "block");
        $("#addRoleForm").find(".errorInput").removeClass("errorInput");
        $("#addRoleForm").find(".error").hide();

        /*Clear input fields*/

        document.roleForm.rname.value = '';
        document.roleForm.description.value = '';
        $("#role_id").parent().remove(); //remove existing hidden permission id inpu
        $("#addRoleForm #add_update_btn").text("Add Role");

        roleService.permissionsToSelect();
        $("#roleTabData").hide();
        $("#roleForm").show();
        $("#addRoleForm").attr("onsubmit", "roleService.addRole(event)");
        $("#roleUsersAssign").hide();  
        $("input[name=rname]").focus();        
        $("#rolePermissionAssign").show();
        $("#rname, #rdescription").attr("readonly", false);
    });

    $(document).on('click', "#closeUserForm", function() {
        $("#userForm").hide();
        $("#userTabData").show();
    });
    $(document).on('click', "#closePermissionForm", function() {
        $("#permissionForm").hide();
        $("#permissionTabData").show();
    });
    $(document).on('click', "#closeRoleForm", function() {
        $("#roleForm").hide();
        $("#roleTabData").show();
    });
    $('a[data-toggle="tab"]').on('shown.bs.tab', function(e) {
        var link = $(this).attr("href");
        var self = $(this);
        var activeLink;
        reloadTabData(link, self, activeLink);
        $('ul.usermgt_breadcrumb li.tab').html('<i class="fa icon-double-angle-right"></i><a class="loadTabData" href="#' + activeLink + 'Tab" data-toggle="tab" data-url="#' + activeLink + 'Tab">' + self.text() + '</a>');
    });
    $(document).on('click', ".backLoadTabData", function() {
        var link = $(this).attr("href");
        var self = $(this);
        var activeLink;
        reloadTabData(link, self, activeLink);
    });
    //Active Breadcrumb Tab
    $(document).on('click', ".activeBreadcrumbTab", function() {

        var activeTab = $(this).attr('data-tab');

        if (activeTab == 'userManagement') {
            activeTab = 'user';
            var activeTabText = 'users';
            $('ul.usermgt_breadcrumb li.tab').html('<i class="fa icon-double-angle-right"></i><a href="#" class="activeBreadcrumbTab" data-tab="' + activeTab + '">' + capitalize(activeTabText) + '</a>');

            //Tabs active class add/remove
            $(".user-management-tabs .tab-pane").removeClass('active');
            $("#userDetails ul.nav-tabs li").removeClass('active');
            $('#userDetails ul.nav-tabs li a:contains("Users")').parent().addClass('active');
        }

        $("#" + activeTab + "Tab").addClass('active');
        $("#" + activeTab + "TabData").show();
        $("#" + activeTab + "Form").hide();
        return false;
    });
    /****All select change event *****/
    $(document).on('change', "#userRoleToSelect, #roleUserSelect", function() {    	
    	var selectId = $(this).attr('id');
    	if($(this).val().length > 0)
    	{
    		$('#'+selectId+'Error').hide();	
    		$('.select2-selection ').removeClass("errorInput");	
    	}
    	else
    	{
    		$('#'+selectId+'Error').show();	
    		$('.select2-selection ').addClass("errorInput");
    	}	
    	
    });
   
});

//Function to convert string into capitalize format
function capitalize(str) {
    strVal = '';
    str = str.split(' ');
    for (var chr = 0; chr < str.length; chr++) {
        strVal += str[chr].substring(0, 1).toUpperCase() + str[chr].substring(1, str[chr].length) + ' '
    }
    return strVal
}

function userSearch(idname, $event) {
    $event.stopPropagation();
    if ($('#' + idname + '.heading_search_box').is(":visible")) {
        $('#' + idname + '.heading_search_box').css('display', 'none');
    } else {
        $('#' + idname + '.heading_search_box').css('display', 'inline-block');
    }
}

//this to get formData into JSON
function getFormData($form) {
    var unindexed_array = $form.serializeArray();
    var indexed_array = {};

    $.map(unindexed_array, function(n, i) {
        indexed_array[n['name']] = n['value'];
    });

    return indexed_array;
}

//this to validation error on form
function removeError(elem) {
    var id = elem.name;
    if ((elem.value).trim() != '') {
        $("#" + id + "Error").hide();
        $('input[name="' + id + '"').removeClass("errorInput"); // Remove Error border
        $('textarea[name="' + id + '"').removeClass("errorInput"); // Remove Error border
    } else {
        $("#" + id + "Error").show();
        $('input[name="' + id + '"').addClass("errorInput"); // Add Error border
        $('textarea[name="' + id + '"').addClass("errorInput"); // Remove Error border
    }
}

function removeEmailError(elem) {
    var id = elem.name;
    if ((elem.value != '') && (!validateEmail(elem.value))) {
        $("#" + id + "Invalid").show();
        $('input[name="' + id + '"').addClass("errorInput"); // Add Error border
    } else {
        $("#" + id + "Invalid").hide();
        $('input[name="' + id + '"').removeClass("errorInput"); // Remove Error border
    }
}

function removePasswordError(elem) {
    var id = elem.name;
    if ((elem.value != '') && (!validatePassword(elem.value))) {
        $("#" + id + "Invalid").show();
    } else {
        $("#" + id + "Invalid").hide();
    }
}

function validatePassword(pwd) {
    var pass = /^(?=.*?[A-Z])(?=.*?[a-z])(?=.*?[0-9])(?=.*[!@#\$%\^&\*\_]).{8,}$/;
    return pass.test(String(pwd));
}

function validateEmail(email) {
    var re = /^([a-zA-Z0-9_\.\-])+\@(([a-zA-Z0-9\-])+\.)+([a-zA-Z0-9]{2,4})+$/;
    return re.test(email);
}

function userManagementSearch(idname, $event) {
    $event.stopPropagation();
    if ($('#' + idname + '.heading_search_box').is(":visible")) {
        $('#' + idname + '.heading_search_box').css('display', 'none');
    } else {
        $('#' + idname + '.heading_search_box').css('display', 'inline-block');
        $('#' + idname + '.heading_search_box').focus();
    }
}

function openConfirmationModal(id, type, status) {
    $("#confirmModal").modal('show');
    document.getElementById("inputId").value = "";
    document.getElementById("inputId").value = id;
    if (type == "deleteUser") {
        $('#confirmModal').find('.modal-body').text('Are you sure you want to delete user?');
        $('#confirmModal').find('.modal-footer').html("");
        $('#confirmModal').find('.modal-footer').append('<button type="button" class="btn btn-default" data-dismiss="modal">No</button><button type="button" class="btn kilda_btn" onclick="userService.removeUser()">Yes</button>');
    } else if (type == 'resetPassword') {
        $('#confirmModal').find('.modal-body').text('Are you sure you want to reset password?');
        $('#confirmModal').find('.modal-footer').html("");
        $('#confirmModal').find('.modal-footer').append('<button type="button" class="btn btn-default" data-dismiss="modal">No</button><button type="button" class="btn kilda_btn" onclick="userService.resetPassword('+id+')">Yes</button>');
    } else if (type == 'reset2fa') {
        $('#confirmModal').find('.modal-body').text('Are you sure you want to reset 2FA?');
        $('#confirmModal').find('.modal-footer').html("");
        $('#confirmModal').find('.modal-footer').append('<button type="button" class="btn btn-default" data-dismiss="modal">No</button><button type="button" class="btn kilda_btn" onclick="userService.reset2fa()">Yes</button>');
    } else if (type == 'deleteRole') {
        $('#confirmModal').find('.modal-body').text('Are you sure you want to delete role?');
        $('#confirmModal').find('.modal-footer').html("");
        $('#confirmModal').find('.modal-footer').append('<button type="button" class="btn btn-default" data-dismiss="modal">No</button><button type="button" class="btn kilda_btn" onclick="roleService.removeRole()">Yes</button>');
    } else if (type == 'deletePermission') {
        $('#confirmModal').find('.modal-body').text('Are you sure you want to delete permission?');
        $('#confirmModal').find('.modal-footer').html("");
        $('#confirmModal').find('.modal-footer').append('<button type="button" class="btn btn-default" data-dismiss="modal">No</button><button type="button" class="btn kilda_btn" onclick="permissionService.removePermission()">Yes</button>');
    } else if (type == "activePermission") {
        var toogleType;
        if (status == 1) {
            toogleType = 'Active';
        } else {
            toogleType = 'Inactive';
        }
        $('#confirmModal').find('.modal-body').text("Are you sure you want to " + toogleType + " this permission ?");
        $('#confirmModal').find('.modal-footer').html("");
        $('#confirmModal').find('.modal-footer').append('<button type="button" class="btn btn-default" data-dismiss="modal">No</button><button type="button" class="btn kilda_btn" onclick="permissionService.activeDeactivePermission(' + id + ',' + status + ')">Yes</button>');

    } else if (type == 'activeUser') {
        var toogleType;
        if (status == 1) {
            toogleType = 'Active';
        } else {
            toogleType = 'Inactive';
        }
        $('#confirmModal').find('.modal-body').text("Are you sure you want to " + toogleType + " this user ?");
        $('#confirmModal').find('.modal-footer').html("");
        $('#confirmModal').find('.modal-footer').append('<button type="button" class="btn btn-default" data-dismiss="modal">No</button><button type="button" class="btn kilda_btn" onclick="userService.activeDeactiveUser(' + id + ',' + status + ')">Yes</button>');
    }
}