/**
 * Copyright © 2015, University of Washington
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *
 *     * Neither the name of the University of Washington nor the names
 *       of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written
 *       permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL UNIVERSITY OF
 * WASHINGTON BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package edu.uw.apl.tupelo.store.filesys;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.DriverManager;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.io.FileUtils;

import edu.uw.apl.commons.tsk4j.digests.BodyFile.Record;
import edu.uw.apl.tupelo.model.ManagedDisk;
import edu.uw.apl.tupelo.model.ManagedDiskDescriptor;
import edu.uw.apl.tupelo.model.ManagedDiskDigest;
import edu.uw.apl.tupelo.model.Session;
import edu.uw.apl.tupelo.model.ProgressMonitor;
import edu.uw.apl.tupelo.model.Utils;
import edu.uw.apl.tupelo.store.Store;

/**
   An implementation of the Tupelo Store interface which uses a flat
   file system for Store component management.  Uses a local directory
   heirarchy for managed disks and attributes.

   Note that the Store interface has no description of any
   synchronisation constraints.  That is left to us here.  Given that
   this FSStore object may be used by a webapp/server war, we need to
   pay attention to multi-threaded access.  To do this, we maintain a
   single lock, but use it more sparingly than at every method
   boundary.  Used that way, a single expensive put() blocks every
   other operation.  Instead, we still use the native lock on the
   FSStore object, but reduce the critical region size where possible.
   In particular, a put() first writes to a temp dir/file, and only
   when it is time to move into the final Store location do we require
   the 'real' lock.
*/
public class FilesystemStore implements Store {
    private boolean writable;
    
    private final UUID uuid;
    private final File root, tempDir;
    private final Map<ManagedDiskDescriptor,ManagedDisk> descriptorMap;
    private final Map<String,ManagedDisk> pathMap;
    private final Log log;

    private TempDirCleaner tempDirCleaner;

	public FilesystemStore( File root ) {
		this( root, true );
	}

	/**
	 * @param root - root directory for the Store. ALL data is held
	 * under this single root.
	 *
	 * @param loadManagedDisks - normally true, but for test cases useful to
	 * pass false so a FilesystemStore has known empty status initially
	 */
	public FilesystemStore( File root, boolean loadManagedDisks ) {
		log = LogFactory.getLog( getClass() );
		this.root = root;
		log.info( "Store.root = " + root );
		tempDir = new File( root, "temp" );
		tempDir.mkdirs();
		log.debug( "FSStore.tmp = " + tempDir );

        // Start the temp directory cleaner
        tempDirCleaner = new TempDirCleaner(tempDir);
        new Thread(tempDirCleaner).start();

		uuid = loadUUID();
		descriptorMap = new HashMap<ManagedDiskDescriptor,ManagedDisk>();
		pathMap = new HashMap<String,ManagedDisk>();
		if( loadManagedDisks )
			loadManagedDisks();
		writable = true;
        // Load the JDBC driver for the FileHashStore
        try {
            DriverManager.registerDriver(new org.sqlite.JDBC());
        } catch (Exception e) {
            // Ignore
        }
	}

	/**
	 * To protect assets in the store, we can make dirs un-writable. Default
	 * is writable, so in testing the user can wipe the store directory
	 */
	public void setWritable( boolean b ) {
		writable = b;
	}
	
	@Override
	public synchronized UUID getUUID() {
		return uuid;
	}

	@Override
	public synchronized long getUsableSpace() {
		return root.getUsableSpace();
	}

	/**
	   a new session is derived from an earlier one persisted to disk,
	   or entirely new if no persistent one available.  We persist the
	   result back to disk for future usage.  So the disk file
	   'session.txt' is part of the Store state.
	*/
	@Override
	public synchronized Session newSession() throws IOException {
		Calendar now = Calendar.getInstance( Session.UTC );
		Session result = null;
		File f = new File( root, "session.txt" );
		if( f.exists() ) {
			BufferedReader br = new BufferedReader( new FileReader( f ) );
			String line = br.readLine();
			try {
				Session saved = Session.parse( line );
				result = saved.successor( now );
			} catch( ParseException pe ) {
				log.warn( pe );
				result = new Session( uuid, now, 1 );
			} finally {
				try{
					br.close();
				} catch(Exception e){
					// Ignore
				}
			}
		} else {
			result = new Session( uuid, now, 1 );
		}
		PrintWriter pw = new PrintWriter( new FileWriter( f ) );
		pw.println( result.format() );
		pw.close();
		return result;
	}

