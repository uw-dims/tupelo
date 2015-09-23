package edu.uw.apl.tupelo.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Class for getting application properties. <br>
 *
 * It checks the JVM arguments, files ~/.tupelo and /etc/tupelo.prp, and a classpath resource /tupelo.prp file, in that order. <br>
 *
 * JVM args are specified by using -Dtupelo.&lt;Property Name>=&lt;Value>
 */
public class Discovery {
	static final Log log = LogFactory.getLog( Discovery.class );
	
	/**
	  Locate a property value given a property name.  We look in four places
	  for the properties 'file', in this order: <br>

	  1: Specified as JVM arguments, prefixed with tupelo.* (Example: -Dtupelo.store=./test-store) <br>

	  2: In a real file name $(HOME)/.tupelo <br>

	  3: In a real file name /etc/tupelo.prp <br>

	  4: In a resource (classpath-based) named /tupelo.prp <br>

	  The first match wins. <br>

	  It is expected that applications can also override this discovery
	  mechanism via e.g. cmd line options.
	*/
	static public String locatePropertyValue( String prpName ) {

		String result = null;
		
		// Search 1: System property
		result = System.getProperty("tupelo."+prpName);

		// Search 2: a fixed file = $HOME/.tupelo
		if( result == null ) {
			String userHome = System.getProperty( "user.home" );
			File dir = new File( userHome );
			File f = new File( dir, ".tupelo" );
			if( f.isFile() && f.canRead() ) {
				log.info( "Searching in file " + f + " for property " +
						  prpName );
				try {
					FileInputStream fis = new FileInputStream( f );
					Properties p = new Properties();
					p.load( fis );
					fis.close();
					result = p.getProperty( prpName );
					log.info( "Located " + result );
				} catch( IOException ioe ) {
					log.info( ioe );
				}
			}
		}

		// Search 3: file = /etc/tupelo.prp
		if(result == null){
		    File f = new File("/etc/tupelo.prp");
		    if(f.isFile() && f.canRead()){
		        log.info("Searching in file "+f+" for property "+prpName);
		        try {
		            FileInputStream fis = new FileInputStream(f);
		            Properties p = new Properties();
		            p.load(fis);
		            result = p.getProperty(prpName);
		            log.info("Located "+result);
		        } catch(Exception e){
		            log.info(e);
		        }
		    }
		}

		// Search 4: a classpath resource
		if( result == null ) {
			InputStream is = Discovery.class.getResourceAsStream
				( "/tupelo.prp" );
			if( is != null ) {
				try {
					log.info( "Searching in resource for property " +
							  prpName );
					Properties p = new Properties();
					p.load( is );
					result = p.getProperty( prpName );
					log.info( "Located " + result );
					is.close();
				} catch( IOException ioe ) {
					log.info( ioe );
				}
			}
		}
		return result;
	}
}

// eof

	