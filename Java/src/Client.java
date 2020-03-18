import java.util.*;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.apache.xmlrpc.*;
import java.math.BigInteger;

public class Client {

    private static String serverAddress; // ip:port
    private static String ipAddress; // ip
    private static int port; // port
    private static String baseDir; // Base directory
    private static int blockSize; // Block size

    // Index and metadata: Map<filename, Vector<version, Vector<hashlist>>>
    private static Map<String, Vector> localIndex; // Local index file
    private static Map<String, Vector> metadata; // Base Directory files metadata
    private static Map<String, Vector> remoteIndex; // Remote index
    private static Map<String, Vector> newIndex; // New index to write to file

    // Map<filename, Vector<Vector<hashlist>, Vector<blocks>>>
    private static Map<String, Vector> filesData;

    /**
     * Get the local index file or create if it does not exists.
     * @return Local index file
     */
    public static File getOrCreateIndexFile() {
        File indexFile = new File(baseDir + "/index.txt");
        try {
            if (!indexFile.exists()) {
                indexFile.createNewFile();
            }
        } catch (IOException e) {
            System.err.println("Index file creation error: " + e);
        }
        return indexFile;
    }

    /**
     * Get the index metadata of contents in the index file.
     * @param indexFile Local index file
     * @return Index metadata
     */
    public static Map<String, Vector> getIndexMetadata(File indexFile) {
        Map<String, Vector> index = new Hashtable<String, Vector>();
        try {
            BufferedReader br = new BufferedReader(new FileReader(indexFile));
            String line;
            while ((line = br.readLine()) != null) {
                String[] strs = line.split(",");
                String filename = strs[0];
                Integer version = new Integer(strs[1]);
                String[] hashstring = strs[2].split(" ");
                Vector<String> hashlist = new Vector<String>();
                for (int i = 0; i < hashstring.length; i++) {
                    if (hashstring[i].equals("0")) continue;
                    hashlist.add(hashstring[i]);
                }
                Vector fileinfo = new Vector();
                fileinfo.add(version);
                fileinfo.add(hashlist);
                index.put(filename, fileinfo);
            }
            br.close();
        } catch (Exception e) {
            System.err.println("Get local index metadata: " + e);
        }
        return index;
    }

    /**
     * Get the metadata of files in the base directory.
     * @return Mapping of filename to version number and hashlist
     */
    public static Map<String, Vector> getFilesMetadata() {
        Map<String, Vector> metadata = new Hashtable<String, Vector>();
        for (String filename : filesData.keySet()) {
            Vector fileinfo = new Vector();
            Integer version = new Integer(0);
            // Check if it is a new file
            if (!localIndex.containsKey(filename)) {
                version = new Integer(1);
            // Check if the file is modified
            } else if (diffHashlist((Vector<String>) filesData.get(filename).get(0), (Vector<String>) localIndex.get(filename).get(1))) {
                version = (int) localIndex.get(filename).get(0) + 1;
            // File is not modified
            } else {
                version = (int) localIndex.get(filename).get(0);
            }
            fileinfo.add(version);
            fileinfo.add(filesData.get(filename).get(0));
            metadata.put(filename, fileinfo);
        }
        // Check if any file is deleted
        for (String filename : localIndex.keySet()) {
            if (!metadata.containsKey(filename)) {
                Vector fileinfo = new Vector();
                System.out.println("Here");
                Integer version = (int) localIndex.get(filename).get(0);
                if ( ((Vector<String>) localIndex.get(filename).get(1)).size() > 0) version += 1;
                fileinfo.add(version);
                fileinfo.add(new Vector<String>());
                metadata.put(filename, fileinfo);
                Vector hashlistblocks = new Vector();
                hashlistblocks.add(new Vector<String>());
                hashlistblocks.add(new Vector<byte[]>());
                filesData.put(filename, hashlistblocks);
            }
        }
        return metadata;
    }

    /**
     * Get the remote index on the server using RPC.
     * @return Mapping of filename to version number
     */
    public static Map<String, Vector> getRemoteIndex() {
        remoteIndex = new Hashtable<String, Vector>();
        try {
            String address = "http://" + serverAddress + "/RPC2";
            XmlRpcClient client = new XmlRpcClient(address);
            Vector params = new Vector();
            remoteIndex = (Map<String, Vector>) client.execute("surfstore.getfileinfomap", params);
        } catch (Exception e) {
            System.err.println("Get remote index: " + e);
        }
        return remoteIndex;
    }