	/**
	 * @throws IllegalStateException if the verify operation fails.
	 * If so, the temporary file used to hold the manageddisk is discarded
	 * and the store proper NOT updated
	 */
	@Override
	public synchronized void put( ManagedDisk md ) throws IOException {

		ManagedDiskDescriptor mdd = md.getDescriptor();
		if( descriptorMap.containsKey( mdd ) )
			throw new IllegalArgumentException( "Already stored: " + mdd );
		
		String fileName = dataFileName( mdd );
		File tempFile = new File( tempDir, fileName );
		log.info( "Writing to " + tempFile );
		/*
		  Since the temp file itself is to be used as a lock,
		  canonicalise it first to avoid any unintended side-stepping
		  of lock requirements.  Since this is where the expensive
		  operation occurs, we maintain the accessibility of the wider
		  store object itself...
		*/
		tempFile = tempFile.getCanonicalFile();
		synchronized( tempFile ) {
			log.debug( "Locked " + tempFile );
			FileOutputStream fos = new FileOutputStream( tempFile );
			BufferedOutputStream bos = new BufferedOutputStream( fos, 1024*64 );
			md.writeTo( bos );
			bos.flush();
			bos.close();
			fos.close();
			// Verify for that data written is complete...
			try {
				md.setManagedData( tempFile );
				md.verify();
			} catch( IllegalStateException ise ) {
				log.warn( ise );
				tempFile.delete();
				throw ise;
			}
			log.debug( "Unlocked " + tempFile );
		}

		// we are now adding to the Store proper, so need the lock....
		synchronized( this ) {
			File outDir = diskDataDir( root, mdd );
			outDir.mkdirs();
			File outFile = new File( outDir, fileName );
			log.info( "Moving to " + outFile );
			tempFile.renameTo( outFile );
			log.info( "Moved to " + outFile );
			md.setManagedData( outFile );

			// Access controls in place to guard against file system screw ups..
			outFile.setWritable( writable );
			// Since only ever supposed to be a single file in the dir, protect the dir
			outDir.setWritable( writable );
			
			link( md );
			descriptorMap.put( mdd, md );
			String path = asPathName( mdd );
			pathMap.put( path, md );
		}
	}

	@Override
	public synchronized void put( ManagedDisk md, ProgressMonitor.Callback cb,
								  int progressUpdateIntervalSecs )
		throws IOException {

		// LOOK: this is same code as put(ManagedDisk) but with the progmon..

		ManagedDiskDescriptor mdd = md.getDescriptor();
		if( descriptorMap.containsKey( mdd ) )
			throw new IllegalArgumentException( "Already stored: " + mdd );
		
		String fileName = dataFileName( mdd );
		File tempFile = new File( tempDir, fileName );
		log.info( "Writing to " + tempFile );
		/*
		  Since the temp file itself is to be used as a lock,
		  canonicalise it first to avoid any unintended side-stepping
		  of lock requirements.  Since this is where the expensive
		  operation occurs, we maintain the accessibility of the wider
		  store object itself...
		*/
		tempFile = tempFile.getCanonicalFile();
		synchronized( tempFile ) {
			log.debug( "Locked " + tempFile );
			FileOutputStream fos = new FileOutputStream( tempFile );
			BufferedOutputStream bos = new BufferedOutputStream( fos, 1024*64 );
			ProgressMonitor pm = new ProgressMonitor
				( md, bos, cb, progressUpdateIntervalSecs );
			pm.start();
			bos.close();
			fos.close();
			log.debug( "Unlocked " + tempFile );
		}

		// we are now adding to the Store proper, so need the lock....
		synchronized( this ) {
			File outDir = diskDataDir( root, mdd );
			outDir.mkdirs();
			File outFile = new File( outDir, fileName );
			log.info( "Moving to " + outFile );
			tempFile.renameTo( outFile );
			log.info( "Moved to " + outFile );
			md.setManagedData( outFile );

			// Access controls in place to guard against file system screw ups..
			outFile.setWritable( writable );
			// Since only ever supposed to be a single file in the dir, protect the dir
			outDir.setWritable( writable );
			
			link( md );
			descriptorMap.put( mdd, md );
			String path = asPathName( mdd );
			pathMap.put( path, md );
		}
	}

	@Override
	public synchronized long size( ManagedDiskDescriptor mdd )
		throws IOException {

		ManagedDisk md = descriptorMap.get( mdd );
		if( md == null ) {
			log.warn( "size. No such descriptor: " + mdd );
			return -1;
		}
		return md.size();
	}

