INSERT INTO "VERSION" (Version_ID, Version_Number, Version_Deployment_Date)
VALUES (1, 1, CURRENT_TIMESTAMP);


INSERT  INTO "KILDA_STATUS" (status_id, STATUS_CODE, STATUS) VALUES 
	(1, 'ACT', 'Active'),
	(2, 'INA', 'Inactive');
	
INSERT INTO "KILDA_USER" (USER_ID, Username, Name, Password, email, Login_Time, Logout_Time, Active_Flag, Is_Authorized, is_two_fa_enabled, two_fa_key, is_two_fa_configured, CREATED_BY, CREATED_DATE, UPDATED_BY, UPDATED_DATE,STATUS_ID) VALUES 
	(1, 'admin', 'Admin', '$2a$11$/PHW3eqqJkN2SDbrQhu44eYQkOPIMmoclx5eg8MeTk3tbay6hsVou', '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, true, true, false, null, false, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP,1);
	
INSERT INTO "KILDA_ROLE" (ROLE_ID, ROLE,STATUS_ID, CREATED_BY, CREATED_DATE, UPDATED_BY, UPDATED_DATE,DESCRIPTION) VALUES 
	(1, 'kilda_user',1,1,CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP,'Kilda User'),
	(2, 'kilda_admin',1,1,CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP,'Kilda Admin');

INSERT INTO "KILDA_PERMISSION" (PERMISSION_ID, PERMISSION, IS_EDITABLE, IS_ADMIN_PERMISSION, STATUS_ID, CREATED_BY, CREATED_DATE, UPDATED_BY, UPDATED_DATE,DESCRIPTION) VALUES 
	(1, 'menu_topology', false, false, 1, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'Permission for topology menu'),
	(2, 'menu_flows', false, false, 1, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'Permission for flows menu'),
	(3, 'menu_isl', false, false, 1, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'Permission for isl menu'),
	(4, 'menu_switches', false, false, 1, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'Permission for switches menu'),
	(5, 'menu_user_management', false, false, 1, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'Permission for user management menu'),
	(6, 'um_role', false, false, 1, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'Permission for user management role tab'),
	(7, 'um_permission', false, false, 1, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'Permission for user management permssions tab'),
	(8, 'um_user_add', false, false, 1, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'Permission for user management -> add user'),
	(9, 'um_user_edit', false, false, 1, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'Permission for user management -> edit user'),
	(10, 'um_user_delete', false, false, 1, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'Permission for user management -> delete user'),
	(11, 'um_user_activate', false, false, 1, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'Permission for user management -> activate/deactivate User'),
	(12, 'um_user_reset', false, false, 1, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'Permission for user management -> reset password'),
	(13, 'um_user_reset_admin', false, true, 1, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'Permission for user management -> reset password (admin)'),
	(14, 'um_user_reset2fa',  false, false, 1, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'Permission for user management -> Reset 2 fa'),
	(15, 'um_role_add', false, false, 1, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'Permission for user management -> add role'),
	(16, 'um_role_edit', false, false, 1, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'Permission for user management -> edit role'),
	(17, 'um_role_delete', false, false, 1, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'Permission for user management -> delete role'),
	(18, 'um_role_view_users', false, false, 1, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'Permission for user management -> view users by role'),
	(19, 'um_permission_add', false, true, 1, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'Permission for user management -> add permission'),
	(20, 'um_permission_edit', false, true, 1, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'Permission for user management -> edit permission'),
	(21, 'um_permission_delete', false, true, 1, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'Permission for user management -> delete permission'),
	(22, 'um_permission_view_roles', false, false, 1, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'Permission for user management -> view roles by permission'),
	(23, 'um_role_assign_users', false, false, 1, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'Permission for user management -> assign users to role'),
	(24, 'um_permission_assign_roles', false, false, 1, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'Permission for user management -> assign permissions to user'),
	(25, 'um_permission_activate', false, true, 1, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'Permission for user management -> activate/deactivate permission'),
	(26, 'sw_permission_rules', false, false, 1, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'Permission for switches -> rules'),
	(27, 'fw_permission_reroute', false, false, 1, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'Permission for flow -> reroute'),
	(28, 'isl_permission_editcost', false, false, 1, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'Permission for isl -> edit cost'),
	(29, 'fw_permission_validate', false, false, 1, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'Permission for flow -> validate'),
	(30, 'um_assign_role_to_users', false, false, 1, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'Permission for user management -> assign role to users'),
	(31, 'um_assign_permission_to_roles', false, false, 1, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'Permission for user management -> assign permission to roles');
	
INSERT INTO "ROLE_PERMISSION" (ROLE_ID,PERMISSION_ID) VALUES 
	(1, 1),
	(1, 2),
	(1, 3),
	(1, 4),
	(2, 1),
	(2, 2),
	(2, 3),
	(2, 4),
	(2, 5),
	(2, 6),
	(2, 7),
	(2, 8),
	(2, 9),
	(2, 10),
	(2, 11),
	(2, 12),
	(2, 13),
	(2, 14),
	(2, 15),
	(2, 16),
	(2, 17),
	(2, 18),
	(2, 19),
	(2, 20),
	(2, 21),
	(2, 22),
	(2, 23),
	(2, 24),
	(2, 25),
	(2, 26),
	(2, 27),
	(2, 28),
	(2, 29),
	(2, 30),
	(2, 31);
	
INSERT INTO "USER_ROLE" (USER_ID, ROLE_ID) VALUES 
	(1, 2);

ALTER TABLE KILDA_PERMISSION ALTER COLUMN permission_id RESTART WITH 100;
ALTER TABLE KILDA_ROLE ALTER COLUMN role_id RESTART WITH 100;
