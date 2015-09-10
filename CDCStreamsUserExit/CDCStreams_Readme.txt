CDC_Streams User Exit description
---------------------------------

This user exit facilitates replicating changes captured using InfoSphere CDC to an InfoSphere Streams 
application. Typically, the user exit would be configured for a subscription between two CDC database Java engines, but
it can also be used with CDC Server. Please note that with CDC Event Server you may expect reduced throughput because
every operation applied will be committed.

One can choose to pass the replicated events to a specialized Streams CDCSource operator, or plainly forward all entries
to a TCP/IP socket or a named pipe (fifo file). We recommend to use the specialized CDCSource operator as it closely ties
with the committing of the subscription bookmark and avoid losing transactions in case of a communications error or the
Streams application being interrupted.

The data passed (tuple) to the target side is formatted as character-separated values, where there is a distinction between
the separator used for the metadata and the one used for the change data; this was done to improve the performance of the
CDCSource operator when parsing the metadata of the changes.
 
There are a number of fixed fields (metadata) in each record that is passed, followed by the before-image of the changed record and
the after-image of the changed record. The tuple has a the following format (assuming the metadata separator is a § character and the
change data is separated by a piping character, |):
table_schema.table_name§commit_timestamp§transaction_id§entry_type§user§before_image_columns|after_image_columns

Details for the columns:
- table_schema.table_name: Table schema and Table name are consolidated in one field and included in every replicated 
    tuple. Because all replicated rows write to the same physical target (TCP/IP socket, named pipe), one needs to be 
    able to identify the table in which the row was changed.
- commit_timestamp: This is an ISO timestamp (yyyy-mm-dd hh24:mi:ss:ffffff) that indicates when the transaction was 
    committed at the source. This resembles the &TIMSTAMP journal control field.
- transaction_id: This is the identification of the unit of work, all operations in a database transaction have the same
    transaction ID.
- entry_type: The entry_type column holds the type of operation (I=Insert, U=Update, D=Delete) performed at the source.
  user: The source database user who performed the database transaction.
- before_columns: One field per source database column which is selected for replication. These fields hold the 
    before-image of the updated or deleted row. In case of an insert, these fields are empty but will be present in the
    output format.
- after_columns: One field per source database column which is selected for replication. These fields hold the 
    after-image of the updated or inserted row. In case of a delete, these fields are empty but will be present in the 
    output format.

All tuples end with the Unix/Linux new line character, this to ensure that InfoSphere Streams can correctly identify
the end of each tuple.

The class implements both the UserExitIF and the SubscriptionUserExitIF which means it can handle both the
subscription-level and row-level events. During configuration one must ensure that the user exit is configured
at the subscription-level and also as before insert/update/delete. If you fail to define the user exit at the 
subscription-level, the first handled operation will cause the subscription stop with an error.

The configuration of the user exit is held in the CDCStreams.properties file. This, amongst others, defines the
destination of the replicated changes (TCP/IP or named pipe) and also the column separator to be used. Please see
the example CDCStreams.properties file for a full list of all properties.

When targeting a TCP/IP port, the subscription-level user exit will attempt to connect to the defined host and port.
If a connection cannot be established within the specified number of seconds (tcpConnectionTimeoutSeconds), a message
is written to the target event log and the subscription will stop. In case that the destination is a named pipe, the
fifo file in question must already exist when starting the subscription, otherwise it raises an error and stops
immediately. 

If you specify "debug=yes" in the properties file, detailed log information (including the received changes) are
written to the current log file in the <cdc_home>/instance/<instance_name>/log directory.

Compilation instructions
------------------------
The user exit has a dependency on the ts.jar file. To compile, do the following:
javac -cp .:../lib/ts.jar UETrace.java 
javac -cp .:../lib/ts.jar UESettings.java 
javac -cp .:../lib/ts.jar CDCStreams.java

This will render 3 class files which can then be deployed on the CDC target engine. 

Implementation instructions
---------------------------
Implementing the user exit must be done through the following steps:
- Copy all .class files to the <cdc_home>/lib directory of the target engine
- Copy the CDCStreams.properties to the <cdc_home> directory of the target engine
- Modify the properties to match your Streams application's requirements
- Restart the CDC target engine

Once the subscription has been created and table mappings are complete, configure the user exit at the
subscription-level and at the table-level. Then, ensure that the user exit can reach the defined target (TCP/IP
listener or named pipe). Finally, start the replication and optionally perform inserts/updates or deletes on the source table.

For test purposes, you can simulate the Streams target so that you can validate the records that are being replicated as follows:
- TCP/IP (tcpsource): On the server specified by the host name in the .properties file, run command "nc -l <port>" from a Linux
  command line. This will start a TCP/IP listener process and you can see the tuples generated by the user exit. Once the 
  listener is active you can start the replication.
- Named pipe: Create a named pipe as specified in the .properties file using command "mkfifo <named pipe filename>". 
  Once the named pipe has been created, start the replication. You can view the tuples by doing a "cat <named pipe filename>" 
  on the Linux command line.
