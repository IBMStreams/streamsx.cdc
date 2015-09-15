# InfoSphere CDC Streams toolkit
============

This toolkit allows Streams to receive tuples from InfoSphere Change Data Capture. It provides a CDC Java user exit which allows subscriptions to push record changes to a Streams application and two Streams operators to respectively receive the incoming tuples and convert them into a tuple. The toolkit contains an example which demonstrates the use of the Streams operators.

## Building the toolkit
The toolkit must be installed on a server that runs Streams. You must use Apache Ant to build the toolkit. Prior to running Ant, ensure that the required CDC jar files are available on the Streams server. We do not recommend installing the CDC engine and CDC Access Server on the Streams server, but rather copy the required components from the servers running the respective CDC components:
* Download the toolkit as a zip file: https://github.com/IBMStreams/streamsx.cdc/archive/master.zip
* Unpack the zip file into the directory that holds the Streams toolkits, typically $STREAMS_INSTALL/toolkits
* Create a directory that holds the Access Server components, for example /tmp/CDCAccessServer
* Copy the CDC Access Server lib folder to this directory
* Set environment variable CDC_ACCESS_SERVER_HOME to the directory that contains the lib folder
* Create a directory that holds the CDC Engine components, for example /tmp/CDCEngine
* Copy the CDC Engine lib folder to this directory
* Set environment variable CDC_ENGINE_HOME to the directory that contains the lib folder
* Go to the toolkit's main directory that holds the build.xml file
* Run ant

## Enabling CDC to target a Streams application
Once the toolkit has been built do the following:
* Transfer the .properties and .class files under the CDCStreamsUserExit bin folder to the server running the CDC target engine
* Place the properties file in the CDC installation directory
* Place the class files in the lib directory of the CDC installation 
* If you have replaced existing class files you must stop all subscriptions targeting the CDC instance in question and restart the instance  

## Configuration
The behaviour of the CDC user exit is determined by the settings in CDCStreams.properties files. You can create multiple properties files and refer to them as a parameter in the subscription-level user exit. 

The most-important parameters to configure in the CDCStreams.properties file are:
* outputType: Specifies the target of the user exit. For the tightest integration between CDC and Streams, we recommend to set this paramter to "cdcsource"; this causes the user exit to try to connect to the toolkit's CDCSource operator
* tcpHostPort: Host name (or IP address) and port that the Streams application is listening to. This parameter applies when the outputType is cdcsource or tcpsource only

## Getting started

### Mapping tables
First you must create a subscription referencing the source datastore and the target datastore. The target datastore must reference the CDC installation in which the CDCStreams user exit has been placed.

![01_create_subscription](https://cloud.githubusercontent.com/assets/8166955/9888416/430e68b8-5bf5-11e5-80a5-8e1de4f6c24e.PNG)

Once the subscription has been created, right-click it and select "User Exit". Subsequently specify CDCStreams as the user exit name and optionally specify the name of the properties file that this subscription will use. If no parameter is specified, the default properties file CDCStreams.properties is assumed.

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

When the table has been successfully mapped, specify "CDCStreams" as the user exit for the "before insert", "before update" and "before delete" actions.

![15_set_user_exit](https://cloud.githubusercontent.com/assets/8166955/9888430/43471884-5bf5-11e5-8328-2850c43dcfdf.PNG)

Dependent on the target engine, the replication status will be set to either Refresh or Active. Plesae ensure that you select the proper replication status, dependent whether you want all records to be sent to the Streams application at the start of the subscription, or only replicate the changes from this moment on.

### Creating your Streams application 

