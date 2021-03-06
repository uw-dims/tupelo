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
package edu.uw.apl.tupelo.http.server;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import java.io.InputStream;
import java.net.URISyntaxException;
import java.io.IOException;
import java.io.File;
import java.util.Date;
import java.util.Properties;

import edu.uw.apl.tupelo.store.Store;
import edu.uw.apl.tupelo.store.filesys.FilesystemStore;
import edu.uw.apl.tupelo.utils.Discovery;
import edu.uw.apl.tupelo.amqp.server.FileHashService;
import edu.uw.apl.tupelo.fuse.ManagedDiskFileSystem;
import edu.uw.apl.tupelo.http.server.service.DiskFileRecordService;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Server context change (Start/stop) class
 */
public class ContextListener implements ServletContextListener {
	private Log log;

	/**
	 * Key for the version of the Tupelo server (Internal)
	 */
	static public final String VERSIONKEY = "version";

	/**
	 * Key for the data store root file path (Use in property file)
	 */
	static public final String DATA_ROOT_KEY = "dataroot";

	/**
	 * Key for storing the Store in the ServletContext (Internal)
	 */
	static public final String STORE_KEY = "store";

	/**
	 * Key for the AMQP URL to use to connect (Use in property file)
	 */
	static public final String AMQP_BROKER_KEY = "amqp.url";
	/**
	 * Key for storing the AMQP service in the ServletContext (Internal)
	 */
	static public final String AMQP_SERVICE_KEY = "amqpservice";

	/**
	 *  For the ManagedDiskFileSystem object itself... (Internal)
	 */
	static public final String MDFS_OBJ_KEY = "mdfs.obj";

	/**
	 *  For the ManagedDiskFileSystem's mount point, a File object... (Internal)
	 */
	static public final String MDFS_MOUNT_KEY = "mdfs.mount";

	/**
	 * For storing the DiskFileRecordService (Internal)
	 */
	static public final String DISK_FHASH_KEY = "filerecord.service";

	/**
	 * How long (In seconds) to sleep between connection attempts
	 */
	private static final int AMQP_RECONNECT_TIME = 15;

	/**
	 * How many consecutive AMQP reconnection attempts to make before giving up
	 */
	private static final int AMQP_RECONNECT_ATTEMPTS = 100;

	public ContextListener() {
		log = LogFactory.getLog( getClass().getPackage().getName() );
	}
	
	/**
	 * On start up:
	 * <ul>
	 * <li> Get the version information </li>
	 * <li> Find the data store root, and create it if needed </li>
	 * <li> Connect to AMQP and start listening for requests </li>
	 * </ul>
	 */
    public void contextInitialized( ServletContextEvent sce ) {
		log.debug( "ContextInitialized" );

		ServletContext sc = sce.getServletContext();

		try {
			locateVersion( sc );
			locateDataRoot( sc );
			startAMQP( sc );
		} catch( IOException ioe ) {
			log.warn( ioe );
		}
	}

	/**
	   A Maven-based build can be configured (the war plugin) to
	   output various specification/implementation strings into a
	   jar/war. We look for those here.  Alas, it looks like the
	   Package-based inspection approach doesn't work for war files,
	   so we drop down to a more manual way, inspecting the resource
	   META-INF/MANIFEST.MF.

	   @see http://stackoverflow.com/questions/14934299/how-to-get-package-version-at-running-tomcat

	   Testing note: If we are testing the webapp using 'mvn
	   jetty:run', we need to make this 'mvn jetty:run-war', since the
	   former runs the webapp directly from the filesystem, e.g. using
	   src/main/webapp as document root.  And no 'war' packaging is
	   done so the details we are looking for will not exist.
	*/
	private void locateVersion( ServletContext sc ) {
		Package p = getClass().getPackage();
		String version = p.getImplementationVersion();
		if( version == null ) {
			Properties prp = new Properties();
			InputStream is = sc.getResourceAsStream( "/META-INF/MANIFEST.MF" );
			if( is != null ) {
				try {
					prp.load( is );
					is.close();
				} catch( IOException ioe ) {
				}
			}
			version = prp.getProperty( "Implementation-Version" );
		}
		log.info( "Version: " + version );
		sc.setAttribute( VERSIONKEY, version );
	}
	
	/**
	 * Find and set up the data root. <br>
	 * The root is defined by a property called 'dataroot' <br>
	 * See the {@link Discovery} class for where it can be defined, or add it to the ServletContext
	 * @param sc
	 * @throws IOException
	 */
	private void locateDataRoot( ServletContext sc ) throws IOException {
	    // Use the servlet context specified store root, it provided
		String storeRoot = sc.getInitParameter( DATA_ROOT_KEY );
		if( storeRoot == null ) {
		    // No store root specified in the servlet context, try and find it elsewhere
		    storeRoot = Discovery.locatePropertyValue(DATA_ROOT_KEY);
		    // If it still isn't defined, use the default
		    if(storeRoot == null){
		        storeRoot = getDefaultDataStorePath();
		    }
		}
		File dataRoot = new File( storeRoot ).getCanonicalFile();
		dataRoot.mkdirs();
		sc.setAttribute( DATA_ROOT_KEY, dataRoot );
		log.info( "Store Root: " + dataRoot );
		Store store = new FilesystemStore( dataRoot );
		log.info( "Store UUID: " + store.getUUID() );

		// Set up the MDFS
		log.info("Setting up store's MDFS");
        File mountPoint = new File( dataRoot, "mdfs" );
        mountPoint.mkdirs();
        ManagedDiskFileSystem mdfs = new ManagedDiskFileSystem( store );
        try{
            mdfs.mount(mountPoint, true);
        } catch(Exception e){
            log.warn("Excepting mounting MDFS/waiting", e);
        }
        sc.setAttribute( ContextListener.MDFS_OBJ_KEY, mdfs );
        sc.setAttribute( ContextListener.MDFS_MOUNT_KEY, mountPoint );

        // Set up the file hash service
		DiskFileRecordService diskFileHashService = new DiskFileRecordService(store, mdfs);
		sc.setAttribute( STORE_KEY, store );
		sc.setAttribute( DISK_FHASH_KEY, diskFileHashService);
	}

