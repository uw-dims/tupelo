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
package edu.uw.apl.tupelo.http.server.servlet;

import java.io.IOException;
import java.io.OutputStream;
import java.io.ObjectOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.PrintWriter;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.UUID;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;

import edu.uw.apl.commons.tsk4j.digests.BodyFile.Record;
import edu.uw.apl.tupelo.http.common.ByteArrayAdapter;
import edu.uw.apl.tupelo.http.server.Constants;
import edu.uw.apl.tupelo.http.server.ContextListener;
import edu.uw.apl.tupelo.http.server.HttpManagedDisk;
import edu.uw.apl.tupelo.http.server.Utils;
import edu.uw.apl.tupelo.model.ManagedDisk;
import edu.uw.apl.tupelo.model.ManagedDiskDescriptor;
import edu.uw.apl.tupelo.model.ManagedDiskDigest;
import edu.uw.apl.tupelo.model.Session;
import edu.uw.apl.tupelo.store.Store;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A servlet handling just requests (put,get) about managed disk
 * data itself.
 *
 * We split the 'http store' into many servlets simply to avoid one big servlet.
 *
 * The expected url layout (i.e. path entered into web.xml) for this
 * servlet is (where DID is 'disk id' and SID is 'session id', both
 * strings):
 *
 * /disks/data/enumerate
 * /disks/data/put/DID/SID
 * /disks/data/put/filerecord/DID/SID
 * /disks/data/size/DID/SID
 * /disks/data/uuid/DID/SID
 * /disks/data/digest/DID/SID
 * /disks/data/filerecord/DID/SID
 * /disks/data/filerecord/check
 *
 *
 * /disks/data/get/DID/SID (TODO, currently no support for retrieving managed data)
 *
 */
public class DataServlet extends HttpServlet {
    private static final String JAVA_CONTENT = "application/x-java-serialized-object";
    private static final String JSON_CONTENT = "application/json";
    private static final String TEXT_CONTENT = "text/plain";
    private Store store;
    private Gson gson;
    private Log log;

	/**
	 * Auto-generated
	 */
	private static final long serialVersionUID = 6793791633653694862L;

	@Override
    public void init( ServletConfig config ) throws ServletException {
        super.init( config );
		log = LogFactory.getLog( getClass().getPackage().getName() );

		/*
		  locate our Store handler from the context.  The bootstrapping
		  ContextListener puts it there
		*/
		ServletContext sc = config.getServletContext();
		store = (Store)sc.getAttribute( ContextListener.STORE_KEY );
		gson = new GsonBuilder().registerTypeHierarchyAdapter(byte[].class, new ByteArrayAdapter()).create();
	}
	
	@Override
	public void doGet( HttpServletRequest req, HttpServletResponse res )
		throws IOException, ServletException {

		String sp = req.getServletPath();
		log.debug( "Get.ServletPath: " + sp );
		String pi = req.getPathInfo();
		log.debug( "Get.PathInfo: " + pi );

		if( pi.equals( "/enumerate" ) ) {
			enumerate( req, res );
		} else if( pi.startsWith( "/digest/" ) ) {
			String details = pi.substring( "/digest/".length() );
			digest( req, res, details );
		} else if( pi.startsWith( "/size/" ) ) {
			String details = pi.substring( "/size/".length() );
			size( req, res, details );
		} else if( pi.startsWith( "/uuid/" ) ) {
			String details = pi.substring( "/uuid/".length() );
			uuid( req, res, details );
		} else if( pi.startsWith("/filerecord/")){
		    String details = pi.substring("/filerecord/".length());
		    hasFileRecord(req, res, details);
		} else {
			res.sendError( HttpServletResponse.SC_NOT_FOUND,
						   "Unknown command '" + pi + "'" );
			return;
		}
	}
	
	/**
	 * We are mapped to /disks/data/*, so exactly which operation is
	 * being requested is encoded into the PathInfo
	 */
	@Override
	public void doPost( HttpServletRequest req, HttpServletResponse res )
		throws IOException, ServletException {
		
		String sp = req.getServletPath();
		log.debug( "Post.ServletPath: " + sp );
		String pi = req.getPathInfo();
		log.debug( "Post.PathInfo: " + pi );

		if(pi.startsWith("/put/filerecord/")){
		    String details = pi.substring("/put/filerecord/".length());
		    addFileRecord(req, res, details);
		} else if( pi.startsWith( "/put/" ) ) {
			String details = pi.substring( "/put/".length() );
			putData( req, res, details );
		} else if(pi.startsWith("/filehash/check")){
		    checkForHash(req, res);
		} else {
			res.sendError( HttpServletResponse.SC_NOT_FOUND,
						   "Unknown command '" + pi + "'" );
			return;
		}
	}

