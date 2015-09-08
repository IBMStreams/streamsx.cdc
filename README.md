streamsx.cdc
============

This toolkit allows Streams to receive tuples from InfoSphere Change Data Capture. It provides a CDC Java user exit which allows subscriptions to push record changes to a Streams application and two Streams operators
to respectively receive the incoming tuples and convert them into a tuple. The toolkit contains an example which demonstrates the use of the Streams operators.

##Building the toolkit
The toolkit must be installed on a server that runs Streams. You must use Apache Ant to build the toolkit. Prior to running Ant, ensure that the required CDC jar files are available on the Streams server. We do not
recommend installing the CDC engine and CDC Access Server on the Streams server, but rather copy the required components from the servers running the respective CDC components:
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

##Enabling CDC to target a Streams application
Once the toolkit has been built do the following:
* Transfer the .properties and .class files under the CDCStreamsUserExit bin folder to the server running the CDC target engine
* Place the properties file in the CDC installation directory
* Place the class files in the lib directory of the CDC installation 
* If you have replaced existing class files you must stop all subscriptions targeting the CDC instance in question and restart the instance  

##Configuration
The behaviour of the CDC user exit is determined by the settings in CDCStreams.properties files. You can create multiple properties
files and refer to them as a parameter in the subscription-level user exit. For the tightest integration between CDC and Streams, we
recommend to set the outputType property to "cdcsource"; this causes the user exit to try to connect to the CDCSource Streams
operator.

##Mapping tables
First you must create a subscription referencing the source datastore and the target datastore. The target datastore must reference
the CDC installation in which the CDCStreams user exit has been placed.
Once the subscription has been created, right-click it and select "User Exit". Subsequently specify CDCStreams as the user exit
name and optionally specify the name of the properties file that this subscription will use. If no parameter is specified,
the default properties file CDCStreams.properties is assumed.

Map a table from source to target. Effectively, you do not need to have a target table for each source table; you can create
a "dummy" target table which will be used as the destination for each of the source tables; inserts, updates and deletes will
be disabled by the user exit.

When the table has been successfully mapped, specify CDCStreams as the user exit for the "before insert", "before update" and
"before delete" actions.
