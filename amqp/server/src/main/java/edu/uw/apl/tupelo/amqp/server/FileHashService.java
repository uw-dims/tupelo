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
package edu.uw.apl.tupelo.amqp.server;

import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.io.IOException;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.client.AMQP.BasicProperties;

import edu.uw.apl.tupelo.model.ManagedDiskDescriptor;
import edu.uw.apl.tupelo.store.Store;
import edu.uw.apl.commons.tsk4j.digests.BodyFile.Record;
import edu.uw.apl.tupelo.amqp.objects.FileHashQuery;
import edu.uw.apl.tupelo.amqp.objects.FileHashResponse;
import edu.uw.apl.tupelo.amqp.objects.RPCObject;
import edu.uw.apl.tupelo.amqp.objects.Utils;

/**
 * Connect to an AMQP broker (url supplied) and listen for messages on
 * the 'tupelo' exchange, a direct exchange.  The message set we
 * understand (and used as our binding from queue to exchange) is
 * currently just 'who-has'.  The message payload for messages to that
 * queue are is an instance of {@link FileHashQuery}.  We then attempt
 * to load the 'md5' attribute from all managed disks (WHAT,WHEN) in
 * our configured Tupelo store and search for the needles (hashes in
 * FileHashQuery) in the haystacks (managed disk filesystem content).
 * We then reply over amqp with a {@link FileHashResponse} structure.
 * Both FileHashQuery and FileHashResponse are encoded as JSON, tied
 * to a {@link RPCObject} which supplies debug/metadata.
 *
 * LOOK: Currently we know about a single store only.  Extend to many??
 */
public class FileHashService {
    private static final String EXCHANGE = "tupelo";
    private static final String BINDINGKEY = "who-has";
    private static final Log log = LogFactory.getLog(FileHashService.class);

    private final Store store;
    private final String brokerURL;
    private Channel channel;
    private Gson gson;

	public FileHashService( Store s, String brokerURL ) {
		store = s;
		this.brokerURL = brokerURL;
		gson = Utils.createGson( true );
	}
	
	public void start() throws Exception {
		ConnectionFactory cf = new ConnectionFactory();
		cf.setUri( brokerURL );
		Connection connection = cf.newConnection();
		channel = connection.createChannel();

		channel.exchangeDeclare( EXCHANGE, "direct" );
		
		String queueName = channel.queueDeclare().getQueue();
		channel.queueBind( queueName, EXCHANGE, BINDINGKEY );
		log.info( "Binding to exchange '" + EXCHANGE + "' with key '"
				  + BINDINGKEY + "'" );
        QueueingConsumer consumer = new QueueingConsumer(channel);
		boolean autoAck = false;
        channel.basicConsume(queueName, autoAck, consumer);

        while (true) {
			log.info( "Waiting..." );
            QueueingConsumer.Delivery delivery = null;

			try {
				delivery = consumer.nextDelivery();
			} catch( ShutdownSignalException sse ) {
			    log.warn("SignalShutdownException in FileHashService", sse);
				break;
			}

			// LOOK: algorithm match
			
			String message = new String( delivery.getBody() );
			// LOOK: check mime type aka contentType
			String json = message;
			
            log.info( "Received request '" + json + "'");

			Type fhqType =
				new TypeToken<RPCObject<FileHashQuery>>(){}.getType();
			RPCObject<FileHashQuery> rpc1 = null;

			try {
				rpc1 = gson.fromJson( json, fhqType );
			} catch( JsonParseException jpe ) {
				log.warn( jpe + " -> " + json );
				continue;
			}
			FileHashQuery fhq = rpc1.appdata;

            log.info( "Searching for " + fhq.hashes.size() + " hashes..." );

			FileHashResponse fhr = new FileHashResponse( fhq.algorithm );

			// Search for the hashes
			List<ManagedDiskDescriptor> matchingDisks = store.checkForHashes(fhq.algorithm, fhq.hashes);

			// Get the matching record details
			for( ManagedDiskDescriptor mdd : matchingDisks ) {
			    List<Record> records = store.getRecords(mdd, fhq.algorithm, fhq.hashes);

				log.info(records.size() + " hashes match from " + mdd );

				for(Record record : records){
				    fhr.add(record.md5, record.sha1, record.sha256, record.size, mdd, record.path);
				}
			}

			channel.basicAck( delivery.getEnvelope().getDeliveryTag(), false );
			BasicProperties reqProps = delivery.getProperties();
			BasicProperties resProps = new BasicProperties.Builder()
				.contentType( "application/json" )
				.build();
			RPCObject<FileHashResponse> rpc2 = RPCObject.asRPCObject
				( fhr, "filehash" );
			json = gson.toJson( rpc2 );
			channel.basicPublish( "", reqProps.getReplyTo(),
								  resProps, json.getBytes() );
            log.info( "Sending reply '" + json + "'");
        }
	}

	public void stop() throws IOException {
		if( channel == null )
			return;
		try {
            channel.close();
        } catch (TimeoutException e) {
            log.warn("TimeoutException closing channel", e);
        }
	}

}
