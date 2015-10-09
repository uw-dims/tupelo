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
package edu.uw.apl.tupelo.shell;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.cli.*;
import org.apache.commons.codec.binary.Hex;

import edu.uw.apl.commons.shell.Shell;
import edu.uw.apl.commons.tsk4j.image.Image;
import edu.uw.apl.commons.tsk4j.volsys.Partition;
import edu.uw.apl.commons.tsk4j.volsys.VolumeSystem;
import edu.uw.apl.commons.tsk4j.digests.BodyFile;
import edu.uw.apl.commons.tsk4j.digests.BodyFile.Record;
import edu.uw.apl.commons.tsk4j.digests.VolumeSystemHash;
import edu.uw.apl.commons.tsk4j.digests.VolumeSystemHashCodec;
import edu.uw.apl.commons.tsk4j.filesys.FileSystem;
import edu.uw.apl.vmvols.model.VirtualMachine;
import edu.uw.apl.vmvols.fuse.VirtualMachineFileSystem;

import edu.uw.apl.tupelo.model.Session;
import edu.uw.apl.tupelo.model.ManagedDisk;
import edu.uw.apl.tupelo.model.ManagedDiskDescriptor;
import edu.uw.apl.tupelo.model.ManagedDiskDigest;
import edu.uw.apl.tupelo.model.DiskImage;
import edu.uw.apl.tupelo.model.FlatDisk;
import edu.uw.apl.tupelo.model.physical.PhysicalDisk;
import edu.uw.apl.tupelo.model.virtual.VirtualDisk;
import edu.uw.apl.tupelo.model.ProgressMonitor;
import edu.uw.apl.tupelo.model.StreamOptimizedDisk;
import edu.uw.apl.tupelo.model.UnmanagedDisk;
import edu.uw.apl.tupelo.http.client.HttpStoreProxy;
import edu.uw.apl.tupelo.store.Store;
import edu.uw.apl.tupelo.store.null_.NullStore;
import edu.uw.apl.tupelo.utils.Discovery;
import edu.uw.apl.tupelo.utils.DiskHashUtils;
import edu.uw.apl.tupelo.store.filesys.FilesystemStore;

/**
   A cmd line shell for the Tupelo system. Works along the lines of
   bash...
*/
public class Elvis extends Shell {

    private String storeLocation;
    private Store store;
    private List<PhysicalDisk> physicalDisks;
    private List<VirtualDisk> virtualDisks;
    private List<DiskImage> diskImages;
    private static boolean verbose, debug;
    private Session session;
    private VirtualMachineFileSystem vmfs;

    // Bodyfiles for unmanaged disks
    // This is only hashes done this session
    private Map<UnmanagedDisk, List<BodyFile>> diskFileRecords;

    /**
     * Path to check for potential disks
     */
    static public final String[] PHYSICAL_DISK_NAMES = {
        // Linux/Unix...
        "/dev/sda", "/dev/sdb", "/dev/sdc",
        "/dev/sdd", "/dev/sde", "/dev/sdf",
        "/dev/sdg", "/dev/sdh", "/dev/sdi",
        // MacOS...
        "/dev/disk0", "/dev/disk1", "/dev/disk2",
        "/dev/disk3", "/dev/disk4", "/dev/disk5"
    };

    private static final String UNMANAGED_DISK_REPORT_FORMAT = "%2s %42s %16s %16s";

    private static final String MANAGED_DISK_REPORT_FORMAT = "%2s %42s %17s";

    private static final String ATTRIBUTE_REPORT_FORMAT = "%2s %42s";

    /**
     * Property name for defining the Tupelo store location
     */
    public static final String STORE_PROPERTY = "store-location";

    static public void main( String[] args ) {
		try {
			Elvis main = new Elvis();
			main.readConfig();
			main.readArgs( args );
			main.start();
			main.finish();
		} catch( Exception e ) {
			if( debug )
				e.printStackTrace();
			else
				System.err.println( e );
		}
	}

