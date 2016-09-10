# InfoSphere CDC Streams toolkit
==============

This toolkit allows Streams to receive tuples from InfoSphere Data Replication Change Data Capture. It provides a CDC Java user exit which allows subscriptions to push record changes to a Streams application and two Streams operators to respectively receive the incoming tuples (CDCSource) and convert (CDCParse) them into a tuple. The toolkit contains an example which demonstrates the use of the Streams operators.

## Installing the toolkit
You can choose to either install the pre-built toolkit, or to build it yourself on the IBM Streams server using the ant tool. In either case, some manual steps are required to implement the CDC user exit jar file on the CDC Engine server and to make the CDC Access Server files available on the Streams server. 

One part of the toolkit, CDCStreamsUserExit, must be installed on the CDC server; this part contains the user exit which is to be configured for the CDC subscriptions. 

The other component, com.ibm.streamsx.cdc, must be installed on the IBM Streams server and contains the CDCSource and CDCParse operators which can be incorporated into the Streams application.

### Downloading the CDC Streams toolkit
* Download the full toolkit as a zip file: https://github.com/IBMStreams/streamsx.cdc/archive/master.zip into a temporary directory, for example /tmp
* Unzip the master.zip file

### Optional: build the toolkit using ant
Optionally, only if you plan to build (recompile) the toolkit, the CDC Engine jar files are needed to compile the user exit.
* Create a directory that holds the CDC engine components, for example /tmp/CDCEngine
* Copy the CDC Engine lib folder to this directory

* Create a directory structure opt/downloaded, which will hold the Access Server jar files, under the CDC Streams toolkit, for example: mkdir -p /tmp/streamsx.cdc/com.ibm.streamsx.cdc/opt/downloaded
* Copy the contents of the CDC Access Server lib folder (jar files) to the opt/downloaded directory
* Set the Streams environment variables by sourcing the `streamsprofile.sh` script which can be found in the Streams `bin` directory
* Set environment variable `CDC_ENGINE_HOME` to the directory that contains the CDC Engine's lib folder, for example `/tmp/CDCEngine`
* Go to the toolkit's main directory that holds the build.xml file, for example: `cd /tmp/streamsx.cdc`
* Run `ant`

### Implementing the CDC toolkit on the Streams server
To access the CDC configuration, the Streams toolkit requires the jar files which are provided as part of the CDC Access Server. If you have not built the toolkit using ant (described in the previous section), you will need to copy the CDC Access Server jar files into the toolkit's directory structure.
* Create a directory structure opt/downloaded, which will hold the Access Server jar files, under the CDC Streams toolkit, for example: `mkdir -p /tmp/streamsx.cdc/com.ibm.streamsx.cdc/opt/downloaded`
* Copy the contents of the CDC Access Server lib folder (jar files) to the `opt/downloaded` directory

To register the toolkit in IBM Streams, it must be moved to a directory in the toolkit path.
* Move the `com.ibm.streamsx.cdc` directory to `$STREAMS_INSTALL/toolkits`; this will make it visible to IBM Streams and Streams Studio

We do not recommend installing the CDC engine and CDC Access Server components on the Streams server, but rather copy the required components to the Streams server.

### Implementing the CDC Streams user exit on CDC target engine
* Transfer the CDCStreamsUserExit folder to the CDC installation directory (_cdc-home_) on the target engine
* Update the _cdc-home_/conf/system.cp file and append the following string to the classpath: `:CDCStreamsUserExit:CDCStreamsUserExit/lib/*`
* Stop all subscriptions targeting the CDC instance in question and restart the instance 

### Configuration
The behaviour of the CDC Streams user exit is determined by the settings in `CDCStreams.properties` file, which is kept in the `CDCStreamsUserExit` folder. You can create multiple properties files and refer to them as a parameter in the subscription-level user exit. The user exit will first look for the properties file in the CDC engine's classpath (which has been enhanced with the `CDCStreamsUserExit` directory). If the specified properties file is not found in the classpath, the user exit will try to load it from the current directory, which is the _cdc-home_ directory.

The most-important parameters to configure in the CDCStreams.properties file are:
* outputType: Specifies the target of the user exit. For the tightest integration between CDC and Streams, we recommend to set this parameter to "cdcsource"; this causes the user exit to try to connect to the toolkit's CDCSource operator
* tcpHostPort: Host name (or IP address) and port that the Streams application is listening to. This parameter applies when the outputType is cdcsource or tcpsource only

## Getting started

### Mapping tables
First you must create a subscription referencing the source datastore and the target datastore. The target datastore must reference the CDC installation in which the CDCStreams user exit has been placed.

![01_create_subscription](documentation/images/01_create_subscription.PNG)

