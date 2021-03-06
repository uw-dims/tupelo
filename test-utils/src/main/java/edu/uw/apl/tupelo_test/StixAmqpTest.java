/**
 * Copyright © 2016, University of Washington
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the University of Washington nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL UNIVERSITY OF WASHINGTON BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package edu.uw.apl.tupelo_test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.codec.binary.Hex;
import org.mitre.stix.stix_1.STIXPackage;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.AMQP.BasicProperties;

import edu.uw.apl.stix.cli.Extractor;
import edu.uw.apl.stix.cli.FileInfoExtractor;
import edu.uw.apl.stix.objects.FileObjectObservable;
import edu.uw.apl.stix.utils.HashComposers;
import edu.uw.apl.stix.utils.HashExtractors;
import edu.uw.apl.tupelo.amqp.objects.FileHashQuery;
import edu.uw.apl.tupelo.amqp.objects.FileHashResponse;
import edu.uw.apl.tupelo.amqp.objects.RPCObject;
import edu.uw.apl.tupelo.amqp.objects.Utils;
import edu.uw.apl.tupelo.utils.Discovery;

/**
 * STIX/Tupelo search test application
 */
public class StixAmqpTest {
    static final String EXCHANGE = "tupelo";

    private File inFile;
    private File outFile;
    private FileHashResponse hashResponse;
    private List<String> hashes = new LinkedList<String>();
    private boolean verbose = false;
    private boolean debug = false;

    private String brokerUrl;

    public static void main(String[] args) throws Exception {
        StixAmqpTest app = new StixAmqpTest();
        app.readArgs(args);
        app.getHashes();
        app.sendAmqpRequest();
        app.writeStixOutput();
    }

    public void readArgs(String[] args) throws Exception {
        Options options = new Options();
        options.addOption("u", true, "Broker url. Can also be located on path and in resource");
        options.addOption("v", false, "Enable verbose printing");
        options.addOption("d", false, "Enable debug mode");

        final String USAGE = "inFile outFile";
        final String HEADER = "";
        final String FOOTER = "";

        CommandLineParser clp = new DefaultParser();
        CommandLine commandLine = null;
        try {
            commandLine = clp.parse(options, args);
        } catch (ParseException pe) {
            printUsage(options, USAGE, HEADER, FOOTER);
            System.exit(1);
        }
        if (commandLine.hasOption("v")) {
            verbose = true;
        }
        if (commandLine.hasOption("d")) {
            debug = true;
        }
        if (commandLine.hasOption("u")) {
            brokerUrl = commandLine.getOptionValue("u");
        } else {
            brokerUrl = Discovery.locatePropertyValue("amqp.url");
        }
        if (debug) {
            System.out.println("Using broker URL " + brokerUrl);
        }

        args = commandLine.getArgs();
        if (args.length >= 2) {
            inFile = new File(args[0]);
            outFile = new File(args[1]);
            if (!inFile.isFile()) {
                // like bash would do, write to stderr...
                System.err.println(inFile + ": No such file or directory");
                System.exit(-1);
            }
        } else {
            printUsage(options, USAGE, HEADER, FOOTER);
            System.exit(1);
        }
    }

    public void sendAmqpRequest() throws Exception {
        Gson gson = Utils.createGson(true);

        ConnectionFactory cf = new ConnectionFactory();
        cf.setUri(brokerUrl);
        Connection connection = cf.newConnection();
        Channel channel = connection.createChannel();

        channel.exchangeDeclare(EXCHANGE, "direct");

        String replyQueueName = channel.queueDeclare().getQueue();

        BasicProperties bp = new BasicProperties.Builder().replyTo(replyQueueName).contentType("application/json")
                .correlationId("" + System.currentTimeMillis()).build();

        // LOOK: populate the fhq via add( byte[] )
        FileHashQuery fhq = new FileHashQuery("md5");
        for (String hash : hashes) {
            char[] cs = hash.toCharArray();
            byte[] bs = Hex.decodeHex(cs);
            fhq.add(bs);
        }
        RPCObject<FileHashQuery> rpc1 = RPCObject.asRPCObject(fhq, "filehash");
        String json = gson.toJson(rpc1);

        if (verbose) {
            System.out.println("Sending request '" + json + "'");
        } else {
            System.out.println("Sending AMQP request");
        }

        channel.basicPublish(EXCHANGE, "who-has", bp, json.getBytes());

        QueueingConsumer consumer = new QueueingConsumer(channel);
        boolean autoAck = true;
        channel.basicConsume(replyQueueName, autoAck, consumer);

        QueueingConsumer.Delivery delivery = consumer.nextDelivery();
        String message = new String(delivery.getBody());

        // look: check contentType
        json = message;
        if (verbose) {
            System.out.println("Received reply '" + json + "'");
        }

        Type fhrType = new TypeToken<RPCObject<FileHashResponse>>() {
        }.getType();
        RPCObject<FileHashResponse> rpc2 = gson.fromJson(json, fhrType);
        hashResponse = rpc2.appdata;
        // Check if there were any hits
        if (hashResponse.hits.size() == 0) {
            System.out.println("No matches found in the Tupelo store");
            System.exit(0);
        }

        System.out.println("Tupelo Hits:");
        for (FileHashResponse.Hit h : hashResponse.hits) {
            String hashHex = new String(Hex.encodeHex(h.md5));
            System.out.println(hashHex + " " + h.descriptor + " " + h.path);
        }

        System.out.println(hashResponse.hits.size() + " total hits");

        channel.close();
        connection.close();
    }

    public void writeStixOutput() throws Exception {
        System.out.println("Writing STIX output");

        List<FileObjectObservable> fileObservables = new ArrayList<FileObjectObservable>(hashResponse.hits.size());
        for (FileHashResponse.Hit hit : hashResponse.hits) {
            FileObjectObservable observable = new FileObjectObservable();
            observable.setFileName(hit.path);
            observable.setFileSize(hit.size);
            if(hit.md5 != null){
                String md5Hash = new String(Hex.encodeHex(hit.md5));
                observable.addHash("MD5", md5Hash);
            }
            if(hit.sha1 != null){
                String sha1Hash = new String(Hex.encodeHex(hit.sha1));
                observable.addHash("SHA1", sha1Hash);
            }
            if(hit.sha256 != null){
                String sha256Hash = new String(Hex.encodeHex(hit.sha256));
                observable.addHash("SHA256", sha256Hash);
            }
            fileObservables.add(observable);
        }

        STIXPackage s = HashComposers.composeFileObjectObservables(fileObservables);
        String xmlString = s.toXMLString(true);

        BufferedWriter writer = new BufferedWriter(new FileWriter(outFile));
        writer.write(xmlString);
        writer.flush();
        writer.close();

        System.out.println("STIX document written to " + outFile.getName());
    }

    public void getHashes() throws Exception {
        System.out.println("Reading hashes from STIX " + inFile.getName());
        Extractor extractor = new FileInfoExtractor();
        List<STIXPackage> stixPackages = extractor.getStixPackages(inFile);
        for (STIXPackage stixPackage : stixPackages) {
            hashes.addAll(HashExtractors.extractMD5HexBinary(stixPackage));
        }
        extractor.close();

        System.out.println("Read " + hashes.size() + " hashes");
    }

    /**
     * Print the usage of the class to the console
     * @param os
     * @param usage
     * @param header
     * @param footer
     */
    static protected void printUsage(Options os, String usage, String header, String footer) {
        HelpFormatter hf = new HelpFormatter();
        hf.setWidth(80);
        hf.printHelp(usage, header, os, footer);
    }

}