	public Elvis() {
		super();
		physicalDisks = new ArrayList<PhysicalDisk>();
		virtualDisks = new ArrayList<VirtualDisk>();
		diskImages = new ArrayList<DiskImage>();
		diskFileRecords = new HashMap<UnmanagedDisk, List<BodyFile>>();
		
		// Try and load the store location via the Discovery class
		storeLocation = Discovery.locatePropertyValue(STORE_PROPERTY);
		if(storeLocation == null){
		    // Use the default if not defined
		    storeLocation = "./test-store";
		}
		verbose = false;

		// report available Java memory...
		addCommand( "mem", new Lambda() {
				public void apply( String[] args ) throws Exception {
					Runtime rt = Runtime.getRuntime();
					long mem = rt.freeMemory();
					System.out.println( "Free Memory: " + mem );
				}
			} );
		commandHelp( "mem", "Print free memory" );
					
		// report store-managed data...
		addCommand( "ms", new Lambda() {
				public void apply( String[] args ) throws Exception {
					Collection<ManagedDiskDescriptor> mdds = store.enumerate();
					reportManagedDisks( mdds );
				}
			} );
		commandHelp( "ms", "List store-managed disks" );

		// report store's free disk space...
		addCommand( "space", new Lambda() {
				public void apply( String[] args ) throws Exception {
					long usableSpace = store.getUsableSpace();
					System.out.println( "Usable space: " + usableSpace );
										 
				}
			} );
		commandHelp( "space", "Print store's free disk space" );

		// report unmanaged data...
		addCommand( "us", new Lambda() {
				public void apply( String[] args ) throws Exception {
					reportUnmanagedDisks();
										 
				}
			} );
		commandHelp( "us", "List local unmanaged disks" );

		// report the store url...
		addCommand( "s", new Lambda() {
				public void apply( String[] args ) throws Exception {
					System.out.println( "Store: " + storeLocation );
					
										 
				}
			} );
		commandHelp( "s", "Print the store location" );

		/*
		  vshash, hash the volume system (unallocated areas) of an identified
		  unmanaged disk
		*/
		addCommand( "vshash", "(.+)", new Lambda() {
				public void apply( String[] args ) throws Exception {
					String needle = args[1];
					needle = needle.trim();
					UnmanagedDisk ud = null;
					try {
						ud = locateUnmanagedDisk( needle );
					} catch( IndexOutOfBoundsException oob ) {
						System.err.println
							( "No unmanaged disk " + needle +
							  ". Use 'us' to list unmanaged disks" );
						return;
					}
					if( ud == null ) {
						System.out.println( "No unmanaged disk: " +
											needle );
						return;
					}
					hashVolumeSystem( ud );
				}
			} );
		commandHelp( "vshash", "unmanagedDisk",
					 "Hash each unallocated area of the identified unmanaged disk, storing the result as a managed disk attribute" );
		
        addCommand("fshash", "(.+)", new Lambda() {
            public void apply(String args[]) throws Exception {
                String diskName = args[1].trim();
                UnmanagedDisk ud = null;
                try {
                    ud = locateUnmanagedDisk(diskName);
                } catch (IndexOutOfBoundsException oob) {
                    System.err.println("No unmanaged disk " + diskName + ". Use 'us' to list unmanaged disks");
                    return;
                }
                if (ud == null) {
                    System.out.println("No unmanaged disk: " + diskName);
                    return;
                }
                // Prep the session
                checkSession();

                try {
                    Date start = new Date();
                    DiskHashUtils disk = new DiskHashUtils(ud.getSource().getAbsolutePath());

                    List<BodyFile> bodyFiles = new LinkedList<BodyFile>();
                    // Process each parition individually
                    List<Partition> partitions = disk.getPartitions();
                    if (partitions != null) {
                        for (Partition partition : partitions) {
                            // Make sure there is a filesystem
                            FileSystem fs = disk.getFileSystem(partition);
                            if (fs == null) {
                                continue;
                            }
                            BodyFile bodyFile = disk.hashFileSystem(fs);
                            bodyFiles.add(bodyFile);

                            // Write the data
                            writeRecordData(bodyFile, diskName + "-" + session + "-" + partition.start() + "-"
                                    + partition.length() + ".bodyfile");
                        }
                    } else {
                        FileSystem fs = disk.getFileSystem();
                        if (fs == null) {
                            System.err.println("No filesystem found");
                            disk.close();
                            return;
                        }
                        BodyFile bodyFile = disk.hashFileSystem(fs);
                        bodyFiles.add(bodyFile);

                        // Write the bodyfile
                        writeRecordData(bodyFile, diskName + "-" + session + ".bodyfile");
                    }

                    disk.close();
                    // Store it for now
                    diskFileRecords.put(ud, bodyFiles);

                    Date end = new Date();
                    System.out.println("Elapsed time: " + (end.getTime() - start.getTime()) / 1000 + "sec");
                } catch (Exception e) {
                    log.warn(e);
                    if (debug) {
                        e.printStackTrace();
                    }
                }
            }
        });
		commandHelp("fshash", "unmanagedDisk",
		        "Hash each filesystem of the identified unmanaged disk.  The resulting bodyfiles are written to the current directory, and will also be sent to the store if the disk is.");

		// putdisk, xfer an unmanaged disk to the store
		addCommand( "putdisk", "(.+)", new Lambda() {
				public void apply( String[] args ) throws Exception {
					String needle = args[1];
					UnmanagedDisk ud = null;
					try {
						ud = locateUnmanagedDisk( needle );
					} catch( IndexOutOfBoundsException oob ) {
						System.err.println
							( "No unmanaged disk " + needle +
							  ". Use 'us' to list unmanaged disks" );
						return;
					}
					if( ud == null ) {
						System.out.println( "No unmanaged disk: " +
											needle );
						return;
					}
					try {
						putDisk( ud );
					} catch( IOException ioe ) {
						log.warn( ioe );
						System.err.println( "" + ioe );
					}
				}
			} );
		commandHelp( "putdisk", "unmanagedDiskPath|unmanagedDiskIndex",
					 "Transfer an identified unmanaged disk to the store" );

		// list attributes for a given managed disk
		addCommand( "as", "(.+)", new Lambda() {
				public void apply( String[] args ) throws Exception {
					String needle = args[1];
					ManagedDiskDescriptor mdd = null;
					try {
						mdd = locateManagedDisk( needle );
					} catch( RuntimeException iae ) {
						System.err.println( iae );
						return;
					}
					if( mdd == null ) {
						System.out.println( "No managed disk: " +
											needle );
						return;
					}
					try {
						listAttributes( mdd );
					} catch( IOException ioe ) {
						log.warn( ioe );
						System.err.println( "" + ioe );
					}
				}
			} );
		commandHelp( "as", "managedDiskIndex",
					 "List attributes of an identified managed disk" );

		addCommand("findhash", "(.+)", new Lambda() {
            @Override
            public void apply(String[] args) throws Exception {
                try {
                    // Get and verify the hashes
                    String input = args[0];
                    String[] hashes = input.split("\\s+");

                    // Check which managed disks have file hash data
                    Collection<ManagedDiskDescriptor> disks = store.enumerate();
                    for (ManagedDiskDescriptor mdd : disks) {
                        // Let the user know which disks are missing file hash
                        // data
                        if (!store.hasFileRecords(mdd)) {
                            System.out.println("Warning: disk is missing file hash data: " + mdd);
                        }
                    }

                    // Start looking for the hashes
                    Set<ManagedDiskDescriptor> matchingDisks = new HashSet<ManagedDiskDescriptor>();
                    for (String hash : hashes) {
                        hash = hash.toLowerCase();
                        // A MD5 hash is a 32 character hex string
                        if(!hash.matches("^[a-f0-9]{32}$")){
                            System.err.println("Error: Invalid hash: "+hash);
                            continue;
                        }
                        byte[] byteHash = Hex.decodeHex(hash.toCharArray());
                        // Add all matching disks to the set
                        matchingDisks.addAll(store.checkForHash(byteHash));
                    }

                    // Report our results
                    if(matchingDisks.isEmpty()){
                        System.out.println("Hash not found.");
                    } else {
                        System.out.println("Hash found on the following disks:");
                        for(ManagedDiskDescriptor mdd : matchingDisks){
                            System.out.println("\t"+mdd.toString());
                        }
                    }
                } catch (Exception e) {
                    log.warn(e);
                    System.out.println("" + e);
                }
            }
		});
		commandHelp("findhash", "MD5 hashes",
		        "Check all managed disks to see which ones contain the specified MD5 file hash. "+
		        "Seperate multiple hashes by a space.");
	}

