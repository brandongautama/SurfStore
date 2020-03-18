import org.apache.xmlrpc.*;
import java.util.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.math.BigInteger;
public class Server {

    // Map<hashvalue, block>
    private Map<String, byte[]> blockStore; // Store mappings of hash value to blocks

    // Map<filename, Vector<version, hashlist>>
    private Map<String, Vector> metaStore; // Store mappings of filenames to vector (version no, hashlist)

    /**
     * Constructor.
     */
    public Server() {
        blockStore = new Hashtable<String, byte[]>();
        metaStore = new Hashtable<String, Vector>();
    }

	/*
     * A simple ping, simply returns True.
     * @return True ping
     */
	public boolean ping() {
		System.out.println("Ping()");
		return true;
	}

    /**
	 * Given a hash value, return the associated block.
     * @param hashvalue String format of the SHA-256 hash
     * @return Byte array chunk block
     */
	public byte[] getblock(String hashvalue) {
		System.out.println("GetBlock(" + hashvalue + ")");
        byte[] blockData = blockStore.get(hashvalue);
		return blockData;
	}

	/**
     * Store the provided block.
     * @param blockData Byte array chunk
     * @return True
     */
	public boolean putblock(byte[] blockData) {
        String hashvalue = hash(blockData);
        blockStore.put(hashvalue, blockData);
		System.out.println("PutBlock(" + hashvalue + ")");
		return true;
	}

	/**
     * Determine which of the provided blocks are on this server.
     * @param hashlist List of hash values
     * @return List of hash values that are available in this server
     */
	public Vector hasblocks(Vector hashlist) {
        Vector availableHash = new Vector();
        for (String hashvalue : (Vector<String>) hashlist) {
            System.out.println(hashvalue + " requested");
            if (blockStore.containsKey(hashvalue)) {
                availableHash.add(hashvalue);
            }
        }
		System.out.println("HasBlocks(): " + availableHash.size());
		return availableHash;
	}

	/**
     * Returns the server's FileInfoMap.
     * @return Mapping of filename to version number and hashlist
     */
	public Hashtable getfileinfomap() {
		Hashtable<String, Vector> result = new Hashtable<String, Vector>();
        for (Map.Entry<String, Vector> entry : metaStore.entrySet()) {
            result.put(entry.getKey(), entry.getValue());
        }
		System.out.println("GetFileInfoMap()");
		return result;
	}

	/**
     * Update's the given entry in the fileinfomap.
     * @param filename Name of the file to update
     * @param version Version number given
     * @param hashlist List of hash values
     * @return True if updated successfully
     */
	public boolean updatefile(String filename, int version, Vector hashlist) {
		System.out.println("UpdateFile(" + filename + ")");
        Vector fileinfo = metaStore.get(filename);

        if (fileinfo == null) {
            // Create new file if file does not exist
            fileinfo = new Vector();
            fileinfo.add(new Integer(version));
            fileinfo.add(hashlist);
            metaStore.put(filename, fileinfo);
            return true;
        }

        // If we retrieved the fileinfo from fileinfomap
        // Check version number
        if (version < (int) fileinfo.get(0) + 1) {
            System.out.println("File version is incorrect");
            return false;
        }
        // Update fileinfomap
        fileinfo = new Vector();
        fileinfo.add(new Integer(version));
        fileinfo.add(hashlist);
        metaStore.put(filename, fileinfo);
		return true;
	}

	// PROJECT 3 APIs below

	// Queries whether this metadata store is a leader
	// Note that this call should work even when the server is "crashed"
	public boolean isLeader() {
		System.out.println("IsLeader()");
		return true;
	}

	// "Crashes" this metadata store
	// Until Restore() is called, the server should reply to all RPCs
	// with an error (unless indicated otherwise), and shouldn't send
	// RPCs to other servers
	public boolean crash() {
		System.out.println("Crash()");
		return true;
	}

	// "Restores" this metadata store, allowing it to start responding
	// to and sending RPCs to other nodes
	public boolean restore() {
		System.out.println("Restore()");
		return true;
	}

	// "IsCrashed" returns the status of this metadata node (crashed or not)
	// This method should always work, even when the node is crashed
	public boolean isCrashed() {
		System.out.println("IsCrashed()");
		return true;
	}

    // Helper Methods
    public String hash(byte[] blockData) {
        String hashvalue = "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(blockData);
            BigInteger number = new BigInteger(1, hash);
            StringBuilder hexString = new StringBuilder(number.toString(16));
            while (hexString.length() < 32) {
                hexString.insert(0, '0');
            }
            hashvalue = hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            System.out.println("Hash block data: " + e);
        }
        return hashvalue;
    }

	public static void main (String [] args) {

		try {

			System.out.println("Attempting to start XML-RPC Server...");

			WebServer server = new WebServer(8080);
			server.addHandler("surfstore", new Server());
			server.start();

			System.out.println("Started successfully.");
			System.out.println("Accepting requests. (Halt program to stop.)");

		} catch (Exception exception){
			System.err.println("Server: " + exception);
		}
	}
}
