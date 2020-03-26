INSERT INTO "VERSION" (Version_ID, Version_Number, Version_Deployment_Date)
VALUES (10, 10, CURRENT_TIMESTAMP);
	
INSERT  INTO "KILDA_STORE_TYPE" (store_type_id, store_type_name, store_type_code) VALUES 
	(2, 'Switch Store', 'SWITCH_STORE');
	
INSERT  INTO "ACTIVITY_TYPE" (activity_type_id, activity_name) VALUES 
	(30, 'UPDATE_SWITCH_STORE_CONFIG'),
	(31, 'DELETE_SWITCH_STORE_CONFIG');
