# SurfStore

Cloud-based File Storage Service

## Introduction

SurfStore is a networked file storage application that is based on DropBox, and lets you sync file to and from the “cloud”.

Multiple clients can concurrently connect to the SurfStore service to access a common, shared set of files. Clients accessing SurfStore “see” a consistent set of updates to files, but SurfStore does not offer any guarantees about operations across files, meaning that it does not support multi-file transactions (such as atomic move).

The SurfStore service is composed of the following two sub-services:

* BlockStore: The content of each file in SurfStore is divided up into chunks, or blocks, each of which has a unique identifier. The BlockStore service stores these blocks, and when given an identifier, retrieves and returns the appropriate block.

* MetadataStore: The MetadataStore service holds the mapping of filenames/paths to blocks.