	@Override
	public synchronized UUID uuid( ManagedDiskDescriptor mdd )
		throws IOException {

		ManagedDisk md = descriptorMap.get( mdd );
		if( md == null ) {
			log.warn( "uuid. No such descriptor: " + mdd );
			return null;
		}
		return md.getUUIDCreate();
	}
	
	/**
	 * Produce a sha1 hash of each grain in the managed disk
	 * identified by the supplied descriptor.
	 
	 * Asserting that we do NOT need this to be synchronized,
	 * since we are reading data already managed, and by definition
	 * that cannot change.

	 * LOOK: We may need to stream this out instead of holding it all.
	 * A 2TB disk using 16 million grains (64K per grain) and a hash
	 * of 20bytes (sha1) per grain means 320MB of memory!
	 *
	 * LOOK: We may/should have this already held as an attribute!
	 */
	@Override
	public ManagedDiskDigest digest( ManagedDiskDescriptor mdd )
		throws IOException {

		if( !descriptorMap.containsKey( mdd ) ) {
			// LOOK: warning ?
			return null;
		}

		File f = managedDataDigest( root, mdd );
		if( !f.isFile() ) {
			log.warn( "Digest missing: " + mdd );
			return null;
		}
		/*
		FileInputStream fis = new FileInputStream( f );
		ObjectInputStream ois = new ObjectInputStream( fis );
		List<byte[]> result = null;
		try {
			result = (List<byte[]>)ois.readObject();
		} catch( ClassNotFoundException neverForList ) {
		}
		ois.close();
		fis.close();
		*/
		FileReader fr = new FileReader( f );
		ManagedDiskDigest result = ManagedDiskDigest.readFrom( fr );
		fr.close();
		return result;
	}

	/**
	 * Scan the managed data identified by the supplied ManagedDiskDescriptor
	 * and sha1 hash each grain.  Save the hashes list to a file alongside
	 * the managed data file itself.
	 */
	public void computeDigest( ManagedDiskDescriptor mdd )
		throws IOException {

		File digestFile = managedDataDigest( root, mdd );
		if( digestFile.exists() )
			return;
		
		ManagedDisk md = descriptorMap.get( mdd );
		if( md == null ) {
			// LOOK: warning ?
			return;
		}
		
		//md.reportMetaData();
		
		MessageDigest mdg = null;
		try {
			mdg = MessageDigest.getInstance( ManagedDisk.DIGESTALGORITHM );
		} catch( NoSuchAlgorithmException never ) {
		}

		int grainCount = (int)(Utils.alignUp( md.size(), md.grainSizeBytes() ) /
							   md.grainSizeBytes());
		log.info( "Grains: " + grainCount );
		byte[] grain = new byte[(int)md.grainSizeBytes()];
		InputStream is = md.getInputStream();
		//		DigestInputStream dis = new DigestInputStream( is, sha1 );
		//		List<byte[]> digest = new ArrayList<byte[]>( grainCount );
		ManagedDiskDigest digest = new ManagedDiskDigest();
		for( int g = 1; g <= grainCount; g++ ) {
			//	int nin = dis.read( grain );
			int nin = is.read( grain );
			/*
			  Only the last read could/should return a partial grain,
			  and that would be if the data size not a multiple of
			  grainSize, which is OK.
			*/
			if( nin != grain.length && g < grainCount ) {
				throw new IllegalStateException( "Partial read (" +
												 g + "/" +
												 grainCount + "). Fix!" );
			}
			byte[] hash = mdg.digest( grain );
			digest.add( hash );
			mdg.reset();
			if( log.isTraceEnabled() )
				log.trace( g );
		}
		//		dis.close();
		is.close();


		/*		FileOutputStream fos = new FileOutputStream( digestFile );
		ObjectOutputStream oos = new ObjectOutputStream( fos );
		oos.writeObject( digest );
		oos.close();
		fos.close();
		*/
		FileWriter fw = new FileWriter( digestFile );
		digest.writeTo( fw );
		fw.close();
	}
	
	// for the benefit of the fuse-based ManagedDiskFileSystem
	@Override
	public ManagedDisk locate( ManagedDiskDescriptor mdd ) {
		return descriptorMap.get( mdd );
	}

	@Override
	public synchronized Collection<ManagedDiskDescriptor> enumerate()
		throws IOException {
		return descriptorMap.keySet();
	}

