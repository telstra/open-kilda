INSERT INTO "VERSION" (Version_ID, Version_Number, Version_Deployment_Date)
VALUES (5, 5, CURRENT_TIMESTAMP);
	
INSERT  INTO "ACTIVITY_TYPE" (activity_type_id, activity_name) VALUES 
	(22, 'CREATE_FLOW'),
	(23, 'UPDATE_FLOW'),
	(24, 'DELETE_FLOW');
