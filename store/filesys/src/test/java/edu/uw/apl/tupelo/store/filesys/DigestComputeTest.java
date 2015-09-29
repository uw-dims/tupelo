package edu.uw.apl.tupelo.store.filesys;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import edu.uw.apl.tupelo.model.ManagedDiskDescriptor;
import edu.uw.apl.tupelo.model.ManagedDiskDigest;

public class DigestComputeTest extends junit.framework.TestCase {

	FilesystemStore store;
	
	protected void setUp() {
		store = new FilesystemStore( new File( "test-store" ) );
	}
	
	public void testNull() {
	}

	public void testDigests() throws IOException {
		Collection<ManagedDiskDescriptor> mdds = store.enumerate();
		for( ManagedDiskDescriptor mdd : mdds ) {
			System.out.println( "Digesting: " + mdd );
			ManagedDiskDigest d = store.digest( mdd );
			System.out.println( d );
		}
	}
}

// eof