	@Override
	public Collection<String> listAttributes( ManagedDiskDescriptor mdd )
		throws IOException {
		File dir = attrDir( root, mdd );
		if( !dir.isDirectory() )
			return Collections.emptyList();
		List<String> result = new ArrayList<String>();
		File[] fs = dir.listFiles();
		for( File f : fs ) {
			result.add( f.getName() );
		}
		return result;
	}

	@Override
	public void setAttribute( ManagedDiskDescriptor mdd,
							  String key, byte[] value ) throws IOException {

		String fileName = key;
		File outDir = attrDir( root, mdd );
		outDir.mkdirs();
		File outFile = new File( outDir, fileName );
		outFile = outFile.getCanonicalFile();
		synchronized( outFile ) {
			log.debug( "Locked " + outFile );
			FileUtils.writeByteArrayToFile( outFile, value );
			log.debug( "Unlocked " + outFile );
		}
	}

	@Override
	public byte[] getAttribute( ManagedDiskDescriptor mdd, String key )
		throws IOException {
		File dir = attrDir( root, mdd );
		if( !dir.isDirectory() )
			return null;
		File inFile = new File( dir, key );
		if( !inFile.isFile() )
			return null;
		return FileUtils.readFileToByteArray( inFile );
	}

    @Override
    public void putFileRecords(ManagedDiskDescriptor mdd, List<Record> records) throws IOException {
        FileRecordStore store = getRecordStore(mdd);
        store.addRecords(records);
        store.close();
    }

    @Override
    public List<Record> getRecords(ManagedDiskDescriptor mdd, String algorithm, List<byte[]> hashes) throws IOException {
        FileRecordStore store = getRecordStore(mdd);
        List<Record> records = store.getRecordsFromHashes(algorithm, hashes);
        store.close();
        return records;
    }

    @Override
    public List<ManagedDiskDescriptor> checkForHash(String algorithm, byte[] hash) throws IOException {
        Collection<ManagedDiskDescriptor> disks = enumerate();
        List<ManagedDiskDescriptor> matchingDisks = new ArrayList<ManagedDiskDescriptor>(disks.size());
        // Iterate over the disks and see if they have a matching hash
        for (ManagedDiskDescriptor mdd : disks) {
            FileRecordStore store = getRecordStore(mdd);
            if (store.containsFileHash(algorithm, hash)) {
                matchingDisks.add(mdd);
            }
            store.close();
        }
        return matchingDisks;
    }

    @Override
    public List<ManagedDiskDescriptor> checkForHashes(String algorithm, List<byte[]> hashes) throws IOException {
        Collection<ManagedDiskDescriptor> disks = enumerate();
        List<ManagedDiskDescriptor> matchingDisks = new ArrayList<ManagedDiskDescriptor>(disks.size());
        // Iterate over the disks and see if they have a matching hash
        for (ManagedDiskDescriptor mdd : disks) {
            FileRecordStore store = getRecordStore(mdd);
            if (store.containsFileHash(algorithm, hashes)) {
                matchingDisks.add(mdd);
            }
            store.close();
        }
        return matchingDisks;
    }

    @Override
    public boolean hasFileRecords(ManagedDiskDescriptor mdd) throws IOException {
        FileRecordStore store = getRecordStore(mdd);
        boolean hasData = store.hasData();
        store.close();
        return hasData;
    }

    /**
     * Get the FileRecordStore associated with the managed disk
     * @param mdd
     * @return
     * @throws Exception
     */
    public FileRecordStore getRecordStore(ManagedDiskDescriptor mdd) throws IOException {
        return new FileRecordStore(diskDir(root, mdd), mdd);
    }

    /*********************** Private Implementation *********************/

	private UUID loadUUID() {
		UUID result = null;
		File f = new File( root, "uuid.txt" );
		if( f.exists() ) {
			String line = null;
			try {
				BufferedReader br = new BufferedReader( new FileReader( f ) );
				line = br.readLine();
				br.close();
			} catch( IOException ioe ) {
				log.warn( ioe );
				throw new IllegalStateException( "UUID read error!" );
			}
			try {
				result = UUID.fromString( line );
			} catch( IllegalArgumentException iae ) {
				log.warn( iae );
				throw new IllegalStateException( "UUID parse error!" );
			}
		} else {
			result = UUID.randomUUID();
			try {
				PrintWriter pw = new PrintWriter( new FileWriter( f ) );
				pw.println( result );
				pw.close();
			} catch( IOException ioe ) {
				log.warn( ioe );
				throw new IllegalStateException( "UUID write error!" );
			}
		}
		return result;
	}

