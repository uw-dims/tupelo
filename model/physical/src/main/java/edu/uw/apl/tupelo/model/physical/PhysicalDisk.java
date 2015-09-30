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
package edu.uw.apl.tupelo.model.physical;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;

import edu.uw.apl.nativelibloader.NativeLoader;
import edu.uw.apl.tupelo.model.UnmanagedDisk;

/*
  TODO: the JNI code needed for deriving device size in bytes and disk
  serial id (aka vendor ID).  We have this, just cut/paste here
*/

public class PhysicalDisk implements UnmanagedDisk {

	public PhysicalDisk( File f ) throws IOException {
		if( f == null )
			throw new IllegalArgumentException( "Null file!" );

		/*
		  Fail early with IOException if no such file, before jni...
		*/
		FileInputStream fis = new FileInputStream( f );
		fis.close();

		// Java reports 0 for the size of a device file...
		long len = f.length();
		if( len != 0 )
			throw new IllegalArgumentException( f + ": Unexpected length "
												+ len );
		disk = f;
	}

	@Override
	public long size() {
		return size( disk.getPath() );
	}

	@Override
	public String getID() {
		String v = vendorID( disk.getPath() );
		if( v != null )
			v = v.trim();
		String p = productID( disk.getPath() );
		if( p != null )
			p = p.trim();
		String s = serialNumber( disk.getPath() );
		if( s != null )
			s = s.trim();
		String concat = v + "-" + p + "-" + s;
		return concat.replaceAll( "\\s", "_" );
	}
	
	@Override
	public InputStream getInputStream() throws IOException {
		return new FileInputStream( disk );
//		FileInputStream fis = new FileInputStream( disk );
//		int sz = 1024 * 1024 * 32;
//		BufferedInputStream bis = new BufferedInputStream( fis, sz );
//		return bis;
	}

	@Override
	public File getSource() {
		return disk;
	}

	// for debug purposes
	String vendorID() {
		return vendorID( disk.getPath() );
	}
	String productID() {
		return productID( disk.getPath() );
	}
	String serialNumber() {
		return serialNumber( disk.getPath() );
	}

	private native long size( String path );

	private native String vendorID( String path );
	private native String productID( String path );
	private native String serialNumber( String path );
	
	final File disk;

	static private final String artifact = "tupelo-model-physical";
    static {
		try {
			NativeLoader.load( PhysicalDisk.class, artifact );
		} catch( Throwable t ) {
			throw new ExceptionInInitializerError( t );
		}
    }
}

// eof