	static protected void printUsage( Options os, String usage,
									  String header, String footer ) {
		HelpFormatter hf = new HelpFormatter();
		hf.setWidth( 80 );
		hf.printHelp( usage, header, os, footer );
	}


	public void readArgs( String[] args ) throws Exception {
		Options os = new Options();
		os.addOption( "c", true, "command string" );
		os.addOption( "d", false, "debug" );
		os.addOption( "s", true, "store location (file/http)" );
		os.addOption( "u", true, "path to unmanaged disk" );
		os.addOption( "V", false, "show version number and exit" );

		String USAGE = Elvis.class.getName() + " [-c] [-d] [-s] [-u] [-V]";
		final String HEADER = "";
		final String FOOTER = "";

		CommandLineParser clp = new DefaultParser();
		CommandLine cl = null;
		try {
			cl = clp.parse( os, args );
		} catch( Exception e ) {
			printUsage( os, USAGE, HEADER, FOOTER );
			System.exit(1);
		}
		
		debug = cl.hasOption( "d" );
		if( cl.hasOption( "s" ) ) {
			storeLocation = cl.getOptionValue( "s" );
		}
		if( cl.hasOption( "c" ) ) {
			cmdString = cl.getOptionValue( "c" );
		}
		if( cl.hasOption( "u" ) ) {
			String[] ss = cl.getOptionValues( "u" );
			for( String s : ss ) {
				File f = new File( s );
				if( !( f.canRead() ) )
					continue;
				if( VirtualDisk.likelyVirtualDisk( f ) ) {
					VirtualDisk vd = new VirtualDisk( f );
					virtualDisks.add( vd );
				} else {
					DiskImage di = new DiskImage( f );
					diskImages.add( di );
				}
			}
		}
		if( cl.hasOption( "V" ) ) {
			Package p = getClass().getPackage();
			String version = p.getImplementationVersion();
			System.out.println( p.getName() + "/" + version );
			System.exit(0);
		}
		args = cl.getArgs();
		if( args.length > 0 ) {
			cmdFile = new File( args[0] );
			if( !cmdFile.exists() ) {
				// like bash would do, write to stderr...
				System.err.println( cmdFile + ": No such file or directory" );
				System.exit(-1);
			}
		}
	}