	private void loadManagedDisks() {
		File dir = new File( root, "disks" );
		dir.mkdirs();
		Collection<File> fs = FileUtils.listFiles
			( dir, new String[] { ManagedDisk.FILESUFFIX.substring(1) }, true );
		for( File f : fs ) {
			try {
				ManagedDisk md = ManagedDisk.readFrom( f );
				ManagedDiskDescriptor mdd = md.getDescriptor();
				log.debug( "Adding: " + mdd );
				ManagedDisk prev = descriptorMap.put( mdd, md );
				if( prev != null ) {
					log.warn( "Previous value: " + prev + " for descriptor " +
							  mdd );
				}
				String path = asPathName( mdd );
				pathMap.put( path, md );
				log.debug( "Located managed disk: " + f );
			} catch( IOException ioe ) {
				log.warn( ioe );
				continue;
			}
		}
		Collection<ManagedDisk> allDisks = pathMap.values();
		List<ManagedDisk> linkedDisks = new ArrayList<ManagedDisk>
			( allDisks.size() );
		for( ManagedDisk md : allDisks ) {
			// LOOK: withdraw any ManagedDisk which cannot be fully linked
			link( md, allDisks, linkedDisks );
		}
	}

	private void link( ManagedDisk md ) {
		if( !md.hasParent() )
			return;
		Collection<ManagedDisk> allDisks = pathMap.values();
		UUID linkage = md.getUUIDParent();
		ManagedDisk parent = locate( linkage, allDisks );
		md.setParent( parent );
	}
		
	/**
	 * @param linkedDisks - the accumulating result, often needed for
	 * base case testing when using a recursive process.
	 */
	private void link( ManagedDisk md, Collection<ManagedDisk> allDisks,
					   List<ManagedDisk> linkedDisks ) {
		if( !md.hasParent() )
			return;
		if( linkedDisks.contains( md ) )
			return;
		UUID linkage = md.getUUIDParent();
		ManagedDisk parent = locate( linkage, allDisks );
		md.setParent( parent );
		log.info( "SetParent: " + md.getDescriptor() + " -> " +
				  parent.getDescriptor() );
		linkedDisks.add( md );
		link( parent, allDisks, linkedDisks );
	}

	private ManagedDisk locate( UUID needle,
								Collection<ManagedDisk> allDisks ) {
		for( ManagedDisk md : allDisks ) {
			if( md.getUUIDCreate().equals( needle ) )
				return md;
		}
		throw new IllegalStateException( "No such uuid: " + needle );
	}

	/*
	  The file sys layout here is

	  disks/
	  disks/DISKID/SESSIONID
	  disks/DISKID/SESSIONID/data
	  disks/DISKID/SESSIONID/data/DISKID-SESSIONID.tmd
	  disks/DISKID/SESSIONID/attrs
	  disks/DISKID/SESSIONID/attrs/SOMEATTR
	*/
	static private File diskDir( File root, ManagedDiskDescriptor vd ) {
		File dir = new File( root, "disks" );
		dir = new File( dir, vd.getDiskID() );
		dir = new File( dir, vd.getSession().toString() );
		return dir;
	}

	static private File diskDataDir( File root, ManagedDiskDescriptor mdd ) {
		File dir = diskDir( root, mdd );
		dir = new File( dir, "data" );
		return dir;
	}

	static protected File managedDataFile( File root,
										 ManagedDiskDescriptor mdd ) {
		File dir = diskDataDir( root, mdd );
		File result = new File( dir, dataFileName( mdd ) );
		return result;
	}

	static private File managedDataDigest( File root,
										   ManagedDiskDescriptor mdd ) {
		File dir = diskDataDir( root, mdd );
		File result = new File( dir, digestFileName( mdd ) );
		return result;
	}

	static private File attrDir( File root, ManagedDiskDescriptor mdd ) {
		File dir = diskDir( root, mdd );
		dir = new File( dir, "attrs" );
		return dir;
	}

	static String asPathName( ManagedDiskDescriptor mdd ) {
		return mdd.getDiskID() + File.separator +
			mdd.getSession().toString();
	}

	static String dataFileName( ManagedDiskDescriptor mdd ) {
		return asFileBase( mdd ) + ManagedDisk.FILESUFFIX;
	}

	static String digestFileName( ManagedDiskDescriptor mdd ) {
		return asFileBase( mdd ) + "." + ManagedDisk.DIGESTALGORITHM;
	}

	static String asFileBase( ManagedDiskDescriptor mdd ) {
		return mdd.getDiskID() + "-" + mdd.getSession().toString();
	}
}
