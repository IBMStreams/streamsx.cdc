# Installing the CDC Streams toolkit
You can choose to either install the pre-built toolkit, or to build it yourself on the IBM Streams server using the ant tool. In either case, some manual steps are required to implement the CDC user exit jar file on the CDC Engine server and to make the CDC Access Server files available on the Streams server. 

One part of the toolkit, CDCStreamsUserExit, must be installed on the server running the CDC target engine; this part contains the user exit which is to be configured for the CDC subscriptions. 

The other component, com.ibm.streamsx.cdc, must be installed on the IBM Streams server and contains the CDCSource and CDCParse operators which can be incorporated into the Streams application.

**Note:** We do not recommend installing the CDC engine and CDC Access Server components on the Streams server, but rather copy the required components to the Streams server.

## Downloading the CDC Streams toolkit
* Download the full toolkit as a zip file: `https://github.com/IBMStreams/streamsx.cdc/archive/master.zip` into a temporary directory, for example `/tmp`
* Unzip the `master.zip` file

## Include the CDC Access Server jar files in the toolkit
* Copy the contents of the CDC Access Server lib folder (jar files) to the `opt/downloaded` directory under the CDC Streams toolkit folder, `com.ibm.streamsx.cdc`, for example `/tmp/streamsx.cdc/com.ibm.streamsx.cdc/opt/downloaded`
* **Please note: ** The jar files copied into the toolkit folder must match the version of the CDC Access Server it will connect to. If you upgrade the existing CDC Access Server installation, you must replace the contents of the `opt/downloaded` folder with the new version of the upgraded Access Server jar files 


## Optional: build the toolkit using ant
Optionally, only if you plan to build (recompile) the toolkit, you can execute the steps below. These steps must be run on the Streams server and require the CDC Engine's jar files, a Java compiler and the Ant tool
* Create a directory that holds the CDC engine components, for example `/tmp/CDCEngine`
* Copy the CDC Engine `lib` folder to this directory
* Set the Streams environment variables by sourcing the `streamsprofile.sh` script which can be found in the Streams installation `bin` directory
* Set environment variable `CDC_ENGINE_HOME` to the directory that contains the CDC Engine's lib folder, for example `/tmp/CDCEngine`
* Go to the toolkit's main directory that holds the `build.xml` file, for example: `cd /tmp/streamsx.cdc`
* Run `ant`

## Implementing the CDC toolkit on the Streams server
To register the toolkit in IBM Streams, it must be moved to a directory in the toolkit path.
* Move the `com.ibm.streamsx.cdc` directory to `$STREAMS_INSTALL/toolkits`; this will make it visible to IBM Streams and Streams Studio. Alternatively you can move it to a different directory and include it in the Streams toolkit path

## Implementing the CDC Streams user exit on CDC target engine
The CDC Streams user exit takes care of sending the tuples to the Streams application and must be implemented on the CDC (java) target engine. If your CDC source engine is one of the java engines (for Oracle, DB2 LUW, SQL Server, etc.), you can also implement the user exit on the source server and set up loop-back subscription. 
* Transfer the CDCStreamsUserExit folder to the CDC installation directory (_cdc-home_) on the target engine
* Update the `conf/system.cp` file which can be found in the _cdc-home_ and append the following string to the classpath: `:CDCStreamsUserExit:CDCStreamsUserExit/lib/*`. If the CDC target engine runs on Windows, the `:` characters must be substituted by `;`
* Stop all subscriptions targeting the CDC instance in question and restart the instance 