	@Override
	public void start() throws Exception {
		try {
			store = buildStore();
		} catch( IllegalStateException ise ) {
			System.err.println( "Cannot locate store: '" + storeLocation +
								"'." );
			System.err.println( "For a file-based store, first 'mkdir " +
								storeLocation + "'" );
			System.exit(1);
			
		}
		report( "Store: " + storeLocation );
		identifyUnmanagedDisks();
		super.start();
	}
	
	
	private void listAttributes( ManagedDiskDescriptor mdd ) throws IOException{
		Collection<String> attrNames = store.listAttributes( mdd );
		reportAttributes( attrNames );
	}
	
	private UnmanagedDisk locateUnmanagedDisk( String needle ) {
		try {
			int i = Integer.parseInt( needle );
			return locateUnmanagedDisk( i );
		} catch( NumberFormatException nfe ) {
			// proceed with name-based lookup...
		}
		for( PhysicalDisk pd : physicalDisks ) {
			String s = pd.getSource().getPath();
			if( s.startsWith( needle ) )
				return pd;
		}
		for( VirtualDisk vd : virtualDisks ) {
			String s = vd.getSource().getName();
			if( s.startsWith( needle ) )
				return vd;
		}
		for( DiskImage di : diskImages ) {
			String s = di.getSource().getName();
			if( s.startsWith( needle ) )
				return di;
		}
		return null;
	}

	/**
	 * 1-based search, natural numbers
	 */
	private UnmanagedDisk locateUnmanagedDisk( int needle ) {
		int total = physicalDisks.size() + virtualDisks.size() +
			diskImages.size();
		if( needle < 1 || needle > total )
			throw new IndexOutOfBoundsException( "Index out-of-range: " +
												 needle );
		needle--;
		if( needle < physicalDisks.size() )
			return physicalDisks.get( needle );
		needle -= physicalDisks.size();
		if( needle < virtualDisks.size() )
			return virtualDisks.get( needle );
		needle -= virtualDisks.size();
		if( needle < diskImages.size() )
			return diskImages.get( needle );
		// should never occur given earlier bounds check
		return null;
	}
	
