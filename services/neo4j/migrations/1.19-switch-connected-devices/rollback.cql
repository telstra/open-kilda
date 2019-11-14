DROP CONSTRAINT ON (switch_connected_device:switch_connected_device) ASSERT switch_connected_device.entityId IS UNIQUE;
DROP CONSTRAINT ON (switch_connected_device:switch_connected_device) ASSERT switch_connected_device.unique_index IS UNIQUE;
DROP INDEX ON :switch_connected_device (port_number);
DROP INDEX ON :switch_connected_device (vlan);
DROP INDEX ON :switch_connected_device (mac_address);
DROP INDEX ON :switch_connected_device (type);
DROP INDEX ON :switch_connected_device (chassis_id);
DROP INDEX ON :switch_connected_device (port_id);