Once the subscription has been created, right-click it and select "User Exit". Subsequently specify CDCStreams as the user exit name and optionally specify the name of the properties file that this subscription will use. If no parameter is specified, the default properties file _cdc-home_/CDCStreamsUserExit/CDCStreams.properties is assumed. By default, the user exit will attempt to find the properties file under the _cdc-home_/CDCStreamsUserExit folder.

![02_subscription_user_exit](https://cloud.githubusercontent.com/assets/8166955/9888417/43104868-5bf5-11e5-8489-71c6148242e5.PNG)

Map a table from source to target, select "Custom" mapping type for mapping an individual table (recommended).

![03_select_mapping_type](https://cloud.githubusercontent.com/assets/8166955/9888420/4312e000-5bf5-11e5-97d6-98f1298ef9de.PNG)

Select the source table for which DML operations must be replicated to your Streams application.

![04_select_source_table](https://cloud.githubusercontent.com/assets/8166955/9888418/4310950c-5bf5-11e5-94a0-48da07bd43a2.PNG)

Effectively you only need one target table for all table mappings; the CDC user exit prevents changes from being written to the selected target table. Create a "dummy" target table the first time you map a table to Streams and re-use this target table for all mappings.
![05_select_new_target_table](https://cloud.githubusercontent.com/assets/8166955/9888419/4311aa00-5bf5-11e5-9380-4379b4ed3745.PNG)
![06_create_table](https://cloud.githubusercontent.com/assets/8166955/9888421/4314befc-5bf5-11e5-9a18-16af7cc8ee85.PNG)
![07_create_table](https://cloud.githubusercontent.com/assets/8166955/9888422/4326b06c-5bf5-11e5-971b-e5b735b3b1f0.PNG)
![08_remove_columns_add_key](https://cloud.githubusercontent.com/assets/8166955/9888423/432839fa-5bf5-11e5-92a2-ecc6bca3a665.PNG)
![09_specify_key](https://cloud.githubusercontent.com/assets/8166955/9888425/432d901c-5bf5-11e5-9d33-7d577b54a484.PNG)
![10_review_table](https://cloud.githubusercontent.com/assets/8166955/9888424/432cfe54-5bf5-11e5-9ac7-dba5d9a8664d.PNG)

Select the target table and specify the key.

![11_select_target_table](https://cloud.githubusercontent.com/assets/8166955/9888427/4332a822-5bf5-11e5-95be-7f88d30c8b65.PNG)
![12_specify_key](https://cloud.githubusercontent.com/assets/8166955/9888426/432f236e-5bf5-11e5-89dd-f3247714c7da.PNG)

Set the replication method to Mirror.

![13_set_replication_method](https://cloud.githubusercontent.com/assets/8166955/9888429/4340b26e-5bf5-11e5-842d-f3d16be91030.PNG)

Review the mappings and confirm the mapping of the source table.

![14_review_mappings](https://cloud.githubusercontent.com/assets/8166955/9888428/433fbcec-5bf5-11e5-90b3-5c3a32ce9fcb.PNG)

When the table has been successfully mapped, specify "com.ibm.replication.cdc.streams.CDCStreams" as the user exit for the "before insert", "before update" and "before delete" actions.

![15_set_user_exit](https://cloud.githubusercontent.com/assets/8166955/9888430/43471884-5bf5-11e5-8328-2850c43dcfdf.PNG)

Dependent on the target engine, the replication status will be set to either Refresh or Active. Plesae ensure that you select the proper replication status, dependent whether you want all records to be sent to the Streams application at the start of the subscription, or only replicate the changes from this moment on.

### Creating your Streams application 

## Troubleshooting
If you have issues replicating database transactions to your Streams application, you can validate the correct working of individual components.

### Check the CDC event log and instance log messages
If the CDCStreams user exit cannot post messages to the Streams applications, or if the subscriptions has not been configured correctly, error messages are issued in the subscription's target event log. You can find additional detailed messages in the instance log file (under _cdc-home_/instance/_instance_/log). Optionally, activate debugging for the user exit by setting debug=true in the CDCStreams.properties configuration file.

### Testing the CDCStreams user exit
The easiest way to validate that the CDCStreams user exit generates the correct data is by targeting a TCP/IP listener process that is started through the netcat tool. 
* Change the CDCStreams.properties file and specify "tcpsource" for the outputType property 
* Subsquently specify <host>:<port> (for example localhost:12345) for the tcpHostPort property
* Once finished, start a netcat listener on the server you specified in the tcpHostPort property, for example: nc -l 12345
* Start the CDC subscription. As soon as the subscription starts, it will first send an initialization entry to the listener. Optionally make some database changes to see them appear in the netcat listener
* If no messages are received by the netcat listener, check the CDC instance log file (under _cdc-home_/instance/_instance_/log) for errors

### Testing the Streams operators



