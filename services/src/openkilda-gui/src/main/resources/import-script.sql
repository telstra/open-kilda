INSERT  INTO SA.ROLE (user_role_id, user_role) VALUES
	(1, 'ROLE_USER'),
	(2, 'ROLE_ADMIN');
/*!40000 ALTER TABLE role ENABLE KEYS */;

-- Dumping data for table openkilda.user: ~2 rows (approximately)
/*!40000 ALTER TABLE user DISABLE KEYS */;
INSERT  INTO SA.KILDA_USER (User_Id, Username, Password, Name, Email, Login_Time, Logout_Time, Active_Flag, Is_Authorized, Created_By, Created_Date, Updated_Date, Updated_By) VALUES
	(1, 'user', '$2a$11$7a9uAFMb/ssuVaAwXXg69u1RPyFRbcstLTDfZ/GN2Jdoi2f.XHRXu', 'User', 'user@openkilda.org', '2017-11-02 12:05:33', '2017-11-02 12:09:33', true, '1', 1, '2017-11-24 14:36:11', '2017-11-24 14:36:11', 1),
	(2, 'admin', '$2a$11$/PHW3eqqJkN2SDbrQhu44eYQkOPIMmoclx5eg8MeTk3tbay6hsVou', 'Admin', 'admin@openkilda.org', '2017-11-02 12:05:33', '2017-11-02 12:09:33', true, '1', 1, '2017-11-24 14:36:11', '2017-11-24 14:36:11', 1);
/*!40000 ALTER TABLE user ENABLE KEYS */;

-- Dumping data for table openkilda.user_role: ~2 rows (approximately)
/*!40000 ALTER TABLE user_role DISABLE KEYS */;
INSERT  INTO SA.USER_ROLE (user_id, role_id) VALUES
	(1, 1),
	(2, 2);
/*!40000 ALTER TABLE user_role ENABLE KEYS */;