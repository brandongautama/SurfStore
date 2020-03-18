import rpyc
import hashlib
import os
import sys

"""
A client is a program that interacts with SurfStore. It is used to create,
modify, read, and delete files.  Your client will call the various file
modification/creation/deletion RPC calls.  We will be testing your service with
our own client, and your client with instrumented versions of our service.
"""

class SurfStoreClient():

    """
    Initialize the client and set up connections to the block stores and
    metadata store using the config file
    """
    def __init__(self, config):
        file  = open(config, 'r')
        lines = file.readlines()
        self.portNum = []
        self.hostNum = []
        for line in lines:
            value = line.split(': ')
            if value[0] == 'B':
                self.numBlockStores = int(value[1])
            elif value[0] == 'metadata':
                item = value[1].split(":")
                self.metadataHost = item[0]
                self.metadataPort = int(item[1])
            elif value[0][0:5:] == 'block':
                value[1] = value[1].rstrip('\n')
                item = value[1].split(':')
                self.hostNum.append(item[0])
                self.portNum.append(int(item[1]))


    def findServer(self, h):
        return int(h,16) % self.numBlockStores
    """
    upload(filepath) : Reads the local file, creates a set of 
    hashed blocks and uploads them onto the MetadataStore 
    (and potentially the BlockStore if they were not already present there).
    """
    def upload(self, filepath):
        if not os.path.isfile(filepath):
            print("Not Found")
            return

        (path, filename) = os.path.split(filepath)
        name = filename.split('.')
        BLOCK_SIZE = 4096
        filenum = 0
        file = open(filepath, 'rb')
        block = file.read(BLOCK_SIZE)
        blockarr = []
        hashlist = []

        #break the file into blocks
        while block:
            blockarr.append(block)
            hashlist.append(hashlib.sha256(block).hexdigest())
            filenum += 1
            block = file.read(BLOCK_SIZE)

        connMeta = rpyc.connect(self.metadataHost, self.metadataPort)

        try:
            v = connMeta.root.read_file(filename)
        except Exception as e:
            if e.error_type == 3:
                pass
        try:
            connMeta.root.modify_file(filename, v[0] + 1, hashlist)
        except Exception as e:
            if e.error_type == 1 or e.error_type == 3:
                mhashlist = e.missing_blocks 
                for h in mhashlist:
                    servernum = self.findServer(h)
                    conn = rpyc.connect(self.hostNum[servernum], self.portNum[servernum])
                    conn.root.store_block(h, blockarr[hashlist.index(h)])
            mhashlist = connMeta.root.modify_file(filename, v[0] + 1, hashlist)

        print('OK')
    """
    delete(filename) : Signals the MetadataStore to delete a file.
    """
    def delete(self, filename):
        connMeta = rpyc.connect(self.metadataHost, self.metadataPort)
        try:
            v, hashlist = connMeta.root.read_file(filename)
        except Exception as e:
            if e.error_type == 3:
                return
        connMeta.root.delete_file(filename, v + 1)
        print("OK")

    """
        download(filename, dst) : Downloads a file (f) from SurfStore and saves
        it to (dst) folder. Ensures not to download unnecessary blocks.
    """
    def download(self, filename, location):
        connMeta = rpyc.connect(self.metadataHost, self.metadataPort)
        
        if location[:-1] != '/':
            location += '/'
        filepath = location + filename

        blockarrori = []
        ohashlist = []
        if os.path.isfile(filepath):
            BLOCK_SIZE = 4096
            filenum = 0

            file = open(filepath, 'rb')
            block = file.read(BLOCK_SIZE)  
            while block:
                blockarrori.append(block)
                ohashlist.append(hashlib.sha256(block).hexdigest())
                filenum += 1
                block = file.read(BLOCK_SIZE)

        try:
            item = connMeta.root.read_file(filename)
            nhashlist = item[1]
        except Exception as e:
            if e.error_type == 3:
                print(e)
                return
        if not nhashlist:
            print("Not Found")
            return
        blockresult = []

        #determine if the block is available locally and download remaining ones
        for h in nhashlist:
            if h in ohashlist:
                blockresult.append(blockarrori[ohashlist.index(h)])
            else:
                servernum = self.findServer(h)
                conn = rpyc.connect(self.hostNum[servernum], self.portNum[servernum])
                blockresult.append(conn.root.get_block(h))

        file = open(filepath, 'wb')
        for block in blockresult:
            file.write(block)
        file.close()
        print('OK')

    """
     Use eprint to print debug messages to stderr
     E.g - 
     self.eprint("This is a debug message")
    """
    def eprint(*args, **kwargs):
        print(*args, file=sys.stderr, **kwargs)



if __name__ == '__main__':
    client = SurfStoreClient(sys.argv[1])
    operation = sys.argv[2]
    if operation == 'upload':
        client.upload(sys.argv[3])
    elif operation == 'download':
        client.download(sys.argv[3], sys.argv[4])
    elif operation == 'delete':
        client.delete(sys.argv[3])
    else:
        print("Invalid operation")
        