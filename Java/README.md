# SurfStore

Cloud-based File Storage Service

## Introduction

SurfStore is a networked file storage application that is based on DropBox, and lets you sync file to and from the “cloud”.

Multiple clients can concurrently connect to the SurfStore service to access a common, shared set of files. Clients accessing SurfStore “see” a consistent set of updates to files, but SurfStore does not offer any guarantees about operations across files, meaning that it does not support multi-file transactions (such as atomic move).

The SurfStore service is composed of the following two sub-services:

* BlockStore: The content of each file in SurfStore is divided up into chunks, or blocks, each of which has a unique identifier. The BlockStore service stores these blocks, and when given an identifier, retrieves and returns the appropriate block.

* MetadataStore: The MetadataStore service holds the mapping of filenames/paths to blocks.

Client will “sync” a local base_dir base directory with your SurfStore cloud service. When you invoke your client, the sync operation will occur, and then the client will exit. As a result of syncing, new files added to your base directory will be uploaded to the cloud, files that were sync’d to the cloud from other clients will be downloaded to your base directory, and any files which have “edit conflicts” will be resolved.

Your client program will create and maintain an index.txt file in the base directory which holds local, client-specific information that must be kept between invocations of the client. If that file doesn’t exist, your client should create it.  In particular, the index.txt contains a copy of the server’s FileInfoMap accurate as of the last time that sync was called. The purpose of this index file is to detect files that have changed, or been added to the base directory since the last time that the client executed.

The format of the index.txt file should be one line per file, and each line should have the filename, the version, and then the hash list.  The filename is separated from the version number with a comma, and the version number is separated from the hash list with a comma.  Entries in the hash list should be separated by spaces.  

For example:
File1.dat,3,h0 h1 h2 h3
File2.jpg,8,h8 h9

## How to Run?

Use the following commands to run the blockstore, metadata store and the client -

1. Blockstore and Metadata Store -

   ```shell
   ./run-server.sh
   ```

2. Client -

   ```shell
   Format: ./run-client.sh localhost:8080 basedir blocksize
   ./run-client.sh localhost:8080 basedir 4096
   ```
