# SurfStore

Cloud-based File Storage Service

## Introduction 

SurfStore is a networked file storage application that supports four basic commands:

* Create a file
* Read the contents of a file
* Change the contents of a file
* Delete a file

Multiple clients can concurrently connect to the SurfStore service to access a common, shared set of files. Clients accessing SurfStore “see” a consistent set of updates to files.


The SurfStore service is composed of the following two sub-services:

* BlockStore: The content of each file in SurfStore is divided up into chunks, or blocks, each of which has a unique identifier. The BlockStore service stores these blocks, and when given an identifier, retrieves and returns the appropriate block.

* MetadataStore: The MetadataStore service holds the mapping of filenames/paths to blocks.

## Configuration File

config.txt
This configuration file helps the server or client know the cluster information and also how many blockstore servers are present in the service. 

```
B: 2
metadata: <host>:<port>
block1: <host>:<port>
block2: <host>:<port>
```
This example has two BlockStore servers:

* The initial line B defines the number of BlockStore servers.

* The ‘metadata’ line specifies the host and port number of your metadata server.

* The ‘block’ line specifies the host and port numbers of your blockstore server(s). Note the ‘1’, ‘2’, etc after the word block to indicate the ports for the different instances of the service.



## How to Run?

Use the following commands to run the blockstore, metadata store and the client - 

1. Blockstore - 

   ```shell
   python blockstore.py <port-number>
   ```

2. Metadata store - 

   ```shell
   python metastore.py config.txt
   ```

3. Client - 

   ```shell
   // to download a file
   python client.py config.txt download myfile.jpg folder_name/
   
   // to upload a file
   python client.py config.txt upload myfile.jpg
   
   // to delete a file
   python client.py config.txt delete myfile.jpg
   ```
