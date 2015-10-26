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
import java.lang.reflect.Constructor;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sqlite.JDBC;

import edu.uw.apl.commons.tsk4j.digests.BodyFile.Record;
import edu.uw.apl.tupelo.model.ManagedDiskDescriptor;

/**
 * A store for keeping a disk's file {@link Record} information. <br>
 * The implementation uses a SQLite database
 */
public class FileRecordStore implements Closeable {
	private static final Log log = LogFactory.getLog(FileRecordStore.class);

	// The name of the database file
	private static final String DB_FILE = "fileRecord.sqlite";

	// SQL Table/column names
	private static final String TABLE_NAME = "records";
	// String
	private static final String PATH_COL = "path";
	// byte[]
	private static final String MD5_COL = "md5";
	// long
	private static final String INODE_COL = "inode";
	// short
	private static final String ATTR_TYPE_COL = "attr_type";
	private static final String ATTR_ID_COL = "attr_id";
	// byte
	private static final String NAME_TYPE_COL = "name_type";
	private static final String META_TYPE_COL = "meta_type";
	// int
	private static final String PERM_COL = "perms";
	private static final String UID_COL = "uid";
	private static final String GID_COL = "gid";
	// long
	private static final String SIZE_COL = "size";
	// int
	private static final String ATIME_COL = "atime";
	private static final String MTIME_COL = "mtime";
	private static final String CTIME_COL = "ctime";
	private static final String CRTIME_COL = "crtime";

	// Table creation SQL statement
	private static final String CREATE_STATEMENT =
			"CREATE TABLE "+TABLE_NAME+" ("+
			PATH_COL+" STRING, "+
			MD5_COL+" BLOB, "+
			INODE_COL+" INTEGER, "+
			ATTR_TYPE_COL+" INTEGER, "+
			ATTR_ID_COL+" INTEGER, "+
			NAME_TYPE_COL+" INTEGER, "+
			META_TYPE_COL+" INTEGER, "+
			PERM_COL+" INTEGER, "+
			UID_COL+" INTEGER, "+
			GID_COL+" INTEGER, "+
			SIZE_COL+" INTEGER, "+
			ATIME_COL+" INTEGER, "+
			MTIME_COL+" INTEGER, "+
			CTIME_COL+" INTEGER, "+
			CRTIME_COL+" INTEGER"+
			")";

	// Insert SQL statment
	private static final String INSERT_STATEMENT =
			"INSERT INTO "+TABLE_NAME+" ("+
			PATH_COL+", "+MD5_COL+", "+INODE_COL+", "+ ATTR_TYPE_COL+", "+
			ATTR_ID_COL+", "+NAME_TYPE_COL+", "+META_TYPE_COL+", "+PERM_COL+", "+
			UID_COL+", "+GID_COL+", "+SIZE_COL+", "+ATIME_COL+", "+MTIME_COL+", "
			+CTIME_COL+", "+CRTIME_COL+") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
	// Count the number of hash statement
	private static final String COUNT_HASH_STATEMENT =
			"SELECT COUNT(*) FROM "+TABLE_NAME+" WHERE "+MD5_COL+" = ?";
	// This needs a ? added for each value and then needs to have a closing )
	private static final String COUNT_HASH_IN_STATEMENT =
	        "SELECT COUNT(*) FROM "+TABLE_NAME+" WHERE "+MD5_COL+" IN (";
	// Count all rows statement
	private static final String COUNT_STATEMENT =
			"SELECT COUNT(*) FROM "+TABLE_NAME;
	// Select statement
	private static final String SELECT_RECORD_BY_HASH =
	        "SELECT * FROM "+TABLE_NAME+" WHERE "+MD5_COL+" = ?";
	// Select from multiple hashes
	// This needs a ? added for each potential hash, and a closing )
    private static final String SELECT_RECORD_BY_HASHES =
            "SELECT * FROM "+TABLE_NAME+" WHERE "+MD5_COL+" IN (";

