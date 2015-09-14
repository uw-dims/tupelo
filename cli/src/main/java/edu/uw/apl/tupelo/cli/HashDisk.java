package edu.uw.apl.tupelo.cli;

import java.io.File;
import java.io.BufferedWriter;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.cli.*;
import org.apache.commons.codec.binary.Hex;
import org.apache.log4j.LogManager;

import edu.uw.apl.tupelo.model.ManagedDiskDescriptor;
import edu.uw.apl.tupelo.store.Store;
import edu.uw.apl.tupelo.fuse.ManagedDiskFileSystem;
import edu.uw.apl.commons.sleuthkit.image.Image;
import edu.uw.apl.commons.sleuthkit.filesys.Attribute;
import edu.uw.apl.commons.sleuthkit.filesys.Meta;
import edu.uw.apl.commons.sleuthkit.filesys.FileSystem;
import edu.uw.apl.commons.sleuthkit.filesys.DirectoryWalk;
import edu.uw.apl.commons.sleuthkit.filesys.Walk;
import edu.uw.apl.commons.sleuthkit.filesys.WalkFile;
import edu.uw.apl.commons.sleuthkit.volsys.Partition;
import edu.uw.apl.commons.sleuthkit.volsys.VolumeSystem;

/**
 * Simple Tupelo Utility: Hash some previously added ManagedDisk,
 * using tsk4j/Sleuthkit routines and a FUSE filesystem to access the
 * managed data.  Store the resultant 'Hash Info' as a Store
 * attribute.
 *
 * Note: This program makes use of fuse4j/fuse and so has an impact on
 * the filesystem as a whole.  In principle, a fuse mount point is
 * created at program start and deleted at program end.  However, if
 * user exits early (Ctrl C), we may have a lasting mount point.  To
 * delete this, do
 *
 * $ fusermount -u test-mount
 *
 * We do have a shutdown hook for the umount installed, but it appears
 * unreliable.
 *
 */

public class HashDisk extends CliBase {
	
	String diskPath;
	static boolean verbose;

	static byte[] DIGESTBUFFER = new byte[ 1024*1024 ];
	static MessageDigest MD5 = null;
	static {
		try {
			MD5 = MessageDigest.getInstance( "md5" );
		} catch( NoSuchAlgorithmException never ) {
		}
	}

	static public void main( String[] args ) {
		HashDisk main = new HashDisk();
		try {
			main.readArgs( args );
			main.start();
		} catch( Exception e ) {
			System.err.println( e );
			if( debug )
				e.printStackTrace();
			System.exit(-1);
		} finally {
			LogManager.shutdown();
		}
			  
	}

	public HashDisk() {
	}

	public void readArgs( String[] args ) {
		Options os = commonOptions();
		os.addOption( "v", false, "Verbose" );

		String usage = commonUsage() + "[-v] diskID sessionID";
		final String HEADER = "";
		final String FOOTER = "";
		CommandLineParser clp = new PosixParser();
		CommandLine cl = null;
		try {
			cl = clp.parse( os, args );
		} catch( ParseException pe ) {
			printUsage( os, usage, HEADER, FOOTER );
			System.exit(1);
		}
		commonParse( os, cl, usage, HEADER, FOOTER );

		verbose = cl.hasOption( "v" );
		args = cl.getArgs();
		if( args.length < 1 ) {
			printUsage( os, usage, HEADER, FOOTER );
			// System.exit(1);
		} else {
			diskPath = args[0];
		}
	}
	
	public void start() throws Exception {

//		Store store = Utils.buildStore( storeLocation );
//		if( debug )
//			System.out.println( "Store type: " + store );
//
//		Collection<ManagedDiskDescriptor> stored = store.enumerate();
//		System.out.println( "Stored: " + stored );
//
//		ManagedDiskDescriptor managedDiskDescriptor = Utils.locateDescriptor( store, diskID,
//															sessionID );
//		if( managedDiskDescriptor == null ) {
//			System.err.println( "Not stored: " + diskID + "," + sessionID );
//			System.exit(1);
//		}

		
		System.out.println( "Trying to open " + diskPath);
		Image image = new Image( diskPath );

		System.out.println( "Trying volume system on "+ diskPath );
		try {
			boolean b = walkVolumeSystem( image );
			if( !b ) {
				System.out.println( "Trying file system on " + diskPath );
				walkFileSystem( image );
			}
		} finally {
			// MUST release i else leaves mdfs non-unmountable
			image.close();
		}
	}
	

