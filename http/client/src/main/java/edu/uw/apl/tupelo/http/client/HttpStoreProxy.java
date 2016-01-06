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
package edu.uw.apl.tupelo.http.client;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.http.HttpStatus;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;

import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.uw.apl.commons.tsk4j.digests.BodyFile.Record;
import edu.uw.apl.tupelo.http.common.ByteArrayAdapter;
import edu.uw.apl.tupelo.model.ManagedDisk;
import edu.uw.apl.tupelo.model.ManagedDiskDescriptor;
import edu.uw.apl.tupelo.model.ManagedDiskDigest;
import edu.uw.apl.tupelo.model.Session;
import edu.uw.apl.tupelo.model.ProgressMonitor;
import edu.uw.apl.tupelo.store.Store;

/**
 * A client side (in the http sense that is) proxy for a Store.
 * Connects to the http.server.StoreServlet.  Kind of like an RMI
 * stub.
 */
@SuppressWarnings({"deprecation", "resource", "unused"})
public class HttpStoreProxy implements Store {
    private static final String JAVA_TYPE = "application/x-java-serialized-object";
    private static final String JSON_TYPE = "application/json";
    private static final String TEXT_TYPE = "text/plain";

    private String server;
    private final Log log;
    private Gson gson;

	public HttpStoreProxy( String s ) {
		if( !s.endsWith( "/" ) )
			s = s + "/";
		this.server = s;
		log = LogFactory.getLog( getClass() );
		gson = new GsonBuilder().registerTypeHierarchyAdapter(byte[].class, new ByteArrayAdapter()).create();
	}

	/**
	 * Get the server's version
	 * @return
	 * @throws IOException
	 */
	public String getServerVersion() throws IOException {
	    HttpGet get = new HttpGet( server + "version" );
	    get.addHeader("Accept", TEXT_TYPE);
	    HttpClient req = new DefaultHttpClient();
	    HttpResponse res = req.execute(get);

	    // Read the response
	    BufferedReader reader = new BufferedReader(new InputStreamReader(res.getEntity().getContent()));
	    String version = "";
	    String line = null;
	    while((line = reader.readLine()) != null){
	        version += line;
	    }

	    return version;
	}

	@Override
	public String toString() {
		// Something useful.  Do we need the class name ??
		return getClass().getName() + ":" + server;
	}
	
	@Override
	public UUID getUUID() throws IOException {
		HttpGet g = new HttpGet( server + "uuid" );
		g.addHeader( "Accept", JAVA_TYPE );
		log.debug( g.getRequestLine() );
		HttpClient req = new DefaultHttpClient( );
		HttpResponse res = req.execute( g );
		HttpEntity he = res.getEntity();
		InputStream is = he.getContent();
		ObjectInputStream ois = new ObjectInputStream( is );
		try {
			UUID result = (UUID)ois.readObject();
			return result;
		} catch( ClassNotFoundException cnfe ) {
			throw new IOException( cnfe );
		} finally {
			ois.close();
		}
	}

	@Override
	public long getUsableSpace() throws IOException {
		HttpGet g = new HttpGet( server + "usablespace" );
		g.addHeader( "Accept", JAVA_TYPE );
		log.debug( g.getRequestLine() );
		HttpClient req = new DefaultHttpClient( );
		HttpResponse res = req.execute( g );
		HttpEntity he = res.getEntity();
		InputStream is = he.getContent();
		ObjectInputStream ois = new ObjectInputStream( is );
		try {
			Long result = (Long)ois.readObject();
			return result;
		} catch( ClassNotFoundException cnfe ) {
			throw new IOException( cnfe );
		} finally {
			ois.close();
		}
	}

	@Override
	public Session newSession() throws IOException {
		// LOOK: is a GET good enough here?  Want non-cacheable...
		HttpPost p = new HttpPost( server + "newsession" );
		p.addHeader( "Accept", JAVA_TYPE );
	
		log.debug( p.getRequestLine() );
		
		HttpClient req = new DefaultHttpClient( );
		HttpResponse res = req.execute( p );
		HttpEntity he = res.getEntity();
		InputStream is = he.getContent();
		ObjectInputStream ois = new ObjectInputStream( is );
		try {
			Session result = (Session)ois.readObject();
			return result;
		} catch( ClassNotFoundException cnfe ) {
			throw new IOException( cnfe );
		} finally {
			ois.close();
		}
	}