    /**
     * Write given index to a file.
     * @param index Mapping of filename to version number and hashlist
     * @param indexFile File object to write the index to
     */
    public static void writeIndex(Map<String, Vector> index, File indexFile) {
        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter(indexFile));
            for (String filename : index.keySet()) {
                String line = filename;
                int version = (int) index.get(filename).get(0);
                line = line + "," + version + ",";
                Vector<String> hashlist = (Vector<String>) index.get(filename).get(1);
                if (hashlist.isEmpty()) {
                    line = line + "0 ";
                }
                for (String hash : hashlist) {
                    line = line + hash + " ";
                }
                line = line.substring(0, line.length() - 1); // Remove last space
                bw.write(line + "\n");
            }
            bw.close();
        } catch (Exception e) {
            System.err.println("Write to index file: " + e);
        }
    }

    /**
     * Print the contents of the given index.
     * @param index Mapping filename to version number and hashlist
     */
    public static void printIndex(Map<String, Vector> index) {
        for (String filename : index.keySet()) {
            String line = filename;
            int version = (int) index.get(filename).get(0);
            line = line + " " + version;
            Vector<String> hashlist = (Vector<String>) index.get(filename).get(1);
            for (String hash : hashlist) {
                line = line + " " + hash;
            }
            System.out.println(line);
        }
    }

    /**
     * Get the files in the base directory.
     * @return Mapping of filename to hashlist and blocklist
     */
    public static Map<String, Vector> getFilesData() {
        Map<String, Vector> filesData = new Hashtable<String, Vector>();
        File dir = new File(baseDir);
        File[] files = dir.listFiles();
        for (File file : files) {
            if (file.getName().equals("index.txt")) continue;
            Vector<byte[]> blocks = splitFile(file);
            Vector<String> hashlist = new Vector<String>();
            for (byte[] block : blocks) {
                hashlist.add(hash(block));
            }
            Vector hashlistblocks = new Vector();
            hashlistblocks.add(hashlist);
            hashlistblocks.add(blocks);
            filesData.put(file.getName(), hashlistblocks);
        }
        return filesData;
    }
    /**
     * Split a file into byte array chunks of size at most blockSize.
     * @param file The file to split
     * @return Vector containing byte array chunks of the file
     */
    public static Vector<byte[]> splitFile(File file) {
        Vector<byte[]> listOfBlocks = new Vector<byte[]>();
        int fileSize = (int) file.length();
        try {
            int currBlockSize = Math.min(blockSize, fileSize);
            byte[] buffer = new byte[currBlockSize];
            FileInputStream fis = new FileInputStream(file);
            BufferedInputStream bis = new BufferedInputStream(fis);
            int bytesRead = 0;
            while ((bytesRead = bis.read(buffer)) > 0) {
                listOfBlocks.add(buffer);
                fileSize -= bytesRead;
                currBlockSize = Math.min(blockSize, fileSize);
                buffer = new byte[currBlockSize];
            }
        } catch (Exception e) {
            System.err.println("Split File Exception: " + e);
        }
        return listOfBlocks;
    }

    /**
     * Calculate hash value of a byte array.
     * @param blockData The byte array chunk
     * @return SHA-256 hash value in string format
     */
    public static String hash(byte[] blockData) {
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
            System.err.println("Hash block data: " + e);
        }
        return hashvalue;
    }

    /**
     * Check if there is difference between two given hashlists.
     * @param h1 Hashlist 1
     * @param h2 Hashlist 2
     * @return True if there is difference between the two hashlists.
     */
    public static boolean diffHashlist(Vector<String> h1, Vector<String> h2) {
        if (h1.size() != h2.size()) return true;
        for (int i = 0; i < h1.size(); i++) {
            if (!h1.get(i).equals(h2.get(i))) return true;
        }
        return false;
    }

    /**
     * Upload the given filename to the server using RPC.
     * It will first update metadata of the server's index and then put the blocks on the server.
     * @param filename Name of the file to upload
     */
    public static void uploadFile(String filename) {
        try {
            String address = "http://" + serverAddress + "/RPC2";
            XmlRpcClient client = new XmlRpcClient(address);
            Vector params = new Vector();
            params.add(filename); // filename
            params.add(metadata.get(filename).get(0)); // Version number
            params.add(metadata.get(filename).get(1)); // Hashlist
            boolean status = (boolean) client.execute("surfstore.updatefile", params);
            // Only upload blocks if the remote index update is successful
            if (status) {
                System.out.println("Uploading: " + filename);
                Vector<byte[]> blocks = (Vector<byte[]>) filesData.get(filename).get(1);
                for (byte[] block : blocks) {
                    params = new Vector();
                    params.add(block);
                    client.execute("surfstore.putblock", params);
                }
                Vector fileinfo = new Vector();
                // Update the new local index
                newIndex.put(filename, metadata.get(filename));
            }
        } catch (Exception e) {
            System.err.println("Upload file: " + e);
        }
    }

    /**
     * Download the given filename.
     * @param filename Name of the file to download
     * @param version Version number of the file to download
     * @param hashlist List of hashes of the blocks corresponding to this file
     */
    public static void downloadFile(String filename, int version, Vector<String> hashlist) {
        try {
            String address = "http://" + serverAddress + "/RPC2";
            XmlRpcClient client = new XmlRpcClient(address);
            Vector params = new Vector();
            params.add(hashlist); // hashlist
            Vector<String> availableHash = (Vector<String>) client.execute("surfstore.hasblocks", params);
            // Check if all required hashes are available on the server
            if (availableHash.size() == hashlist.size()) {
                Vector<byte[]> blocks = new Vector<byte[]>();
                for (String hashvalue : hashlist) {
                    params = new Vector();
                    params.add(hashvalue);
                    byte[] block = (byte[]) client.execute("surfstore.getblock", params);
                    blocks.add(block);
                }
                // Update filesData
                Vector hashlistblocks = new Vector();
                hashlistblocks.add(hashlist);
                hashlistblocks.add(blocks);
                filesData.put(filename, hashlistblocks);
                // Create file from blocks
                constructFile(filename);
                // Update new local index
                Vector fileinfo = new Vector();
                fileinfo.add(version);
                fileinfo.add(hashlist);
                newIndex.put(filename, fileinfo);
            } else {
                System.out.println("Download file: Server does not have those hashlist");
            }
        } catch (Exception e) {
            System.err.println("Upload file: " + e);
        }

    }

    /**
     * Construct file using the blocks.
     * @param filename Name of file to construct
     */
    public static void constructFile(String filename) {
        // Delete file if hashlist is empty
        Vector<String> hashlist = (Vector<String>) remoteIndex.get(filename).get(1);
        if (hashlist.isEmpty()) {
            // Check if file exists and delete if it does.
            File file = new File(baseDir + "/" + filename);
            if (file.exists()) {
                file.delete();
                return;
            }
            // If file does not exists (already deleted locally) and not construct file.
            return;
        }
        // Create file
        try {
            File file = new File(baseDir + "/" + filename);
            FileOutputStream fos = new FileOutputStream(file);
            BufferedOutputStream bos = new BufferedOutputStream(fos);
            for (byte[] block : (Vector<byte[]>) filesData.get(filename).get(1)) {
                bos.write(block);
            }
            bos.close();
        } catch (Exception e) {
            System.err.println("Construct file: " + e);
        }
    }

    /**
     * Check which files to upload to server
     */
    public static void uploadSync() {
        for (String filename : metadata.keySet()) {
            // Check if there is new file or modified file in the base directory
            if (!remoteIndex.containsKey(filename) || (int) remoteIndex.get(filename).get(0) < (int) metadata.get(filename).get(0)) {
                uploadFile(filename);
            }
        }
    }

    /**
     * Check which files to download from server.
     */
    public static void downloadSync() {
        // Check if there is new file or there is modified file in server
        for (String filename : remoteIndex.keySet()) {
            if (!localIndex.containsKey(filename) || (int) localIndex.get(filename).get(0) < (int) remoteIndex.get(filename).get(0)) {
                System.out.println("Downloading: " + filename);
                int version = (int) remoteIndex.get(filename).get(0);
                Vector<String> hashlist = (Vector<String>) remoteIndex.get(filename).get(1);
                downloadFile(filename, version, hashlist);
            }
        }
    }

    /**
     * Copy index content of files that stays the same to the new index.
     */
    public static void indexSync() {
        for (String filename : metadata.keySet()) {
            if (!newIndex.containsKey(filename)) {
                System.out.println("Index Sync: " + filename);
                newIndex.put(filename, metadata.get(filename));
            }
        }
    }

    public static void main (String [] args) {
        // Check if user supplied all the command line arguments required
        if (args.length != 3) {
            System.err.println("Usage: Client host:port /basedir blockSize");
            System.exit(1);
        }

        // Parse command line input arguments
        System.out.println(Arrays.toString(args));
        serverAddress = args[0];
        String[] ipPort = args[0].split(":"); // Get server IP and port
        ipAddress = ipPort[0];
        port = Integer.parseInt(ipPort[1]);
        baseDir = args[1]; // Get base directory to sync with
        blockSize = Integer.parseInt(args[2]); // Get block size

        // Local index file
        File indexFile = getOrCreateIndexFile();
        localIndex = getIndexMetadata(indexFile);

        System.out.println("Initial Local Index Content");
        printIndex(localIndex);
        System.out.println();

        // Get files data and metadata of files in base directory
        filesData = getFilesData();
        metadata = getFilesMetadata();

        System.out.println("Metadata");
        printIndex(metadata);
        System.out.println();

        // Remote Index
        Map<String, Vector> remoteIndex = getRemoteIndex();
        System.out.println("Initial Remote Index Content");
        printIndex(remoteIndex);
        System.out.println();

        // New Index
        newIndex = new Hashtable<String, Vector>();

        // Download Sync
        System.out.println("Download Sync");
        downloadSync();
        System.out.println();

        // Upload Sync
        System.out.println("Upload Sync");
        uploadSync();
        System.out.println();

        // Index Sync
        System.out.println("Index Sync");
        indexSync();
        System.out.println();

        System.out.println("New Local Index");
        printIndex(newIndex);
        System.out.println();

        // Write new index to file
        System.out.println("Writing New Index to File");
        writeIndex(newIndex, indexFile);
        System.out.println();

        // New remote index
        System.out.println("New Remote Index");
        remoteIndex = getRemoteIndex();
        printIndex(remoteIndex);
        System.out.println();
    }
}