	/**
	 * @return true if a volume system found (and thus traversed), false
	 * otherwise.  False result lets us try the image as a standalone
	 * filesystem
	 */
	private boolean walkVolumeSystem( Image image ) throws Exception {

		VolumeSystem volumeSystem = null;
		try {
			volumeSystem = new VolumeSystem( image );
		} catch( Exception iae ) {
			return false;
		}
		
		List<Partition> partitions = volumeSystem.getPartitions();
		try {
			for( Partition partition : partitions ) {
				if( !partition.isAllocated() )
					continue;
				System.out.println( "At sector " + partition.start() +
									", located " + partition.description() );
				Map<String,byte[]> fileHashes = new HashMap<String,byte[]>();
				FileSystem fileSystem = new FileSystem( image, partition.start() );
				walk( fileSystem, fileHashes );
				fileSystem.close();
				System.out.println( " FileHashes : " + fileHashes.size() );
				record( fileHashes, partition.start(), partition.length() );
			}
		} finally {
			// MUST release volumeSystem else leaves mdfs non-unmountable
			volumeSystem.close();
		}
		return true;
	}

	/**
	 * Walk and record all the MD5 hashes of an image 
	 * @param image
	 * @throws Exception
	 */
	private void walkFileSystem( Image image ) throws Exception {
		Map<String,byte[]> fileHashes = new HashMap<String,byte[]>();
		FileSystem fs = new FileSystem( image );
		try {
			walk( fs, fileHashes );
			System.out.println( "FileHashes: " + fileHashes.size() );
			// signify a standalone file system via a 0,0 sector interval
			record( fileHashes, 0, 0 );
		} finally {
			fs.close();
		}
	}
	
	/**
	 * Walk a mounted filesystem
	 * @param fs
	 * @param fileHashes
	 * @throws Exception
	 */
	private void walk( FileSystem fs,
					   final Map<String,byte[]> fileHashes )
		throws Exception {
		
		DirectoryWalk.Callback callBack = new DirectoryWalk.Callback() {
				public int apply( WalkFile f, String path ) {
					try {
						process( f, path, fileHashes );
						return Walk.WALK_CONT;
					} catch( Exception e ) {
						System.err.println( e );
						return Walk.WALK_ERROR;
					}
				}
			};
		int flags = DirectoryWalk.FLAG_NONE;
		// LOOK: visit deleted files too ??
		flags |= DirectoryWalk.FLAG_ALLOC;
		flags |= DirectoryWalk.FLAG_RECURSE;
		flags |= DirectoryWalk.FLAG_NOORPHAN;
		fs.dirWalk( fs.rootINum(), flags, callBack );
		fs.close();
	}

	/**
	 * Process and get the MD5 hash for a file
	 * @param file the file
	 * @param path the file's path
	 * @param fileHashes the map to store the hash
	 * @throws IOException
	 */
	private void process( WalkFile file, String path,
						  Map<String,byte[]> fileHashes )
		throws IOException {

		String name = file.getName();
		if( name == null )
			return;
		if(	"..".equals( name ) || ".".equals( name ) ) {
			return;
		}
		Meta metaData = file.meta();
		if( metaData == null )
			return;
		// LOOK: hash directories too ??
		if( metaData.type() != Meta.TYPE_REG )
			return;
		Attribute attribute = file.getAttribute();
		// Seen some weirdness where an allocated file has no attribute(s) ??
		if( attribute == null )
			return;

		if( debug )
			System.out.println( "'" + path + "' '" + name + "'" );

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
	
	/**
	 * Write all the hashes and info about this session to a file
	 * @param fileHashes the file/hash pairs to write
	 * @param start
	 * @param length
	 * @throws Exception
	 */
	private void record( Map<String,byte[]> fileHashes,
						 long start, long length )
		throws Exception {

		List<String> sorted = new ArrayList<String>( fileHashes.keySet() );
		Collections.sort( sorted );
		String outName = diskPath + "-" +
			start + "-" + length + ".md5";
		// Use underscore instead of /, so we don't write the file under /dev or some other directory somewhere
		outName = outName.replace('/', '_');
		System.out.println( "Writing: " + outName );
		
		// Write all the data out
		FileWriter fileWriter = new FileWriter( outName );
		BufferedWriter bufferedWrite = new BufferedWriter( fileWriter, 1024 * 1024 );
		PrintWriter printWriter = new PrintWriter( bufferedWrite );
		for( String fName : sorted ) {
			byte[] hash = fileHashes.get( fName );
			String s = new String( Hex.encodeHex( hash ) );
			printWriter.println( s + " " + fName );
		}
		// Flush and close everything
		printWriter.flush();
		printWriter.close();
	}

}
