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
package edu.uw.apl.tupelo.store;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import edu.uw.apl.commons.tsk4j.digests.BodyFile.Record;
import edu.uw.apl.tupelo.model.ManagedDisk;
import edu.uw.apl.tupelo.model.ManagedDiskDescriptor;
import edu.uw.apl.tupelo.model.ManagedDiskDigest;
import edu.uw.apl.tupelo.model.ProgressMonitor;
import edu.uw.apl.tupelo.model.Session;

/**
 * Interface for the Tupelo store
 */
public interface Store {

	/**
	 * Get the store's UUID
	 * @return
	 * @throws IOException
	 */
	public UUID getUUID() throws IOException;

	/**
	 * Get the store's usable space, in bytes
	 * @return
	 * @throws IOException
	 */
	public long getUsableSpace() throws IOException;

	/**
	 * Get a new session
	 * @return
	 * @throws IOException
	 */
	public Session newSession() throws IOException;

	/**
	 * Add a {@link ManagedDisk} to the store
	 * @param md
	 * @throws IOException
	 */
	public void put( ManagedDisk md ) throws IOException;

	/**
	 * Add a {@link ManagedDisk} to the store, and give callbacks about the progress
	 * @param md
	 * @param cb
	 * @param progressUpdateIntervalSecs
	 * @throws IOException
	 */
	public void put( ManagedDisk md, ProgressMonitor.Callback cb,
					 int progressUpdateIntervalSecs ) throws IOException;

	/**
	 * Add a list of file records for a managed disk
	 * @param mdd
	 * @param records
	 * @throws IOException
	 */
	public void putFileRecords(ManagedDiskDescriptor mdd, List<Record> records) throws IOException;

	/**
	 * Check if which, if any, managed disks contain the specified MD5 file hash
	 * @param hash the MD5 hash
	 * @return the list of ManagedDiskDescriptors that contain the hash
	 */
	public List<ManagedDiskDescriptor> checkForHash(byte[] hash) throws IOException;

    /**
     * Check if which, if any, managed disks contain the specified MD5 file hashes
     * @param hashes the MD5 hashes
     * @return the list of ManagedDiskDescriptors that contain the hash
     */
    public List<ManagedDiskDescriptor> checkForHashes(List<byte[]> hashes) throws IOException;

	/**
	 * Check if the store has the file records of the full filesystem available for the Managed Disk
	 * @param mdd
	 * @return
	 */
	public boolean hasFileRecords(ManagedDiskDescriptor mdd) throws IOException;

	/**
	 * Get the {@link Record}s for any files for the managed disk with one of the provided hashes
	 * @param mdd the disk
	 * @param hashes the MD5 hashes to look up
	 * @return
	 */
	public List<Record> getRecords(ManagedDiskDescriptor mdd, List<byte[]> hashes) throws IOException;

	/**
	 * @return size, in bytes, of the managed disk described by the
	 * supplied descriptor. Return -1 if the descriptor does not
	 * identify a managed disk held in this store.
	 */
	public long size( ManagedDiskDescriptor mdd ) throws IOException;

	/**
	 * Get the UUID of a ManagedDisk
	 * @param mdd
	 * @return
	 * @throws IOException
	 */
	public UUID uuid( ManagedDiskDescriptor mdd ) throws IOException;

	/**
	 * Get the {@link ManagedDiskDigest} of a {@link ManagedDisk} in the store
	 * @param mdd
	 * @return
	 * @throws IOException
	 */
	public ManagedDiskDigest digest( ManagedDiskDescriptor mdd )
		throws IOException;

	/**
	 * Get all the attributes associated with the ManagedDisk
	 * @param mdd
	 * @return
	 * @throws IOException
	 */
	public Collection<String> listAttributes( ManagedDiskDescriptor mdd )
		throws IOException;

	/**
	 * Add an attribute about the ManagedDisk
	 * @param mdd
	 * @param key
	 * @param value
	 * @throws IOException
	 */
	public void setAttribute( ManagedDiskDescriptor mdd,
							  String key, byte[] value ) throws IOException;

	/**
	 * Get the value of the attribute for the ManagedDisk. <br>
	 * No attribute exists for the provided key, returns null
	 * 
	 * @param mdd
	 * @param key
	 * @return
	 * @throws IOException
	 */
	public byte[] getAttribute( ManagedDiskDescriptor mdd, String key )
		throws IOException;

	/**
	 * Get the {@link ManagedDisk} associated with the provided {@link ManagedDiskDescriptor}
	 * @param mdd
	 * @return
	 */
	// for the benefit of the fuse-based ManagedDiskFileSystem
	public ManagedDisk locate( ManagedDiskDescriptor mdd );
	
	/**
	 * Get all the {@link ManagedDiskDescriptor}s for the disks in the store
	 * @return
	 * @throws IOException
	 */
	public Collection<ManagedDiskDescriptor> enumerate() throws IOException;

	/*
	 * Use with caution, could LOSE data
	 */
	//	public void clear();
}
