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

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sqlite.JDBC;

import edu.uw.apl.tupelo.model.ManagedDiskDescriptor;

/**
 * A store for keeping a disk's filename->MD5 hash information. <br>
 * The implementation uses a SQLite database
 */
public class FileHashStore implements Closeable {
	private static final Log log = LogFactory.getLog(FileHashStore.class);

	// The name of the database file
	private static final String DB_FILE = "fileHash.sqlite";

	// SQL Table/column names
	private static final String TABLE_NAME = "hashes";
	private static final String FILENAME_COL = "filename";
	private static final String HASH_COL = "hash";

	// Table creation SQL statement
	private static final String CREATE_STATEMENT =
			"CREATE TABLE "+TABLE_NAME+" ("+FILENAME_COL+" STRING, "+
			HASH_COL+" BLOB)";

	// Insert SQL statment
	private static final String INSERT_STATEMENT =
			"INSERT INTO "+TABLE_NAME+" ("+FILENAME_COL+", "+HASH_COL+
			") VALUES (?, ?)";
	// Count the number of hash statement
	private static final String COUNT_HASH_STATEMENT =
			"SELECT COUNT(*) FROM "+TABLE_NAME+" WHERE "+HASH_COL+" = ?";
	// Count all rows statement
	private static final String COUNT_STATEMENT =
			"SELECT COUNT(*) FROM "+TABLE_NAME;

	private File sqlFile;
	private ManagedDiskDescriptor mdd;
	private Connection connection;

	/**
	 * Create a new FileHashStore that saves the data in the dataDir folder
	 * for the ManagedDiskDescriptor
	 * @param dataDir
	 * @param mdd
	 * @throws Exception
	 */
	public FileHashStore(File dataDir, ManagedDiskDescriptor mdd) throws IOException{
		try{
			this.mdd = mdd;
			log.info("FileHashStore for "+mdd);

			// Get the file name
			sqlFile = new File(dataDir, DB_FILE);
			// If the file doesn't already exist, set up the tables
			boolean setup = !sqlFile.exists();

			// Open a connection
			connection = DriverManager.getConnection(JDBC.PREFIX + sqlFile.getAbsolutePath());

			if(setup){
				init();
			}
		} catch(SQLException e){
			throw new IOException(e);
		}
	}

	/**
	 * Check if there is any stored data at all
	 * @return
	 * @throws Exception
	 */
	public boolean hasData() throws IOException {
		try{
			PreparedStatement count = connection.prepareStatement(COUNT_STATEMENT);
			ResultSet result = count.executeQuery();
			return result.getInt(1) > 0;
		} catch(SQLException e){
			throw new IOException(e);
		}
	}

	/**
	 * Add all the hashes in the (Filename, hash) map to the database
	 * @param hashes
	 */
	public void addAllHashes(Map<String, byte[]> hashes) throws IOException {
		log.debug("Adding file hashes for disk "+mdd);
		// Add everything
		for(String key : hashes.keySet()){
			addHash(key, hashes.get(key));
		}
	}

	/**
	 * Checks if the provided hash is contained in the store
	 * @param hash
	 * @return
	 */
	public boolean containsFileHash(byte[] hash) throws IOException {
		try{
			PreparedStatement query = connection.prepareStatement(COUNT_HASH_STATEMENT);
			query.setBytes(1, hash);
			ResultSet result = query.executeQuery();
			// If the count is != 0, the hash is in there
			return result.getInt(1) != 0;
		} catch(SQLException e){
			throw new IOException(e);
		}
	}

	/**
	 * Insert a (fileName, hash) pair to the database
	 * @param fileName
	 * @param hash
	 * @throws SQLException
	 */
	public void addHash(String fileName, byte[] hash) throws IOException {
		try{
			PreparedStatement insert = connection.prepareStatement(INSERT_STATEMENT);
			insert.setString(1, fileName);
			insert.setBytes(2, hash);
			insert.execute();
		} catch(SQLException e){
			throw new IOException(e);
		}
	}

	/**
	 * Run initial setup
	 * @throws SQLException
	 */
	private void init() throws SQLException {
		log.debug("Initializing database for managed disk "+mdd);
		Statement statement = connection.createStatement();
		statement.executeUpdate(CREATE_STATEMENT);
	}

	@Override
	public void close() throws IOException {
		try {
			connection.close();
		} catch (SQLException e) {
			throw new IOException(e);
		}
	}

}