	@Override
	public long size( ManagedDiskDescriptor mdd ) throws IOException {
		HttpGet g = new HttpGet( server + "disks/data/size/" + mdd.getDiskID() +
								 "/" + mdd.getSession() );
		log.debug( g.getRequestLine() );
		g.addHeader( "Accept", JAVA_TYPE );
		log.debug( g.getRequestLine() );
		HttpClient req = new DefaultHttpClient( );
		HttpResponse res = req.execute( g );
		HttpEntity he = res.getEntity();
		InputStream is = he.getContent();
		ObjectInputStream ois = new ObjectInputStream( is );
		try {
			long result = (Long)ois.readObject();
			return result;
		} catch( ClassNotFoundException cnfe ) {
			throw new IOException( cnfe );
		} finally {
			ois.close();
		}
	}

	@Override
	public UUID uuid( ManagedDiskDescriptor mdd ) throws IOException {
		HttpGet g = new HttpGet( server + "disks/data/uuid/" + mdd.getDiskID() +
								   "/" + mdd.getSession() );
		log.debug( g.getRequestLine() );
		g.addHeader( "Accept", JAVA_TYPE );
		log.debug( g.getRequestLine() );
		HttpClient req = new DefaultHttpClient( );
		HttpResponse res = req.execute( g );
		HttpEntity he = res.getEntity();
		InputStream is = he.getContent();
		ObjectInputStream ois = new ObjectInputStream( is );
		try {
			UUID result = (UUID)ois.readObject();
			return result;
		} catch( ClassNotFoundException cnfe ) {
			throw new IOException( cnfe );
		} finally {
			ois.close();
		}
	}
	
	/**
	   Slightly awkward implementation, making use of a Pipe.  We have
	   a ManagedDisk as the source of our data, which supports just
	   writeTo( OutputStream ). But the HttpClient InputStreamEntity
	   wants to see an Inputstream.  So, in a new thread, we write the
	   ManagedDisk to the OutputStream side of the Pipe.  The caller
	   thread is then given the input side of the Pipe.
	*/
	@Override
	public void put( final ManagedDisk md ) throws IOException {
		ManagedDiskDescriptor mdd = md.getDescriptor();
		HttpPost p = new HttpPost( server + "disks/data/put/" + mdd.getDiskID() +
								   "/" + mdd.getSession() );
		log.debug( p.getRequestLine() );

		final PipedOutputStream pos = new PipedOutputStream();
		PipedInputStream pis = new PipedInputStream( pos );
		InputStreamEntity ise = new InputStreamEntity(pis,
		        -1, ContentType.APPLICATION_OCTET_STREAM );
		ise.setChunked( true );
		p.setEntity( ise );
		Runnable r = new Runnable() {
				public void run() {
					try {
						md.writeTo( pos );
						pos.close();
					} catch( IOException ioe ) {
						log.error( ioe );
					}
				}
			};
		Thread t = new Thread( r );
		t.start();
		HttpClient req = new DefaultHttpClient( );
		HttpResponse res = req.execute( p );
		try {
			t.join();
		} catch( InterruptedException ie ) {
		}
	}

	@Override
	public synchronized void put( final ManagedDisk md,
								  final ProgressMonitor.Callback cb,
								  final int progressUpdateIntervalSecs )
		throws IOException {

		ManagedDiskDescriptor mdd = md.getDescriptor();
		HttpPost p = new HttpPost( server + "disks/data/put/" +
								   mdd.getDiskID() +
								   "/" + mdd.getSession() );
		log.debug( p.getRequestLine() );

		final PipedOutputStream pos = new PipedOutputStream();
		PipedInputStream pis = new PipedInputStream( pos );
		InputStreamEntity ise = new InputStreamEntity( pis,
		        -1, ContentType.APPLICATION_OCTET_STREAM );
		ise.setChunked( true );
		p.setEntity( ise );
		Runnable r = new Runnable() {
				public void run() {
					try {
						ProgressMonitor pm = new ProgressMonitor
							( md, pos, cb, progressUpdateIntervalSecs );
						pm.start();
						pos.close();
					} catch( IOException ioe ) {
						log.error( ioe );
					}
				}
			};
		Thread t = new Thread( r );
		t.start();
		HttpClient req = new DefaultHttpClient( );
		HttpResponse res = req.execute( p );
		try {
			t.join();
		} catch( InterruptedException ie ) {
		}
	}
	

