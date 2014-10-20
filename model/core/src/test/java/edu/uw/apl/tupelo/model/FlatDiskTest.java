package edu.uw.apl.tupelo.model;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class FlatDiskTest extends junit.framework.TestCase {

	public void testNull() {
	}

	// A sized file which should PASS the 'whole number of sectors' test
	public void testSize64k() throws IOException {
		File f = new File( "src/test/resources/64k" );
		if( !f.exists() )
			return;
		UnmanagedDisk ud = new DiskImage( f );
		try {
			FlatDisk fd = new FlatDisk( ud, Session.CANNED );
		} catch( IllegalArgumentException iae ) {
			fail();
		}
	}
	
	// A sized file which should FAIL the 'whole number of sectors' test
	public void testSize1000() throws IOException {
		File f = new File( "src/test/resources/1000" );
		if( !f.exists() )
			return;
		UnmanagedDisk ud = new DiskImage( f );
		try {
			FlatDisk fd = new FlatDisk( ud, Session.CANNED );
			fail();
		} catch( IllegalArgumentException iae ) {
			System.out.println( "Expected: " + iae );
		}
	}


	public void testWriteCanned1() throws IOException {
		File f = new File( "src/test/resources/64k" );
		if( !f.exists() )
			return;
		testWriteCanned( f );
	}

	public void testWriteCanned2() throws IOException {
		File f = new File( "src/test/resources/1m" );
		if( !f.exists() )
			return;
		testWriteCanned( f );
	}

	private void testWriteCanned( File f ) throws IOException {
		UnmanagedDisk ud = new DiskImage( f );
		FlatDisk fd = new FlatDisk( ud, Session.CANNED );

		File output = new File( f.getParent(),
								f.getName() + ManagedDisk.FILESUFFIX );
		System.out.println( output );
		fd.writeTo( output );
		assertEquals( f.length() + 512, output.length() );
	}

	public void testReadManagedCanned1() throws IOException {
		File raw = new File( "src/test/resources/64k" );
		if( !raw.exists() )
			return;
		testReadManagedCanned( raw );
	}

	public void testReadManagedCanned2() throws IOException {
		File raw = new File( "src/test/resources/1m" );
		if( !raw.exists() )
			return;
		testReadManagedCanned( raw );
	}

	private void testReadManagedCanned( File raw ) throws IOException {
		File managed = new File( raw.getPath() + ManagedDisk.FILESUFFIX );
		if( !managed.exists() )
			return;

		String md5_1 = Utils.md5sum( raw );
		
		ManagedDisk md = ManagedDisk.readFrom( managed );
		assertEquals( md.header.type, ManagedDisk.DiskTypes.FLAT );

		InputStream is = md.getInputStream();
		String md5_2 = Utils.md5sum( is );
		is.close();

		assertEquals( md5_1, md5_2 );
	}

}

// eof