	private ManagedDiskDescriptor locateManagedDisk( String needle )
		throws IOException {
		try {
			int i = Integer.parseInt( needle );
			return locateManagedDisk( i );
		} catch( NumberFormatException nfe ) {
			// proceed with name-based lookup...
		}
		// TODO
		return null;
	}

	private ManagedDiskDescriptor locateManagedDisk( int index ) 
		throws IOException {
		Collection<ManagedDiskDescriptor> mdds = store.enumerate();
		List<ManagedDiskDescriptor> sorted =
			new ArrayList<ManagedDiskDescriptor>( mdds );
		Collections.sort( sorted, ManagedDiskDescriptor.DEFAULTCOMPARATOR );
		int total = sorted.size();
		if( index < 1 || index > total )
			throw new IllegalArgumentException( "Index out-of-range: " +
												index );
		return sorted.get(index-1);
	}

	private void identifyUnmanagedDisks() {
		identifyPhysicalDisks();
	}

	private void identifyPhysicalDisks() {
		for( String pdName : PHYSICAL_DISK_NAMES ) {
			File pdf = new File( pdName );
			if( !pdf.exists() )
				continue;
			if( !pdf.canRead() ) {
				if( isInteractive() ) {
					System.out.println( "Unreadable: " + pdf );
					continue;
				}
			}	
			try {
				PhysicalDisk pd = new PhysicalDisk( pdf );
				log.info( "Located " + pdf );
				physicalDisks.add( pd );
			} catch( IOException ioe ) {
				log.error( ioe );
			}
		}
	}
	
	private void finish() throws Exception {
		if( isInteractive() )
			System.out.println( "Bye!" );
	}

	private void report( String msg ) {
		if( !isInteractive() )
			return;
		System.out.println( msg );
	}

	private Store buildStore() {
		Store s = null;
		if( storeLocation.equals( "/dev/null" ) ) {
			s = new NullStore();
		} else if( storeLocation.startsWith( "http" ) ) {
			s = new HttpStoreProxy( storeLocation );
		} else {
			File dir = new File( storeLocation );
			if( !dir.isDirectory() ) {
				throw new IllegalStateException
					( "Not a directory: " + storeLocation );
			}
			s = new FilesystemStore( dir );
		}
		return s;
	}

	@Override
	protected void prompt() {
		System.out.print( "tupelo> " );
		System.out.flush();
	}

	void reportManagedDisks( Collection<ManagedDiskDescriptor> mdds ) {
		String header = String.format( MANAGED_DISK_REPORT_FORMAT,
									   "N", "ID", "Session" );
		System.out.println( header );
		int n = 1;
		List<ManagedDiskDescriptor> sorted =
			new ArrayList<ManagedDiskDescriptor>( mdds );
		Collections.sort( sorted, ManagedDiskDescriptor.DEFAULTCOMPARATOR );
		for( ManagedDiskDescriptor mdd : sorted ) {
			String fmt = String.format( MANAGED_DISK_REPORT_FORMAT, n,
										mdd.getDiskID(),
										mdd.getSession() );
			System.out.println( fmt );
			n++;
		}
	}

	void reportAttributes( Collection<String> attrNames ) {
		String header = String.format( ATTRIBUTE_REPORT_FORMAT,
									   "N", "Name" );
		System.out.println( header );
		int n = 1;
		List<String> sorted = new ArrayList<String>( attrNames );
		Collections.sort( sorted );
		for( String s : sorted ) {
			String fmt = String.format( ATTRIBUTE_REPORT_FORMAT, n, s );
			System.out.println( fmt );
			n++;
		}
	}

	void reportUnmanagedDisks() {
		String header = String.format( UNMANAGED_DISK_REPORT_FORMAT,
									   "N", "ID", "Size", "Path" );
		System.out.println( header );
		reportPhysicalDisks( 1 );
		reportVirtualDisks( 1 + physicalDisks.size() );
		reportDiskImages( 1 + physicalDisks.size() + virtualDisks.size() );
	}

	void reportPhysicalDisks( int n ) {
		for( PhysicalDisk pd : physicalDisks ) {
			String fmt = String.format( UNMANAGED_DISK_REPORT_FORMAT,
										n, pd.getID(), pd.size(),
										pd.getSource() );
			System.out.println( fmt );
			n++;
		}
	}

