INSERT INTO "VERSION" (Version_ID, Version_Number, Version_Deployment_Date)
VALUES (31, 31, CURRENT_TIMESTAMP);

INSERT  INTO "ACTIVITY_TYPE" (activity_type_id, activity_name) VALUES 
	(44, 'UPDATE_ISL_BFD_PROPERTIES'),
	(45, 'DELETE_ISL_BFD');

INSERT INTO "KILDA_PERMISSION" (PERMISSION_ID, PERMISSION, IS_EDITABLE, IS_ADMIN_PERMISSION, STATUS_ID, CREATED_BY, CREATED_DATE, UPDATED_BY, UPDATED_DATE,DESCRIPTION) VALUES 
	(360, 'isl_update_bfd_properties', false, false, 1, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'Permission to update isl bfd properties'),
	(361, 'isl_delete_bfd', false, false, 1, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'Permission to delete isl bfd');
	
INSERT INTO "ROLE_PERMISSION" (ROLE_ID,PERMISSION_ID) VALUES 
	(2, 360),
	(2, 361);