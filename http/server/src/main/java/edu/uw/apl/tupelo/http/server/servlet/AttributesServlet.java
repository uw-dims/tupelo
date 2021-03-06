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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.ObjectOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.text.ParseException;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import edu.uw.apl.tupelo.http.server.Constants;
import edu.uw.apl.tupelo.http.server.ContextListener;
import edu.uw.apl.tupelo.http.server.Utils;
import edu.uw.apl.tupelo.model.ManagedDiskDescriptor;
import edu.uw.apl.tupelo.model.Session;
import edu.uw.apl.tupelo.store.Store;


/**
 * A servlet handling just requests (put,get,list) about managed disk
 * attributes.  Recall an attribute for a managed disk is simply a
 * string-valued 'key' and a bytearray-valued 'value'.
 *
 * We split the 'http store' into many servlets simply to avoid one big servlet.
 *
 * The expected url layout (i.e. path entered into web.xml) for this servlet is
 *
 * /disks/attr/get/DID/SID/key
 * /disks/attr/set/DID/SID/key  (POST)
 * /disks/attr/list/DID/SID
 *
 */
public class AttributesServlet extends HttpServlet {

    /**
	 * Auto-generated
	 */
	private static final long serialVersionUID = -4669356230481837640L;
	
	public void init( ServletConfig config ) throws ServletException {
        super.init( config );
		log = LogFactory.getLog( getClass().getPackage().getName() );

		/*
		  Locate our Store handler from the context.  The
		  bootstrapping ContextListener puts it there
		*/
		ServletContext sc = config.getServletContext();
		store = (Store)sc.getAttribute( ContextListener.STORE_KEY );

		// gson object claimed thread-safe, so can be a member...
		GsonBuilder gsonb = new GsonBuilder();
		gsonb.registerTypeAdapter(Session.class, Constants.SESSIONSERIALIZER );
		gson = gsonb.create();

		//		log.info( getClass() + " " + log );

	}
	
	/**
	 * We are mapped to /disks/attr/*, so exactly which operation is
	 * being requested is encoded into the PathInfo
	 */
	public void doGet( HttpServletRequest req, HttpServletResponse res )
		throws IOException, ServletException {

		String sp = req.getServletPath();
		log.debug( "Get.ServletPath: " + sp );
		String pi = req.getPathInfo();
		log.debug( "Get.PathInfo: " + pi );

		if( pi.startsWith( "/get/" ) ) {
			String details = pi.substring( "/get/".length() );
			getAttribute( req, res, details );
		} else if( pi.startsWith( "/list/" ) ) {
			String details = pi.substring( "/list/".length() );
			listAttributes( req, res, details );
		} else {
			res.sendError( HttpServletResponse.SC_NOT_FOUND,
						   "Unknown command '" + pi + "'" );
			return;
		}
	}
	
	/**
	 * We are mapped to /disks/attr/*, so exactly which operation is
	 * being requested is encoded into the PathInfo
	 */
	public void doPost( HttpServletRequest req, HttpServletResponse res )
		throws IOException, ServletException {
		
		String sp = req.getServletPath();
		log.debug( "Post.ServletPath: " + sp );
		String pi = req.getPathInfo();
		log.debug( "Post.PathInfo: " + pi );

		if( pi.startsWith( "/set/" ) ) {
			String details = pi.substring( "/set/".length() );
			setAttribute( req, res, details );
		} else {
			res.sendError( HttpServletResponse.SC_NOT_FOUND,
						   "Unknown command '" + pi + "'" );
			return;
		}
	}