	/**
	 * Returns the full path to ~/.tupelo
	 * @return
	 */
	private String getDefaultDataStorePath(){
	    String userHome = System.getProperty("user.home");
	    File home = new File(userHome);
	    File store = new File(home, ".tupelo");
	    return store.getAbsolutePath();
	}

	/**
	 * @return amqp broker url as a string, or null if not found on any of
	 * the possible search locations
	 */
	private String locateBrokerUrl( ServletContext sc ) {
		String result = null;

		/*
		  Search 1: value supplied in context init param list.  Could
		  be hard-wired into web.xml (bad) or truly supplied by
		  container (OK as long as that file NOT under version
		  control).  If non-null, we use.
		*/
		
		if( result == null ) {
			result = sc.getInitParameter( AMQP_BROKER_KEY );
		}

		// If the value was not in the servlet context, let the Discovery class try and find it
		if( result == null ) {
			result = Discovery.locatePropertyValue(AMQP_BROKER_KEY);
		}
		return result;
	}

	/**
	 * Connect to AMQP
	 * @param sc
	 * @throws IOException
	 */
	private void startAMQP( ServletContext sc ) throws IOException {
		String brokerURL = locateBrokerUrl( sc );
		if( brokerURL == null ) {
			log.warn( "Missing context param: " + AMQP_BROKER_KEY );
			log.warn( "Unable to start AMQP Services" );
			return;
		}
		Store s = (Store)sc.getAttribute( STORE_KEY );
		final FileHashService fhs = new FileHashService( s, brokerURL );
		sc.setAttribute( AMQP_SERVICE_KEY, fhs );
        Runnable r = new Runnable() {
            @Override
            public void run() {
                try {
                    Date lastConnectTry;
                    int reconnectCount = 0;
                    while (reconnectCount < AMQP_RECONNECT_ATTEMPTS) {
                        lastConnectTry = new Date();
                        try {
                            fhs.start();
                        } catch (Exception e) {
                            if (e instanceof URISyntaxException) {
                                throw e;
                            }
                            log.warn("Exception in AMQP FileHashService, restarting", e);
                        }

                        try {
                            Date now = new Date();
                            if (now.getTime() - lastConnectTry.getTime() <= 60 * 1000) {
                                reconnectCount++;
                                log.debug("Reconnect try count = " + reconnectCount);
                            } else {
                                reconnectCount = 1;
                            }
                            log.info("Waiting "+AMQP_RECONNECT_TIME+" sec to reconnect to AMQP");
                            Thread.sleep(AMQP_RECONNECT_TIME * 1000);
                        } catch (Exception e) {
                            // Ignore
                        }
                    }
                    log.warn("More than "+AMQP_RECONNECT_ATTEMPTS+" failed AMQP connection attempts, stopping FileHashService");
                } catch (Exception e) {
                    log.warn("Fatal exception in AMQP FileHashService, disabling", e);
                }
            }
        };
        new Thread(r).start();
    }

	/**
	 * Clean up when the server stops
	 */
	@Override
    public void contextDestroyed( ServletContextEvent sce ) {
		ServletContext sc = sce.getServletContext();
		FileHashService fhs = (FileHashService)sc.getAttribute( AMQP_SERVICE_KEY );
		if( fhs != null ) {
			log.info( "Stopping AMQP service" );
			try {
				fhs.stop();
			} catch( IOException ioe ) {
				log.warn( ioe );
			}
		}

		// Stop the disk file hash service
		DiskFileRecordService diskFileHashService = (DiskFileRecordService) sc.getAttribute(DISK_FHASH_KEY);
		diskFileHashService.stop();

		// Clean up the MDFS
		ManagedDiskFileSystem mdfs = (ManagedDiskFileSystem)sc.getAttribute( MDFS_OBJ_KEY );

		if( mdfs != null ) {
			log.info( "Unmounting MDFS" );
			try {
				mdfs.umount();
			} catch( Exception e ) {
				log.warn( e );
			}
		}
		File mdfsMountPoint = (File)sc.getAttribute( MDFS_MOUNT_KEY );
		if( mdfsMountPoint != null ) {
			log.info( "Deleting MDFS mount point" );
			mdfsMountPoint.delete();
		}
		log.info( "ContextDestroyed" );
	}
}


// eof
