# InfoSphere CDC Streams toolkit
==============

This toolkit allows Streams to receive tuples from InfoSphere Data Replication Change Data Capture. It provides a CDC Java user exit which allows subscriptions to push record changes to a Streams application and two Streams operators to respectively receive the incoming tuples (CDCSource) and convert (CDCParse) them into a tuple. The toolkit contains an example which demonstrates the use of the Streams operators.

## Installation of the toolkit
Installation instructions can be found here: [Installing the toolkit](documentation/Installation.md)

## Getting started
Once installation is done, here you can find how to use the toolkit: [Using the CDCStreams toolkit](documentation/Usage.md)

## Developing your Streams application
When you've done the initial settings and configured the replication with the user exit, you can start developing your Streams application: [Developing the Streams application](documentation/StreamsDevelopment.md)

## Troubleshooting
If the CDC subscription fails to send changes to the Streams application, or if your application issues errors when receiving tuples from the subscriptions, please check: [Troubleshooting](documentation/Troubleshooting.md)