	private void getAttribute( HttpServletRequest req, HttpServletResponse res,
							   String details )
		throws IOException, ServletException {

		log.debug( "getAttribute.details: '" + details  + "'" );

		Matcher m = ATTRPATHREGEX.matcher( details );
		if( !m.matches() ) {
			res.sendError( HttpServletResponse.SC_NOT_FOUND,
						   "Malformed managed disk descriptor: " + details );
			return;
		}
		String diskID = m.group(1);
		Session s = null;
		try {
			s = Session.parse( store.getUUID(), m.group(2) );
		} catch( ParseException notAfterRegexMatch ) {
		}
		ManagedDiskDescriptor mdd = new ManagedDiskDescriptor( diskID, s );
		String key = m.group( ATTRNAMEGROUPINDEX );

		// LOOK: check the content type...
		// String hdr = req.getHeader( "Content-Encoding" );

		byte[] value = store.getAttribute( mdd, key );
		
		if( Utils.acceptsJavaObjects( req ) ) {
			res.setContentType( "application/x-java-serialized-object" );
			OutputStream os = res.getOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream( os );

			/*
			  Having serialization issues with the object returned
			  from the store, what seems to be a HashMap$KeySet.  So
			  expand to a regular List on output.
			*/
			oos.writeObject( value );
		} else if( Utils.acceptsJson( req ) ) {
			res.setContentType( "application/json" );
			String json = gson.toJson( value );
			PrintWriter pw = res.getWriter();
			pw.print( json );
		} else {
			res.setContentType( "text/plain" );
			PrintWriter pw = res.getWriter();
			pw.println( "TODO: Store.getAttribute text/plain" );
		}

	}

	private void setAttribute( HttpServletRequest req, HttpServletResponse res,
							   String details )
		throws IOException, ServletException {

		log.debug( "setAttribute.details: '" + details  + "'" );

		Matcher m = ATTRPATHREGEX.matcher( details );
		if( !m.matches() ) {
			log.warn( "Details not matching regex: " +
					  ATTRPATHREGEX.pattern() );
			res.sendError( HttpServletResponse.SC_NOT_FOUND,
						   "Malformed managed disk descriptor: " + details );
			return;
		}
		String diskID = m.group(1);
		Session s = null;
		try {
			s = Session.parse( store.getUUID(), m.group(2) );
		} catch( ParseException notAfterRegexMatch ) {
		}
		ManagedDiskDescriptor mdd = new ManagedDiskDescriptor( diskID, s );

		String key = m.group( ATTRNAMEGROUPINDEX );

		// LOOK: check the content type...
		// String hdr = req.getHeader( "Content-Encoding" );

		InputStream is = req.getInputStream();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		IOUtils.copy( is, baos );

		byte[] value = baos.toByteArray();
		store.setAttribute( mdd, key, value );
	}

	private void listAttributes( HttpServletRequest req,
								 HttpServletResponse res,
								 String details )
		throws IOException, ServletException {

		Matcher m = Constants.MDDPIREGEX.matcher( details );
		if( !m.matches() ) {
			res.sendError( HttpServletResponse.SC_NOT_FOUND,
						   "Malformed managed disk descriptor: " + details );
			return;
		}
		String diskID = m.group(1);
		Session s = null;
		try {
			s = Session.parse( store.getUUID(), m.group(2) );
		} catch( ParseException notAfterRegexMatch ) {
		}
		ManagedDiskDescriptor mdd = new ManagedDiskDescriptor( diskID, s );

		Collection<String> ss = store.listAttributes( mdd );

		if( Utils.acceptsJavaObjects( req ) ) {
			res.setContentType( "application/x-java-serialized-object" );
			OutputStream os = res.getOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream( os );

			/*
			  Having serialization issues with the object returned
			  from the store, what seems to be a HashMap$KeySet.  So
			  expand to a regular List on output.
			*/
			oos.writeObject( ss );
		} else if( Utils.acceptsJson( req ) ) {
			res.setContentType( "application/json" );
			String json = gson.toJson( ss );
			PrintWriter pw = res.getWriter();
			pw.print( json );
		} else {
			res.setContentType( "text/plain" );
			PrintWriter pw = res.getWriter();
			pw.println( "TODO: Store.attributeSet text/plain" );
		}
}
	
	/*
	  LOOK: we assume the client did NOT send the full session info,
	  including the uuid, because that session must have generated by
	  us anyway ??
	*/
	ManagedDiskDescriptor fromPathInfo( String pathInfo )
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

	static final String ATTRNAMEREGEX = "([\\p{Alnum}_:\\.]+)";
	
	static public final Pattern ATTRPATHREGEX = Pattern.compile
		( Constants.MDDPIREGEX.pattern() + "/" + ATTRNAMEREGEX );
	
	/*
	  The diskID and session regexs may themselves have capturing groups,
	  here we identify the actual group match for each component we want
	*/
	static final int DISKIDGROUPINDEX = 1;
	static final int SESSIONIDGROUPINDEX = 2;
	static final int ATTRNAMEGROUPINDEX = 5;

	private Store store;
	private Gson gson;
	private Log log;

}

// eof

