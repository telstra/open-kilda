# Apache Derby To MySQL Migration
## _Objective :_

Goal of this document is to provide a stepwise guide to migrate your application from using Apache Derby to MySQL. We need to ensure that exported data structure is compatible with MySQL so that data can be imported to MySQL without any issue.

## Pre-requisite:

- **Required JAVA:** To check if JAVA_HOME is setup run below command
   - ```echo $JAVA_HOME```
- **Required MySQL:** Click [here](https://dev.mysql.com/doc/mysql-installation-excerpt/5.7/en/) to view Mysql Installation guide
- **Script Files** : Below mentioned script files that are needed for complete migration and are available [here](/tools/derby-to-mysql-migration/script-files).
**a. derbymysqlmigration.sh:** bash file that contains script to setup and install derby tools (dblook and ij). script to connect to ij and export the derby data to CSV files. These CSV files will later be used to import the data in MYSQL.
**b. derby-mysql-import.sh:** bash file that contains the script which will lookup the derby db metadata in form of SQLs and then will update those SQLs to make them compatible with MYSQL and will create a MySQL compatible .sql metadata file. Then will create the database, along with tables and import all the table records.
**c. derby-export.sql:** sql file that contains scripts to export the data from each Derby db table into CSV files.
**d. derby-import.sql:** sql file that contains commands to import the data in MYSQL database tables.
**e. derby.properties:** properties file to configure MySQL Database 
**f. input.txt:** input file for IJ configuration

## Migration

User will have to navigate to below mentioned location and execute following commands:

- ```cd /tools/derby-to-mysql-migration```
- ```make pre-req "olddb=<oldDBName>" "newdb=<newDBName>" "derbydb=<derbyDBPath>"```

 **a. oldDBName:** Derby database name, which is SA by default
 **b. newDBName:** Name of the database you want to create in MySQL
 **c. derbyDBPath:** Path of your existing Derby database
- Stop the application that is running with Derby database from which DB migration has to be done, in order to avoid any data loss. Also, this is mandatory for the successful execution of bash scripts.

## Export & Import metadata and data from existing Derby to MySQL DB:
  - ```make export```
  - ```make import```

This will lookup the metadata in form of SQLs and then will update those SQLs to make them compatible to MySQL. On execution, It will create the database in MySQL, along with all the tables and will also import all the records in db tables. **It will promt you for MySQL password twice**.

## Verification
Follow below mentioned steps to Verify if scripts execution is successful and database is created successfully along with each table and its records. 
- ```mysql -u root -p ```
Enter Password:
- ```mysql> use {dbname};```
- ```mysql> show tables;```
- ```mysql> select * from {tablename};```
## Cleanup
In case there's a failure in migration at any step, first navigate to /opt directory and execute below command to cleanup the process and then start again from scratch i.e by navigating to /tools/derby-to-mysql-migration directory and then execute commands.
 
 - ```cd /opt``` 
 - ```rm -rf /opt/derby```
