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
package edu.uw.apl.tupelo.utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import edu.uw.apl.commons.tsk4j.digests.BodyFile;
import edu.uw.apl.commons.tsk4j.digests.BodyFileBuilder;
import edu.uw.apl.commons.tsk4j.digests.BodyFileBuilder.BuilderCallback;
import edu.uw.apl.commons.tsk4j.filesys.FileSystem;
import edu.uw.apl.commons.tsk4j.image.Image;
import edu.uw.apl.commons.tsk4j.volsys.Partition;
import edu.uw.apl.commons.tsk4j.volsys.VolumeSystem;

/**
 * Class for accessing and hashing information on a disk not managed in
 * a Tupelo store
 *
 */
public class DiskHashUtils {
	private final String path;
	private final Image image;
	
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
	 * Get a {@link BodyFile} for the provided filesystem. <br>
	 * NOTE: The filesystem will be closed when done!
	 * @param fs
	 * @return
	 * @throws IOException
	 */
	public BodyFile hashFileSystem(FileSystem fs) throws IOException {
	    BodyFile bodyFile = BodyFileBuilder.create(fs);
	    fs.close();
	    return bodyFile;
	}

	/**
	 * Hash a filesystem and send the file records back via the callback
	 * @param fs
	 * @param callback
	 * @throws IOException
	 */
	public void hashFileSystem(FileSystem fs, BuilderCallback callback) throws IOException {
	    BodyFileBuilder.create(fs, callback);
	}

	/**
	 * Walk the FileSystem and get a {@link BodyFile} for each partition.
	 * @return List of {@link BodyFile}s
	 */
	public List<BodyFile> hashDisk() throws IOException {
	    List<BodyFile> bodyFiles = new ArrayList<BodyFile>();
	    for(FileSystem fs : getFilesystems()){
	        bodyFiles.add(hashFileSystem(fs));
	    }
	    return bodyFiles;
	}

    /**
     * Hash a disk and send the file records back via the callback
     * @param fs
     * @param callback
     * @throws IOException
     */
	public void hashDisk(BuilderCallback callback) throws IOException {
	    for(FileSystem fs : getFilesystems()){
	        hashFileSystem(fs, callback);
	    }
	}

}
