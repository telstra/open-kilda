
## **User Documentation**
Note: Current version does not support individual user accounts. However, you need to use default login credentials to access system.
#### Login Credentials 
###### ADMIN Credentials
 >username = admin
 
 >password = admin
###### USER Credentials
 >username = user
 
 >password = user
----
Application base url: http://localhost:1010/openkilda` 
* You will see login screen after launching GUI.
* By using default credentials, you will be able to login into system. 
* On successful login, you will be redirected to home screen.
* On left, Different menu tabs will be shown like Home, Topology etc. 
* User will be able to see default username on top right. On click, it will provide an option to logout from system.
* Click on Topology tab will open default Topology View which is a cluster-wide view of the network topology.
* Elements shown in base release are:
  + Switch Details
  + ISL Link Details
  + Flow Link Details
* Switch Details: Switch details provide information of switch and number of ports.Click on Switch in topology view will show following details:   
         + ```DPID```
         + ```Address```
         + ```Description```
         + ```controller```
         +  Port Details: Port details will provide information of port with following elements:
        + ```Interface Type```
        + ```Port Name```
        + ```Port Number```
        + ```Status```
* ISL Link Details: Click on ISL Link, will provide Source and Destination of switch details.
      + ```Speed```
      + ```Latency```
      + ```Available Bandwidth```
      + Graph: The Graph View provides ISL link traffic flow between ports connecting the two switches.
        + Choose options 
          + ```Start_datetime```
          + ```End_datetime```
          + ```Metric```
          + ```Downsample```
          + ```AutoReload(sec)```
  + Flow link details: The Flow view provides packet details with a known source and destination switch.
    + Flow Detail
      + ```Flow name```
      + ```Port```
      + ```Switch```
      + ```Vlan```
      + ```Status```
      + ```Maximum Bandwidth```
      + Graph - The Graph View provides traffic flow between ports of connecting switches. 
        + Choose options 
          + ```Start_datetime```
          + ```End_datetime```
          + ```Metric```
          + ```Downsample```
          + ```AutoReload(sec)```
      
  
 