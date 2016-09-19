# Troubleshooting
If you have issues replicating database transactions to your Streams application, you can validate the correct working of individual components.

## Check the CDC event log and instance log messages
If the CDCStreams user exit cannot post messages to the Streams applications, or if the subscriptions has not been configured correctly, error messages are issued in the subscription's target event log. You can find additional detailed messages in the instance log file (under _cdc-home_/instance/_instance_/log). Optionally, activate debugging for the user exit by setting debug=true in the CDCStreams.properties configuration file.

## Testing the CDCStreams user exit
The easiest way to validate that the CDCStreams user exit generates the correct data is by targeting a TCP/IP listener process that is started through the netcat tool. 
* Change the CDCStreams.properties file and specify "tcpsource" for the outputType property 
* Subsquently specify <host>:<port> (for example localhost:12345) for the tcpHostPort property
* Once finished, start a netcat listener on the server you specified in the tcpHostPort property, for example: nc -l 12345
* Start the CDC subscription. As soon as the subscription starts, it will first send an initialization entry to the listener. Optionally make some database changes to see them appear in the netcat listener
* If no messages are received by the netcat listener, check the CDC instance log file (under _cdc-home_/instance/_instance_/log) for errors

## Errors while running the Streams application
### ArrayOutOfBoundsException in Streams application
If any of the CDCSource or the CDCParse operators termines with an ArrayOutOfBoundsException failure, this most-likely means that the columns that you replicate include a newline character, or the separator specified in the `CDCStreams.properties` file. To replace newline and separator characters before the data is transmitted, use the `fixColumns` parameter for the table-level user exit. See the [Getting started](Usage.md) document for more details on how to configure this parameter.


