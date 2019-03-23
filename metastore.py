import rpyc
import sys
from threading import Thread, Lock
import time

'''
A sample ErrorResponse class. Use this to respond to client requests when the request has any of the following issues - 
1. The file being modified has missing blocks in the block store.
2. The file being read/deleted does not exist.
3. The request for modifying/deleting a file has the wrong file version.

You can use this class as it is or come up with your own implementation.
'''
class ErrorResponse(Exception):
    def __init__(self, message):
        super(ErrorResponse, self).__init__(message)
        self.error = message
        self.error_type = 0


    def missing_blocks(self, hashlist):
        self.error_type = 1
        self.missing_blocks = hashlist

    def wrong_version_error(self, version):
        self.error_type = 2
        self.current_version = version

    def file_not_found(self):
        self.error_type = 3



'''
The MetadataStore RPC server class.

The MetadataStore process maintains the mapping of filenames to hashlists. All
metadata is stored in memory, and no database systems or files will be used to
maintain the data.
'''
class MetadataStore(rpyc.Service):
    

    """
        Initialize the class using the config file provided and also initialize
        any datastructures you may need.
    """
    def __init__(self, config):
        file  = open(config, 'r')
        lines = file.readlines()
        self.mutex = Lock()
        self.portNum = []
        self.hostNum = []

        #take the host and port numbers inside config.txt
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

        self.versionMap = {}
        self.blockListMap = {}
        self.lastApplied = -1

    def findServer(self, h):
        return int(h,16) % self.numBlockStores
        
    '''
        ModifyFile(f,v,hl): Modifies file f so that it now contains the
        contents refered to by the hashlist hl.  The version provided, v, must
        be exactly one larger than the current version that the MetadataStore
        maintains.

        As per rpyc syntax, adding the prefix 'exposed_' will expose this
        method as an RPC call
    '''
    def exposed_modify_file(self, filename, version, hashlist):
        self.mutex.acquire();
        if version != 1:
            sVersion = self.versionMap[filename]

            if version != sVersion + 1:
                ErrRes = ErrorResponse('wrong version error')
                ErrRes.wrong_version_error(sVersion)
                self.mutex.release()
                raise ErrRes 
        state = False
        missingBlocks = []
        hashlistt = []
        for h in hashlist:
            s = str(h)
            hashlistt.append(s) 

        #check if the block is present in blockstore
        for m_hash in hashlistt:
            servernum = self.findServer(m_hash)
            conn = rpyc.connect(self.hostNum[servernum], self.portNum[servernum])
            if not conn.root.has_block(m_hash):
                state = True
                missingBlocks.append(m_hash)

        #return missing block
        if missingBlocks:
            ErrRes = ErrorResponse('missing blocks')
            ErrRes.missing_blocks(tuple(missingBlocks))
            self.mutex.release()
            raise ErrRes 

        # modify
        if state == False:
            self.versionMap[filename] = version
            self.blockListMap[filename] = hashlistt
            self.lastApplied += 1
        self.mutex.release()

    '''
        DeleteFile(f,v): Deletes file f. Like ModifyFile(), the provided
        version number v must be one bigger than the most up-date-date version.

        As per rpyc syntax, adding the prefix 'exposed_' will expose this
        method as an RPC call
    '''
    def exposed_delete_file(self, filename, version):
        
        try:
            sVersion = self.versionMap[filename]
        except Exception:
            ErrRes = ErrorResponse('Not Found')
            ErrRes.file_not_found()
            raise ErrRes 
            
        if version != sVersion + 1:
            ErrRes = ErrorResponse('wrong version error')
            ErrRes.wrong_version_error(sVersion)
            raise ErrRes 

        # delete operation
        self.versionMap[filename] = version
        self.blockListMap[filename] = []
        

    '''
        (v,hl) = ReadFile(f): Reads the file with filename f, returning the
        most up-to-date version number v, and the corresponding hashlist hl. If
        the file does not exist, v will be 0.

        As per rpyc syntax, adding the prefix 'exposed_' will expose this
        method as an RPC call
    '''

    def exposed_read_file(self, filename):
        try:
            version = self.versionMap[filename]
        except:
            return (0, [])
        
        blockList = self.blockListMap[filename]

        return (version, blockList)
        


if __name__ == '__main__':
    from rpyc.utils.server import ThreadedServer

    file  = open(sys.argv[1], 'r')
    lines = file.readlines()
    for line in lines:
            value = line.split(': ')
            if value[0] == 'metadata':
                item = value[1].split(":")
                port = item[1]
    try: 
        port = int(port)
    except Exception:
        port = 6000

    server = ThreadedServer(MetadataStore(sys.argv[1]), port = port )
    server.start()