	@Override
	public ManagedDiskDigest digest( ManagedDiskDescriptor mdd )
		throws IOException {
		HttpGet g = new HttpGet( server + "disks/data/digest/" + mdd.getDiskID() +
								 "/" + mdd.getSession() );
		//		g.addHeader( "Accept", "application/x-java-serialized-object" );
		g.addHeader( "Accept", TEXT_TYPE );
	
		log.debug( g.getRequestLine() );
		
		HttpClient req = new DefaultHttpClient( );
		HttpResponse res = req.execute( g );
		StatusLine sl = res.getStatusLine();
		if( sl.getStatusCode() == HttpStatus.SC_NOT_FOUND )
			return null;
		
		HttpEntity he = res.getEntity();
		InputStream is = he.getContent();
		/*
		  ObjectInputStream ois = new ObjectInputStream( is );
		try {
			List<byte[]>result = (List<byte[]>)ois.readObject();
			return result;
		} catch( ClassNotFoundException cnfe ) {
			throw new IOException( cnfe );
		} finally {
			ois.close();
		}
		*/
		InputStreamReader isr = new InputStreamReader( is );
		ManagedDiskDigest result = ManagedDiskDigest.readFrom( isr );
		isr.close();
		return result;
	}

		
	@SuppressWarnings("unchecked")
	@Override
	public Collection<String> listAttributes( ManagedDiskDescriptor mdd )
		throws IOException {
		HttpGet g = new HttpGet( server + "disks/attr/list/" + mdd.getDiskID() +
								 "/" + mdd.getSession() );
		g.addHeader( "Accept", JAVA_TYPE );
	
		log.debug( g.getRequestLine() );
		
		HttpClient req = new DefaultHttpClient( );
		HttpResponse res = req.execute( g );
		HttpEntity he = res.getEntity();
		InputStream is = he.getContent();
		ObjectInputStream ois = new ObjectInputStream( is );
		try {
			Collection<String>result = (Collection<String>)ois.readObject();
			return result;
		} catch( ClassNotFoundException cnfe ) {
			throw new IOException( cnfe );
		} finally {
			ois.close();
		}
	}

	@Override
	public byte[] getAttribute( ManagedDiskDescriptor mdd, String key )
		throws IOException {
		HttpGet g = new HttpGet( server + "disks/attr/get/" + mdd.getDiskID() +
								 "/" + mdd.getSession() + "/" + key  );
		g.addHeader( "Accept", JAVA_TYPE );
		log.debug( g.getRequestLine() );
		
		HttpClient req = new DefaultHttpClient( );
		HttpResponse res = req.execute( g );
		HttpEntity he = res.getEntity();
		InputStream is = he.getContent();
		ObjectInputStream ois = new ObjectInputStream( is );
		try {
			byte[] result = (byte[])ois.readObject();
			return result;
		} catch( ClassNotFoundException cnfe ) {
			throw new IOException( cnfe );
		} finally {
			ois.close();
		}
	}

	@Override
	public void setAttribute( ManagedDiskDescriptor mdd,
							  String key, byte[] value ) throws IOException {
		HttpPost p = new HttpPost( server + "disks/attr/set/" + mdd.getDiskID() +
								   "/" + mdd.getSession() + "/" + key  );
		log.debug( p.getRequestLine() );

		ByteArrayInputStream bais = new ByteArrayInputStream( value );
		InputStreamEntity ise = new InputStreamEntity
			( bais, value.length, ContentType.APPLICATION_OCTET_STREAM );
		p.setEntity( ise );
		HttpClient req = new DefaultHttpClient( );
		HttpResponse res = req.execute( p );
	}

	/*
	  For the benefit of the fuse-based ManagedDiskFileSystem, so
	  meaningless for a client-side http proxy.  Should never be
	  called.
	*/
	public ManagedDisk locate( ManagedDiskDescriptor mdd ) {
		throw new UnsupportedOperationException( "HttpStoreProxy.locate" );
	}