	private void enumerate( HttpServletRequest req, HttpServletResponse res )
		throws IOException, ServletException {

		Collection<ManagedDiskDescriptor> mdds = store.enumerate();
		
		if( Utils.acceptsJavaObjects( req ) ) {
			/*
			  Having serialization issues with the object returned
			  from the store, what seems to be a HashMap$KeySet.  So
			  expand to a regular List on output.
			*/
			List<ManagedDiskDescriptor> asList =
				new ArrayList<ManagedDiskDescriptor>( mdds );
			respondJava(res, asList);
		} else if( Utils.acceptsJson( req ) ) {
			respondJson(res, mdds);
		} else {
			res.setContentType( "text/plain" );
			PrintWriter pw = res.getWriter();
			/*
			  Just to be nice to a web browser users, sort the
			  result so it 'looks good'
			*/
			List<ManagedDiskDescriptor> sorted =
				new ArrayList<ManagedDiskDescriptor>( mdds );
			Collections.sort( sorted, ManagedDiskDescriptor.DEFAULTCOMPARATOR );
			for( ManagedDiskDescriptor mdd : sorted ) {
				pw.println( mdd.toString() );
			}
			pw.close();
		}
	}

	private void checkForHash(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
	    // Get the hash to look for
	    byte[] hash = new byte[req.getContentLength()];
	    InputStream is = req.getInputStream();
	    is.read(hash, 0, hash.length);
	    is.close();

	    // Get the info from the store
	    List<ManagedDiskDescriptor> disks = store.checkForHash(hash);

	    // Shove it back
	    if(Utils.acceptsJavaObjects(req)){
	        respondJava(res, disks);
	    } else {
	        respondJson(res, disks);
	    }
	}

	private void putData( HttpServletRequest req, HttpServletResponse res,
						  String details )
		throws IOException, ServletException {

		log.debug( "Put.details: '" + details  + "'" );

		ManagedDiskDescriptor mdd = null;
		try {
			mdd = fromPathInfo( details );
		} catch( ParseException pe ) {
			log.debug( "put send error" );
			res.sendError( HttpServletResponse.SC_NOT_FOUND,
						   "Malformed managed disk descriptor: " + details );
			return;
		}

		// LOOK: check the content type...
		// String hdr = req.getHeader( "Content-Encoding" );

		log.debug( "MDD: " + mdd );
		InputStream is = req.getInputStream();
		ManagedDisk md = new HttpManagedDisk( mdd, is );
		store.put( md );
		is.close();
	}

