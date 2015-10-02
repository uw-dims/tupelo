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
import java.security.MessageDigest;

import edu.uw.apl.tupelo.model.ManagedDiskDescriptor;
import edu.uw.apl.tupelo.model.Session;

public class FileHashStoreTest extends junit.framework.TestCase {
	private File testDir;
	private FileHashStore store;
	private MessageDigest MD5;

	private static final String FILE_NAME = "/some/file";

	protected void setUp() throws Exception {
		System.out.println("Setting up FileHashStore tests");
		testDir = new File("fileHashCheck");
		testDir.mkdir();
		// Delete on exit
		testDir.deleteOnExit();

		MD5 = MessageDigest.getInstance("MD5");

		ManagedDiskDescriptor mdd = new ManagedDiskDescriptor("hash-test", Session.testSession());
		store = new FileHashStore(testDir, mdd);
	}

	/**
	 * Check that the database fiel exists
	 */
	public void testExists(){
		System.out.println("Checking that the sqlite file exists");
		File db = new File(testDir, "fileHash.sqlite");
		assert(db.exists());
	}

	/**
	 * Test adding/removing/querying for data
	 */
	public void testAddingData() throws Exception {
		// The store should start empty
		System.out.println("Store should start empty");
		assertEquals(store.hasData(), false);

		// MD5 hash the file name
		MD5.update(FILE_NAME.getBytes());
		byte[] digest = MD5.digest();
		MD5.reset();

		// Add the filename/hash
		System.out.println("Adding a filename/hash pair");
		store.addHash(FILE_NAME, digest);

		System.out.println("Store should have data now");
		// The store should have data
		assertEquals(store.hasData(), true);

		// It should find the hash we just added
		System.out.println("Store should find the hash we just added");
		assertEquals(store.containsFileHash(digest), true);

		// Test a hash that isnt in the table
		MD5.update(this.getClass().getName().getBytes());
		byte[] badHash = MD5.digest();
		MD5.reset();

		System.out.println("Store should not find hash that isn't in it");
		assertEquals(store.containsFileHash(badHash), false);
	}

	@Override
	protected void tearDown() throws Exception {
		// Close the store
		store.close();
		// Force delete the test dir
		for(File f : testDir.listFiles()){
			f.delete();
		}
		testDir.delete();
	}

}
