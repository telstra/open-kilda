
-- Dumping data for table openkilda.role: ~2 rows (approximately)
/*!40000 ALTER TABLE `role` DISABLE KEYS */;
INSERT IGNORE INTO `role` (`user_role_id`, `user_role`) VALUES
	(1, 'ROLE_USER'),
	(2, 'ROLE_ADMIN');
/*!40000 ALTER TABLE `role` ENABLE KEYS */;

-- Dumping data for table openkilda.user: ~2 rows (approximately)
/*!40000 ALTER TABLE `user` DISABLE KEYS */;
INSERT IGNORE INTO `user` (`UserId`, `Username`, `Password`, `Name`, `Email`, `LoginTime`, `LogoutTime`, `ActiveFlag`, `IsAuthorized`, `CreatedBy`, `CreatedDate`, `UpdatedDate`, `UpdatedBy`) VALUES
	(1, 'user', '$2a$11$E7TUOEYF3xh8wxpKPDQ7oekTYojKYbPo4dXluBhM6eSmg5HrdnQIW', 'User', 'user@openkilda.org', '2017-11-02 12:05:33', '2017-11-02 12:09:33', 1, '1', 1, '2017-11-24 14:36:11', '2017-11-24 14:36:11', 1),
	(2, 'admin', '$2a$11$/PHW3eqqJkN2SDbrQhu44eYQkOPIMmoclx5eg8MeTk3tbay6hsVou', 'Admin', 'admin@openkilda.org', '2017-11-02 12:05:33', '2017-11-02 12:09:33', 1, '1', 1, '2017-11-24 14:36:11', '2017-11-24 14:36:11', 1);
/*!40000 ALTER TABLE `user` ENABLE KEYS */;

-- Dumping data for table openkilda.user_role: ~2 rows (approximately)
/*!40000 ALTER TABLE `user_role` DISABLE KEYS */;
INSERT IGNORE INTO `user_role` (`user_id`, `role_id`) VALUES
	(1, 1),
	(2, 2);
/*!40000 ALTER TABLE `user_role` ENABLE KEYS */;