	void reportVirtualDisks( int n ) {
		for( VirtualDisk vd : virtualDisks ) {
			String fmt = String.format( UNMANAGED_DISK_REPORT_FORMAT,
										n, vd.getID(), vd.size(),
										vd.getSource().getName() );
			System.out.println( fmt );
			n++;
		}
	}

	void reportDiskImages( int n ) {
		for( DiskImage di : diskImages ) {
			String fmt = String.format( UNMANAGED_DISK_REPORT_FORMAT,
										n, di.getID(), di.size(),
										di.getSource().getName() );
			System.out.println( fmt );
			n++;
		}
	}

	private void checkSession() throws IOException {
		if( session == null ) {
			session = store.newSession();
			report( "Session: " + session );
		}
	}

	private void putDisk( UnmanagedDisk ud ) throws IOException {
		checkSession();
		Collection<ManagedDiskDescriptor> existing = store.enumerate();
		if( verbose )
			System.out.println( "Stored data: " + existing );

		List<ManagedDiskDescriptor> matching =
			new ArrayList<ManagedDiskDescriptor>();
		for( ManagedDiskDescriptor mdd : existing ) {
			if( mdd.getDiskID().equals( ud.getID() ) ) {
				matching.add( mdd );
			}
		}
		Collections.sort( matching, ManagedDiskDescriptor.DEFAULTCOMPARATOR );
		System.out.println( "Matching managed disks:" );
		for( ManagedDiskDescriptor el : matching ) {
			System.out.println( " " + el.getSession() );
		}

		ManagedDiskDigest digest = null;
		UUID uuid = null;
		if( !matching.isEmpty() ) {
			ManagedDiskDescriptor recent = matching.get( matching.size()-1 );
			log.info( "Retrieving uuid for: "+ recent );
			uuid = store.uuid( recent );
			if( debug )
				System.out.println( "UUID: " + uuid );
			log.info( "Requesting digest for: "+ recent );
			digest = store.digest( recent );
			if( digest == null ) {
				log.warn( "No digest, continuing with full disk put" );
			} else {
				log.info( "Retrieved digest for " +
						  recent.getSession() + ": " +
						  digest.size() );
			}
			
		}
		checkSession();
		ManagedDiskDescriptor mdd = new ManagedDiskDescriptor( ud.getID(),
															   session );
		System.out.println( "Storing: " + ud.getSource() +
							" (" + ud.size() + " bytes) to " + mdd );
		ManagedDisk md = null;
		boolean useFlatDisk = ud.size() < 1024L * 1024 * 1024;
		if( useFlatDisk ) {
			md = new FlatDisk( ud, session );
		} else {
			if( uuid != null ){
				md = new StreamOptimizedDisk( ud, session, uuid );
			} else {
				md = new StreamOptimizedDisk( ud, session );
			}
			md.setCompression( ManagedDisk.Compressions.SNAPPY );
		}

		if( digest != null ) {
			md.setParentDigest( digest );
		}
		
		if( !isInteractive() ) {
			store.put( md );
		} else {
			final long sz = ud.size();
			ProgressMonitor.Callback cb = new ProgressMonitor.Callback() {
					public void update( long in, long out, long elapsed ) {
						double pc = in / (double)sz * 100;
						System.out.print( (int)pc + "% " );
						System.out.flush();
						if( in == sz ) {
							System.out.println();
							System.out.printf( "Unmanaged size: %12d\n",
											   sz );
							System.out.printf( "Managed   size: %12d\n", out );
							System.out.println( "Elapsed: " + elapsed );
						}
					}
				};
			int progressMonitorUpdateIntervalSecs = 5;
			store.put( md, cb, progressMonitorUpdateIntervalSecs );
		}

		/*
		  Store various attributes about the UNMANAGED state that
		  serve as a context for the acquisition.  None of these are
		  REQUIRED to be 'correct', they are purely informational.
		*/
		String user = System.getProperty( "user.name" );
		store.setAttribute( mdd, "unmanaged.user",
							user.getBytes() );
		Date timestamp = new Date();
		// storing the timestamp as a STRING
		store.setAttribute( mdd, "unmanaged.timestamp",
							("" + timestamp).getBytes() );
		
		store.setAttribute( mdd, "unmanaged.path",
							ud.getSource().getPath().getBytes() );

		try {
			InetAddress ia = InetAddress.getLocalHost();
			String dotDecimal = ia.getHostAddress();
			store.setAttribute( mdd, "unmanaged.inetaddress",
								dotDecimal.getBytes() );
		} catch( UnknownHostException uhe ) {
		}

		// If the disk's files have been hashed, add them too
		if(diskFileRecords.containsKey(ud)){
		    System.out.println("Sending file records for disk");
		    // Add records for all partitions
		    List<Record> allRecords = new LinkedList<Record>();
		    for(BodyFile bodyFile : diskFileRecords.get(ud)){
		        allRecords.addAll(bodyFile.records());
		    }
		    // Send it
		    store.putFileRecords(mdd, allRecords);

		    System.out.println("File records sent");
		} else {
		    // TODO - Check for a hash file and read/add it
		    System.out.println("No file records found in memory");
		}
	}