	@SuppressWarnings("unchecked")
    private void addFileRecord(HttpServletRequest req, HttpServletResponse res, String details)
            throws IOException, ServletException {
        ManagedDiskDescriptor mdd = null;
        try {
            mdd = fromPathInfo(details);
        } catch (ParseException e) {
            log.debug("Error getting disk");
            res.sendError(HttpServletResponse.SC_NOT_FOUND, "Malformed managed disk descriptor: " + details);
            return;
        }

        String contentType = req.getContentType();
        if (contentType.equals(JAVA_CONTENT)) {
            try {
                // Read the records from the input stream
                ObjectInputStream ois = new ObjectInputStream(req.getInputStream());
                List<Record> records = (List<Record>) ois.readObject();
                // Shove it in the store;
                store.putFileRecords(mdd, records);
                // Send a response
                respondJava(res, true);
            } catch (ClassNotFoundException e) {
                throw new IOException(e);
            }
        } else if (contentType.equals(JSON_CONTENT)) {
            JsonReader reader = new JsonReader(new InputStreamReader(req.getInputStream()));
            reader.setLenient(true);
            // Read the records
            Record[] records = gson.fromJson(reader, Record[].class);

            // Stick it in the store
            store.putFileRecords(mdd, Arrays.asList(records));
            // Send a response
            respondJson(res, true);
        } else {
            // Unknown type
            res.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, "Unknown content type");
        }
    }

	private void hasFileRecord(HttpServletRequest req, HttpServletResponse res,
	        String details) throws IOException, ServletException {
	    log.debug("Checking if disk has file records");

	    ManagedDiskDescriptor mdd = null;
	    try {
	        mdd = fromPathInfo(details);
	    } catch(ParseException e){
	        log.debug("Error getting disk");
	        res.sendError(HttpServletResponse.SC_NOT_FOUND,
	                "Malformed managed disk descriptor: " + details);
	        return;
	    }

	    boolean hasFileHash = store.hasFileRecords(mdd);
	    if(Utils.acceptsJavaObjects(req)){
	        respondJava(res, hasFileHash);
	    } else if(Utils.acceptsJson(req)){
            respondJson(res, hasFileHash);
	    } else {
            respondText(res, hasFileHash);
	    }
	}

	private void size( HttpServletRequest req, HttpServletResponse res,
					   String details )
		throws IOException, ServletException {
		
		log.debug( "size.details: '" + details  + "'" );
		
		ManagedDiskDescriptor mdd = null;
		try {
			mdd = fromPathInfo( details );
		} catch( ParseException pe ) {
			log.debug( "size send error" );
			res.sendError( HttpServletResponse.SC_NOT_FOUND,
						   "Malformed managed disk descriptor: " + details );
			return;
		}

		// LOOK: check the content type...
		// String hdr = req.getHeader( "Content-Encoding" );

		long size = store.size( mdd );
		log.debug( "size.result: " + size );
		
		
		if( Utils.acceptsJavaObjects( req ) ) {
			respondJava(res, size);
		} else if( Utils.acceptsJson( req ) ) {
			respondJson(res, size);
		} else {
			respondText(res, size);
		}

	}

	private void uuid( HttpServletRequest req, HttpServletResponse res,
					   String details )
		throws IOException, ServletException {
		
		log.debug( "uuid.details: '" + details  + "'" );

		ManagedDiskDescriptor mdd = null;
		try {
			mdd = fromPathInfo( details );
		} catch( ParseException pe ) {
			log.debug( "uuid send error" );
			res.sendError( HttpServletResponse.SC_NOT_FOUND,
						   "Malformed managed disk descriptor: " + details );
			return;
		}

		// LOOK: check the content type...
		//String hdr = req.getHeader( "Content-Encoding" );

		UUID uuid = store.uuid( mdd );

		if( Utils.acceptsJavaObjects( req ) ) {
			respondJava(res, uuid);
		} else if( Utils.acceptsJson( req ) ) {
			respondJson(res, uuid);
		} else {
			respondText(res, uuid);
		}

	}

	private void digest( HttpServletRequest req, HttpServletResponse res,
						 String details )
		throws IOException, ServletException {
		
		log.debug( "digest.details: '" + details  + "'" );

		ManagedDiskDescriptor mdd = null;
		try {
			mdd = fromPathInfo( details );
		} catch( ParseException pe ) {
			log.debug( "put send error" );
			res.sendError( HttpServletResponse.SC_NOT_FOUND,
						   "Malformed managed disk descriptor: " + details );
			return;
		}

		// LOOK: check the content type...
		//String hdr = req.getHeader( "Content-Encoding" );

		ManagedDiskDigest digest = store.digest( mdd );
		if( digest == null ) {
			res.sendError( HttpServletResponse.SC_NOT_FOUND,
						   "Missing digest: " + details );
			return;
		}
		
//		if( Utils.acceptsJavaObjects( req ) ) {
//			res.setContentType( "application/x-java-serialized-object" );
//			OutputStream os = res.getOutputStream();
//			ObjectOutputStream oos = new ObjectOutputStream( os );
//			oos.writeObject( digest );
//		} 
		if( Utils.acceptsJson( req ) ) {
			res.setContentType( "application/json" );
			String json = gson.toJson( digest );
			PrintWriter pw = res.getWriter();
			pw.print( json );
		} else {
			res.setContentType( "text/plain" );
			PrintWriter pw = res.getWriter();
			digest.writeTo( pw );
			//			pw.println( "TODO: Store.digest text/plain" );
		}
	}

	private void respondJava(HttpServletResponse res, Object o) throws IOException {
        res.setContentType( JAVA_CONTENT );
        OutputStream os = res.getOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream( os );
        oos.writeObject( o );
        oos.flush();
        oos.close();
	}

	private void respondJson(HttpServletResponse res, Object o) throws IOException {
        res.setContentType( JSON_CONTENT );
        String json = gson.toJson( o );
        PrintWriter pw = res.getWriter();
        pw.print( json );
        pw.flush();
        pw.close();
	}

	private void respondText(HttpServletResponse res, Object o) throws IOException {
	    res.setContentType( TEXT_CONTENT );
        PrintWriter pw = res.getWriter();
        pw.println( "" + o );
        pw.flush();
        pw.close();
	}

	private ManagedDiskDescriptor fromPathInfo( String pathInfo )
		throws ParseException, IOException {

		Matcher m = Constants.MDDPIREGEX.matcher( pathInfo );
		if( !m.matches() ) {
			throw new ParseException( "Malformed managed disk path: " +
									  pathInfo,0 );
		}
		String diskID = m.group(1);
		Session s = Session.parse( store.getUUID(), m.group(2) );
		return new ManagedDiskDescriptor( diskID, s );
	}

}

