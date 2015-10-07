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
import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Class for monitoring and cleaning the temp directory of a FileSystemStore. <br>
 * Once running, it checks the temp directory every 5 minutes for files to remove.<br>
 * Files with a valid last modified date of twice the check frequency will be deleted.
 * <br> <br>
 * Why check the temp dir so often? When a FileSystemStore is used in a web server,
 * a network or client error will stop transmitting a disk, and whatever is left
 * gets left in the temp dir.
 */
class TempDirCleaner implements Runnable {
    private static final Log log = LogFactory.getLog(TempDirCleaner.class);

    // How often to check (In milis);
    private static final long FREQUENCY = 5 * 60 * 1000;

    // The directory we are monitoring
    private final File tempDir;

    public TempDirCleaner(File tempDir){
        this.tempDir = tempDir;
    }

    @Override
    public void run() {
        do{
            // Clean files
            cleanFiles();
            // Sleep for 5min
            try{
                Thread.sleep(FREQUENCY);
            } catch(Exception e){
                log.warn("Exception sleeping, running again now");
            }
        } while(true);
    }

    /**
     * Remove any non-active files from the temp directory.
     */
    public void cleanFiles() {
        for (File cur : tempDir.listFiles()) {
            long lastModifiedThreshold = new Date().getTime() - 2 * FREQUENCY;
            try {
                if (cur.lastModified() != 0 && cur.lastModified() < lastModifiedThreshold) {
                    log.debug("Trying to delete file: " + cur);
                    if (!cur.delete()) {
                        log.warn("File not deleted: " + cur);
                    }
                } else {
                    log.debug("File last modified within threshold, not deleting "+cur);
                }
            } catch (Exception e) {
                log.warn("Exception while trying to delete file", e);
            }
        }
    }
}