	private void hashVolumeSystem( UnmanagedDisk ud ) throws IOException {
		checkSession();
		if( ud instanceof VirtualDisk ) {
			hashVolumeSystemVirtual( (VirtualDisk)ud );
		} else {
			hashVolumeSystemNonVirtual( ud );
		}
	}

	private void checkVMFS() throws IOException {
		if( vmfs == null ) {
			vmfs = new VirtualMachineFileSystem();
			final VirtualMachineFileSystem vmfsF = vmfs;
			Runtime.getRuntime().addShutdownHook( new Thread() {
				public void run() {
					try {
						vmfsF.umount();
					} catch( Exception e ) {
						System.err.println( e );
					}
				}
				} );
			final File mountPoint = new File( "vmfs" );
			mountPoint.mkdirs();
			mountPoint.deleteOnExit();
			try {
				vmfs.mount( mountPoint, true );
			} catch( Exception e ) {
				throw new IOException( e );
			}
		}
	}

	private void hashVolumeSystemVirtual( VirtualDisk ud ) throws IOException {
		checkVMFS();
		VirtualMachine vm = ud.getVM();
		vmfs.add( vm );
		edu.uw.apl.vmvols.model.VirtualDisk vmDisk = ud.getDelegate();
		File f = vmfs.pathTo( vmDisk );
		Image i = new Image( f );
		try {
			hashVolumeSystemImpl( i, ud );
		} finally {
			// MUST release i else leaves vmfs non-unmountable
			i.close();
		}

	}

	private void hashVolumeSystemNonVirtual( UnmanagedDisk ud )
		throws IOException {
		Image i = new Image( ud.getSource() );
		try {
			hashVolumeSystemImpl( i, ud );
		} finally {
			i.close();
		}
	}

	private void hashVolumeSystemImpl( Image i, UnmanagedDisk ud )
		throws IOException {
		VolumeSystem vs = null;
		try {
			vs = new VolumeSystem( i );
		} catch( IllegalStateException noVolSys ) {
			log.warn( noVolSys );
			return;
		}
		try {
			VolumeSystemHash vsh = VolumeSystemHash.create( vs );
			StringWriter sw = new StringWriter();
			VolumeSystemHashCodec.writeTo( vsh, sw );
			String s = sw.toString();
			ManagedDiskDescriptor mdd = new ManagedDiskDescriptor( ud.getID(),
																   session );
			String key = "hashvs";
			byte[] value = s.getBytes();
			store.setAttribute( mdd, key, value );
		} finally {
			// MUST release vs else leaves vmfs non-unmountable
			vs.close();
		}
	}

	/**
	 * Write the file record data to a file
	 * @param fileHashes the map of file path to hash
	 * @param fileName the output file name
	 * @throws IOException
	 */
	private void writeRecordData(BodyFile bodyFile, String fileName) throws IOException{
	    List<Record> records = bodyFile.records();
		// Replace / with _ so it doesnt try and write the file to some other directory
		fileName = fileName.replace('/', '_');
		System.out.println("Writing "+records.size()+" hashes to "+fileName);

		// Get the writers ready
		FileWriter fileWriter = new FileWriter(fileName);
		PrintWriter printWriter = new PrintWriter(new BufferedWriter(fileWriter));

		// Add the header
		printWriter.println("MD5|name|inode|mode_as_string|UID|GID|size|atime|mtime|ctime|crtime");
		// Print the records
		for(Record record : records){
			printWriter.println(record.toString());
		}
		// Flush and close the writers
		printWriter.flush();
		printWriter.close();
	}

}
