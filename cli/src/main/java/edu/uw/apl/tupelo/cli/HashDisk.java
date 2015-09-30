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
package edu.uw.apl.tupelo.cli;

import java.io.BufferedWriter;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.*;
import org.apache.commons.codec.binary.Hex;
import org.apache.log4j.LogManager;

import edu.uw.apl.commons.tsk4j.filesys.FileSystem;
import edu.uw.apl.commons.tsk4j.volsys.Partition;
import edu.uw.apl.tupelo.utils.DiskHashUtils;

/**
 * Simple Tupelo Utility: Hash a raw disk or image,
 * using tsk4j/Sleuthkit routines. Store the resultant 'Hash Info' as a Store
 * attribute.
 *
 */
public class HashDisk extends CliBase {
	
	private String diskPath;
	static boolean verbose;



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

		String usage = commonUsage() + "[-v] diskPath";
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
			System.exit(1);
		}
		diskPath = args[0];
	}
	
	public void start() throws Exception {
		Date start = new Date();
		System.out.println( "Trying to open " + diskPath);
		DiskHashUtils disk = new DiskHashUtils(diskPath);
		disk.setDebug(debug);

		// Get the disk's partitions
		List<Partition> partitions = disk.getPartitions();
		if(partitions != null){
			// If we have paritions, analyze the FileSystems
			for(Partition partition : partitions){
				FileSystem fs = disk.getFileSystem(partition);
				// Skip null FileSystems
				if(fs == null){
					continue;
				}
				// Hash it
				Map<String, byte[]> hashes = disk.hashFileSystem(fs);
				// Record it
				record(hashes, partition.start(), partition.length());
			}
		} else {
			// Get the filesystem
			FileSystem fs = disk.getFileSystem();
			if(fs == null){
				System.err.println("No filesystem found");
				disk.close();
				System.exit(-1);
			}

			// Get and record the hashes
			Map<String, byte[]> hashes = disk.hashFileSystem(fs);
			record(hashes, 0, 0);
		}
		
		// Cleanup
		disk.close();

		System.out.println("Done");
		Date end = new Date();
		System.out.println("Elapsed time: "+(end.getTime() - start.getTime()) / 1000 + "sec");
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
		System.out.println( "Writing "+fileHashes.size()+" hashes to: " + outName );
		
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