	@SuppressWarnings("unchecked")
	@Override
	public Collection<ManagedDiskDescriptor> enumerate() throws IOException {
		HttpGet g = new HttpGet( server + "disks/data/enumerate" );
		g.addHeader( "Accept", JAVA_TYPE );
	
		log.debug( g.getRequestLine() );
		
		HttpClient req = new DefaultHttpClient( );
		HttpResponse res = req.execute( g );
		HttpEntity he = res.getEntity();
		InputStream is = he.getContent();
		ObjectInputStream ois = new ObjectInputStream( is );
		try {
			Collection<ManagedDiskDescriptor> result =
				(Collection<ManagedDiskDescriptor>)ois.readObject();
			return result;
		} catch( ClassNotFoundException cnfe ) {
			throw new IOException( cnfe );
		} finally {
			ois.close();
		}
	}

    @Override
    public void putFileRecords(ManagedDiskDescriptor mdd, List<Record> records) throws IOException{
        HttpPost post = new HttpPost(server + "disks/data/put/filerecord/" + mdd.getDiskID() +
                "/" + mdd.getSession());
        log.debug(post.getRequestLine());

        post.setHeader("content-type", JSON_TYPE );
        // Get the body ready
        post.setEntity(new StringEntity(gson.toJson(records)));
        // Make the request
        HttpClient req = new DefaultHttpClient();
        HttpResponse res = req.execute(post);
        // Check the response code
        if(res.getStatusLine().getStatusCode() != HttpStatus.SC_OK){
            log.error("Error putting file records");
        }
    }

    @Override
    public List<ManagedDiskDescriptor> checkForHash(String algorithm, byte[] hash) throws IOException {
        List<byte[]> hashes = new ArrayList<byte[]>(1);
        hashes.add(hash);
        return checkForHashes(algorithm, hashes);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<ManagedDiskDescriptor> checkForHashes(String algorithm, List<byte[]> hashes) throws IOException {
        HttpPost post = new HttpPost(server + "disks/data/filerecord/check");
        post.addHeader( "Accept", JAVA_TYPE );
        log.debug(post.getRequestLine());

        // Get the body ready
        post.setHeader("content-type", JSON_TYPE);
        post.setHeader("algorithm", algorithm);
        post.setEntity( new StringEntity(gson.toJson(hashes)) );

        // Make the request
        HttpClient req = new DefaultHttpClient();
        HttpResponse res = req.execute(post);
        // Get the response
        ObjectInputStream ois = new ObjectInputStream(res.getEntity().getContent());
        try{
            List<ManagedDiskDescriptor> disks = (List<ManagedDiskDescriptor>) ois.readObject();
            ois.close();

            return disks;
        } catch (ClassNotFoundException e){
            log.error("Error getting list of disks with file hash");
            return null;
        }
    }

    @Override
    public List<Record> getRecords(ManagedDiskDescriptor mdd, String algorithm, List<byte[]> hashes) throws IOException {
        HttpPost post = new HttpPost(server + "disks/data/filerecord/"+mdd.getDiskID()+"/"+mdd.getSession());
        post.addHeader("Accept", JSON_TYPE);
        post.addHeader("algorithm", algorithm);
        log.debug(post.getRequestLine());

        // Get the body ready
        post.setHeader("content-type", JSON_TYPE);
        post.setEntity(new StringEntity(gson.toJson(hashes)));

        // Make the request
        HttpClient req = new DefaultHttpClient();
        HttpResponse res = req.execute(post);

        // Read the response
        JsonReader reader = new JsonReader(new InputStreamReader(res.getEntity().getContent()));
        Record[] records = gson.fromJson(reader, Record[].class);

        return Arrays.asList(records);
    }

    @Override
    public boolean hasFileRecords(ManagedDiskDescriptor mdd) throws IOException {
        HttpGet g = new HttpGet( server + "disks/data/filerecord/"+mdd.getDiskID()+"/"+mdd.getSession() );
        g.addHeader( "Accept", JAVA_TYPE );
        log.debug( g.getRequestLine() );
        HttpClient req = new DefaultHttpClient( );
        HttpResponse res = req.execute( g );
        HttpEntity he = res.getEntity();
        InputStream is = he.getContent();
        ObjectInputStream ois = new ObjectInputStream( is );
        try {
            Boolean result = (Boolean)ois.readObject();
            return result;
        } catch( ClassNotFoundException cnfe ) {
            throw new IOException( cnfe );
        } finally {
            ois.close();
        }
    }
}
