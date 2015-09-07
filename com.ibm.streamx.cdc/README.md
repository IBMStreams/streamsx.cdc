streamsx.cdc
============

This toolkit allows Streams to receive tuples from InfoSphere Change Data Capture. It provides a CDCStreams user exit which can be defined as a table-level and subscription-level user exit. 
The user exit is configured to connect to the toolkit's CDCSource operator, after which the CDCParse operator
converts the incoming messages to a tuple.

## Setup

 