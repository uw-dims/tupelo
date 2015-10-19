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

package edu.uw.apl.tupelo.http.server.service;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.uw.apl.commons.tsk4j.digests.BodyFile;
import edu.uw.apl.tupelo.fuse.ManagedDiskFileSystem;
import edu.uw.apl.tupelo.model.ManagedDiskDescriptor;
import edu.uw.apl.tupelo.store.Store;
import edu.uw.apl.tupelo.utils.DiskHashUtils;

/**
 * Monitors and generates the file hashes for managed disks that do not yet have file records. <br>
 * It polls the store for disks without file hashes approximatly every 20 minutes
 */
public class DiskFileRecordService {
    private static final Log log = LogFactory.getLog(DiskFileRecordService.class);

    // How often to check the store, in milis
    private static final long UPDATE_INTERVAL = 20 * 60 * 1000;

    // The store
    private final Store store;
    // The store's MDFS
    private final ManagedDiskFileSystem mdfs;
    // Which disks are next
    private BlockingQueue<ManagedDiskDescriptor> diskQueue;
    // The currently hashing disk (If any)
    private ManagedDiskDescriptor currentDisk;
    // Update thread
    private UpdaterThread updater;
    // Worker thread
    private WorkerThread worker;

    public DiskFileRecordService(Store store, ManagedDiskFileSystem mdfs){
        this.store = store;
        this.mdfs = mdfs;

        diskQueue = new LinkedBlockingQueue<ManagedDiskDescriptor>();
        // Start the updater
        updater = new UpdaterThread();
        updater.start();
        // Start the worker
        worker = new WorkerThread();
        worker.start();
    }

    /**
     * Get the queue of disks to be hashed
     * @return
     */
    public synchronized ManagedDiskDescriptor[] getQueue(){
        return diskQueue.toArray(new ManagedDiskDescriptor[diskQueue.size()]);
    }

    /**
     * Get the disk (If any) that are currently being hashed
     * @return
     */
    public synchronized ManagedDiskDescriptor getCurrentDisk(){
        return currentDisk;
    }

    /**
     * Shut down all threads
     */
    public void stop(){
        worker.interrupt();
        updater.interrupt();
    }

    /**
     * Check for disks without file hashes, queue them, and start processing them.
     */
    public synchronized void checkForUnhashedDisks(){
        log.debug("Starting check for disks without file hashes");
        try{
            Collection<ManagedDiskDescriptor> allDisks = store.enumerate();
            for(ManagedDiskDescriptor mdd : allDisks){
                // Check if the store has hashes
                if(!store.hasFileRecords(mdd) && !diskQueue.contains(mdd)){
                    log.debug("Disk missing file hashes, adding to queue: "+mdd);
                    diskQueue.add(mdd);
                }
            }
        } catch(IOException e){
            log.warn("Exception checking for disks without file hashes", e);
        }
    }

    /**
     * Thread that runs a check for unhashed files every UPDATE_INTERVAL. <br>
     * If interrupted, it will stop
     */
    private class UpdaterThread extends Thread {
        @Override
        public void run() {
            // Sleep for 30 seconds at start up
            // This is so that the server can get everything set up
            try {
                Thread.sleep(30 * 1000);
            } catch(InterruptedException e){
                // Ignore
            }

            do{
                // Check for unhashed disks
                checkForUnhashedDisks();
                try{
                    Thread.sleep(UPDATE_INTERVAL);
                } catch(Exception e){
                    log.debug("Update thread interuppted, stopping");
                    break;
                }
            } while(!isInterrupted());
        }
    }

    /**
     * Worker thread. It will pull disks from the queue and process them.
     */
    private class WorkerThread extends Thread {
        @Override
        public void run() {
            do {
                try {
                    log.debug("File record worker waiting for disk");
                    // Get an available disk from the queue
                    // This will block until one is available
                    ManagedDiskDescriptor diskDescriptor = diskQueue.take();

                    try {
                        // Check that there are still no file records
                        if(store.hasFileRecords(diskDescriptor)){
                            log.debug("Disk has file records, skipping "+diskDescriptor);
                            continue;
                        }

                        log.debug("Starting to process disk: " + diskDescriptor);
                        currentDisk = diskDescriptor;

                        File diskPath = mdfs.pathTo(diskDescriptor);
                        DiskHashUtils hashUtils = new DiskHashUtils(diskPath.getAbsolutePath());

                        // Create the BodyFiles
                        List<BodyFile> bodyFiles = hashUtils.hashDisk();

                        // Check that there are still no file records
                        // If there are multiple instances of the store running, another may have already stored the records
                        if(store.hasFileRecords(diskDescriptor)){
                            log.debug("Disk has file records, skipping "+diskDescriptor);
                            continue;
                        }

                        // Save it
                        log.debug("Saving file records");
                        for(BodyFile bodyFile : bodyFiles){
                            store.putFileRecords(diskDescriptor, bodyFile.records());
                        }
                        
                        hashUtils.close();
                        log.debug("Done getting records for disk " + diskDescriptor);
                    } catch (Exception e) {
                        log.error("Exception getting records disk", e);
                    }
                } catch (InterruptedException e) {
                    log.warn("File record thread interrupted, stopping");
                    break;
                }
            } while (!isInterrupted());
        }
    }
}
