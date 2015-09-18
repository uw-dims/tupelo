package edu.uw.apl.tupelo.utils;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uw.apl.commons.tsk4j.filesys.Attribute;
import edu.uw.apl.commons.tsk4j.filesys.DirectoryWalk;
import edu.uw.apl.commons.tsk4j.filesys.FileSystem;
import edu.uw.apl.commons.tsk4j.filesys.Meta;
import edu.uw.apl.commons.tsk4j.filesys.Walk;
import edu.uw.apl.commons.tsk4j.filesys.WalkFile;
import edu.uw.apl.commons.tsk4j.image.Image;
import edu.uw.apl.commons.tsk4j.volsys.Partition;
import edu.uw.apl.commons.tsk4j.volsys.VolumeSystem;

/**
 * Class for accessing and hashing information on a disk not managed in
 * a Tupelo store
 *
 */
public class DiskHashUtils {
	
	// Hashing variables
	static MessageDigest MD5 = null;
	static {
		try {
			MD5 = MessageDigest.getInstance( "md5" );
		} catch( NoSuchAlgorithmException never ) {
		}
	}
	static byte[] DIGESTBUFFER = new byte[ 1024*1024 ];
	
	private final String path;
	private final Image image;
	private boolean debug = true;
	
	/**
	 * Create a new UnmanagedDisk
	 * @param path the path to the source image/disk
	 * @throws IOException if the source can not be read
	 */
	public DiskHashUtils(String path) throws IOException {
		this.path = path;
		image = new Image(path);
	}
	
	/**
	 * Turn debug printing on/off
	 * @param debug
	 */
	public void setDebug(boolean debug){
		this.debug = debug;
	}
	
	/**
	 * Get the SleuthKit image
	 * @return
	 */
	public Image getImage(){
		return image;
	}
	
	/**
	 * Get the path to the disk
	 * @return
	 */
	public String getPath(){
		return path;
	}
	
	/**
	 * Close the underlying image
	 */
	public void close(){
		image.close();
	}
	
	/**
	 * Get the list of partitions within the disk. <br />
	 * This will return null if there are no partitions, IE its a raw filesystem
	 * @return
	 */
	public List<Partition> getPartitions(){
		// Try and get the partitions as a volume system
		// If that fails, return null
		try{
			VolumeSystem volumeSystem = new VolumeSystem(image);
			return volumeSystem.getPartitions();
		} catch(Exception e){
			return null;
		}
	}
	
	/**
	 * Get all the FileSystems in the disk
	 * @return the list of FileSystems
	 * @throws IOException
	 */
	public List<FileSystem> getFilesystems() throws IOException {
		List<FileSystem> filesystems = new ArrayList<FileSystem>();
		
		// Get the parititons
		List<Partition> partitions = getPartitions();
		// If we have partitions, create a filesystem for all allocated partitions
		if(partitions != null){
			for(Partition partition : partitions){
				FileSystem fs = getFileSystem(partition);

				if(fs != null){
					filesystems.add(fs);
				}
			}
		} else {
			// No partitions, create a filesystem from the image
			FileSystem fs = new FileSystem(image);
			filesystems.add(fs);
		}
		
		return filesystems;
	}
	
	/**
	 * Get a FileSystem from a Partition. <br />
	 * If the parition is unallocated or null, it will return null
	 * 
	 * @param partition
	 * @return
	 * @throws IOException
	 */
	public FileSystem getFileSystem(Partition partition) throws IOException {
		// If the partition is null or unallocated, stop now
		if(partition == null || !partition.isAllocated()){
			return null;
		}
		return new FileSystem(image, partition.start());
	}
	
	/**
	 * Tries to get a filesystem of just the image.
	 * If the image has more than one partition, this will return null
	 * 
	 * @return
	 * @throws IOException
	 */
	public FileSystem getFileSystem() throws IOException {
		List<Partition> partitions = getPartitions();
		
		if(partitions == null){
			// No partitions, wrap the image in a FileSystem
			return new FileSystem(image);
		} else if(partitions.size() == 1){
			// Only one partition, return its FileSystem
			return getFileSystem(partitions.get(0));
		}
		
		// All other cases, return null
		return null;
	}
	
	/**
	 * Walk the FileSystem and get a map of <FilePath, MD5 byte> hashes.
	 * The FileSystem will be closed when done!
	 * @param fs the FileSystem
	 * @return Map of <FilePath, MD5 hash>
	 */
	public Map<String, byte[]> hashFileSystem(FileSystem fs) throws IOException {
		final Map<String, byte[]> fileHashes = new HashMap<String, byte[]>(); 
		
		DirectoryWalk.Callback callBack = new DirectoryWalk.Callback() {
			public int apply(WalkFile f, String path) {
				try {
					hashFile(f, path, fileHashes);
					return Walk.WALK_CONT;
				} catch (Exception e) {
					System.err.println(e);
					return Walk.WALK_ERROR;
				}
			}
		};
		int flags = DirectoryWalk.FLAG_NONE;
		// LOOK: visit deleted files too ??
		flags |= DirectoryWalk.FLAG_ALLOC;
		flags |= DirectoryWalk.FLAG_RECURSE;
		flags |= DirectoryWalk.FLAG_NOORPHAN;
		fs.dirWalk(fs.rootINum(), flags, callBack);
		fs.close();
		
		return fileHashes;
	}
	
	/**
	 * Process and get the MD5 hash for a file
	 * @param file the file
	 * @param path the file's path
	 * @param fileHashes the map to store the hash
	 * @throws IOException
	 */
	private void hashFile( WalkFile file, String path,
						  Map<String,byte[]> fileHashes )
		throws IOException {

		String name = file.getName();
		// Skip null names or parent/current directory
		if( name == null ){
			return;
		} else if(	"..".equals( name ) || ".".equals( name ) ) {
			return;
		}

		Meta metaData = file.meta();
		if( metaData == null )
			return;
		// LOOK: hash directories too ??
		if( metaData.type() != Meta.TYPE_REG ){
			return;
		}
		Attribute attribute = file.getAttribute();
		// Seen some weirdness where an allocated file has no attribute(s) ??
		if( attribute == null ){
			return;
		}

		if( debug ){ 
			System.out.println( "'" + path + "' '" + name + "'" );
		}

		String wholeName = path + name;
		// Put the has in the map
		byte[] digest = digest( attribute );
		fileHashes.put( wholeName, digest );
	}
	
	/**
	 * Get the MD5 has of an attribute
	 * @param attribute
	 * @return
	 * @throws IOException
	 */
	private byte[] digest( Attribute attribute ) throws IOException {
		MD5.reset();
		InputStream inputStream = attribute.getInputStream();
		DigestInputStream digestInputStream = new DigestInputStream( inputStream, MD5 );
		while( true ) {
			if( digestInputStream.read( DIGESTBUFFER ) < 0 )
				break;
		}
		return MD5.digest();
	}

}