	// The constructor for Record objects
	private static Constructor<Record> recordConstructor;

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
	public FileRecordStore(File dataDir, ManagedDiskDescriptor mdd) throws IOException{
		try{
			this.mdd = mdd;
			// log.info("FileRecordStore for "+mdd);

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
	public void addRecords(List<Record> records) throws IOException {
		log.debug("Adding file records for disk "+mdd);
		// Add everything
		for(Record cur : records){
			addRecord(cur);
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
	 * Check if any of the provided hashes are contained in the store
	 * @param hashes
	 * @return
	 * @throws IOException
	 */
    public boolean containsFileHash(List<byte[]> hashes) throws IOException {
        if (hashes == null || hashes.isEmpty()) {
            throw new IllegalArgumentException("Array must not be empty");
        }

        // For a single hash, use the search for one hash method because it
        // could be faster
        if (hashes.size() == 1) {
            return containsFileHash(hashes.get(0));
        }

        try {
            // The query string needs to be built
            StringBuilder queryBuilder = new StringBuilder(COUNT_HASH_IN_STATEMENT);
            for (int i = 0; i < (hashes.size() - 1); i++) {
                queryBuilder.append("?, ");
            }
            // Now, add the last ? and close the parentheses
            queryBuilder.append("?)");

            // Now, build the statement
            PreparedStatement query = connection.prepareStatement(queryBuilder.toString());
            // The prepared statement setXXXX() methods start with 1
            for (int i = 1; i <= hashes.size(); i++) {
                query.setBytes(i, hashes.get(i - 1));
            }

            // Run the query
            ResultSet result = query.executeQuery();
            return result.getInt(1) != 0;
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

	/**
	 * Insert a {@link Record} to the database
	 * @param record
	 * @throws SQLException
	 */
	public void addRecord(Record record) throws IOException {
	    /*
            PATH_COL+" STRING, "+
            MD5_COL+" BLOB, "+
            INODE_COL+" INTEGER, "+
            ATTR_TYPE_COL+" INTEGER, "+
            ATTR_ID_COL+" INTEGER, "+
            NAME_TYPE_COL+" INTEGER, "+
            META_TYPE_COL+" INTEGER, "+
            PERM_COL+" INTEGER, "+
            UID_COL+" INTEGER, "+
            GID_COL+" INTEGER, "+
            SIZE_COL+" INTEGER, "+
            ATIME_COL+" INTEGER, "+
            MTIME_COL+" INTEGER, "+
            CTIME_COL+" INTEGER, "+
            CRTIME_COL+" INTEGER"+
	     */
		try{
			PreparedStatement insert = connection.prepareStatement(INSERT_STATEMENT);
			insert.setString(1, record.path);
			insert.setBytes(2, record.md5);
			insert.setLong(3, record.inode);
			insert.setShort(4, record.attrType);
			insert.setShort(5, record.attrId);
			insert.setByte(6, record.nameType);
			insert.setByte(7, record.metaType);
			insert.setInt(8, record.perms);
			insert.setInt(9, record.uid);
			insert.setInt(10, record.gid);
			insert.setLong(11, record.size);
			insert.setInt(12, record.atime);
			insert.setInt(13, record.mtime);
			insert.setInt(14, record.ctime);
			insert.setInt(15, record.crtime);
			insert.execute();
		} catch(SQLException e){
			throw new IOException(e);
		}
	}

	/**
	 * Get the list of {@link Record} objects with a matching MD5 hash
	 * @param hash the MD5 hash
	 * @return the list of records
	 * @throws IOException
	 */
    public List<Record> getRecordsFromHash(byte[] hash) throws IOException {
        try {
            // Prep the query
            PreparedStatement query = connection.prepareStatement(SELECT_RECORD_BY_HASH);
            query.setBytes(1, hash);

            // Run the query
            ResultSet result = query.executeQuery();

            // Get the results out
            List<Record> records = new LinkedList<Record>();
            while (result.next()) {
                records.add(getRecordFromResult(result));
            }
            return records;
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    /**
     * Get the list of {@link Record} objects with a matching MD5 hash
     * @param hash the MD5 hash
     * @return the list of records
     * @throws IOException
     */
    public List<Record> getRecordsFromHashes(List<byte[]> hashes) throws IOException {
        try {
            // Use the single lookup method for a single hash
            if (hashes.size() == 1) {
                return getRecordsFromHash(hashes.get(0));
            }
            // Build the query string
            StringBuilder queryBuilder = new StringBuilder(SELECT_RECORD_BY_HASHES);
            for (int i = 0; i < (hashes.size() - 1); i++) {
                queryBuilder.append("?, ");
            }
            // Close off the query
            // The last ? gets added here
            queryBuilder.append("?)");

            // Prep the query
            PreparedStatement query = connection.prepareStatement(queryBuilder.toString());
            for (int i = 1; i <= hashes.size(); i++) {
                query.setBytes(i, hashes.get(i - 1));
            }

            // Run the query
            ResultSet result = query.executeQuery();

            // Get the results out
            List<Record> records = new LinkedList<Record>();
            while (result.next()) {
                records.add(getRecordFromResult(result));
            }
            return records;
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    /**
     * Creates a Record object from the current row of a ResultSet
     * @param result
     * @return
     */
    @SuppressWarnings("unchecked")
    private Record getRecordFromResult(ResultSet result) throws SQLException {
        String path = result.getString(PATH_COL);
        byte[] hash = result.getBytes(MD5_COL);
        long inode = result.getLong(INODE_COL);
        int attrType = result.getInt(ATTR_TYPE_COL);
        int attrId = result.getInt(ATTR_ID_COL);
        int nameType = result.getInt(NAME_TYPE_COL);
        int metaType = result.getInt(META_TYPE_COL);
        int perms = result.getInt(PERM_COL);
        int uid = result.getInt(UID_COL);
        int gid = result.getInt(GID_COL);
        long size = result.getLong(SIZE_COL);
        int atime = result.getInt(ATIME_COL);
        int mtime = result.getInt(MTIME_COL);
        int ctime = result.getInt(CTIME_COL);
        int crtime = result.getInt(CRTIME_COL);

        // Check the constructor
        if (recordConstructor == null) {
            recordConstructor = (Constructor<Record>) Record.class.getDeclaredConstructors()[0];
            recordConstructor.setAccessible(true);
        }

        try {
            // Try and create the record object
            return recordConstructor.newInstance(hash, path, inode, attrType, attrId, nameType, metaType, perms, uid,
                    gid, size, atime, mtime, ctime, crtime);
        } catch (Exception e) {
            log.error("Exception re-creating record from result", e);
            return null;
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
