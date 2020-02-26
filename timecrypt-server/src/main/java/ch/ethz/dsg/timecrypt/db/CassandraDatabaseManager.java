/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.db;

import ch.ethz.dsg.timecrypt.index.blockindex.BlockTree;
import ch.ethz.dsg.timecrypt.index.blockindex.node.BlockIdUtil;
import ch.ethz.dsg.timecrypt.index.blockindex.node.BlockNode;
import ch.ethz.dsg.timecrypt.exceptions.TimeCryptStorageException;
import ch.ethz.dsg.timecrypt.index.Chunk;
import com.datastax.driver.core.*;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import org.cognitor.cassandra.migration.Database;
import org.cognitor.cassandra.migration.MigrationRepository;
import org.cognitor.cassandra.migration.MigrationTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

public class CassandraDatabaseManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraDatabaseManager.class);

    private static final String KEY_SPACE = "timecrypt";
    private static final String CQL_INSERT_TREE_TABLE =
            "INSERT INTO treestore (username, uid, root_node, root_content, root_version, k) VALUES (?, ?, ?, ? ,?, ?);";
    private static final String CQL_INSERT_BLOCK_TABLE =
            "INSERT INTO treeblockstore (username, uid, blockid, version, content) VALUES (?, ?, ?, ? ,?);";
    private static final String CQL_INSERT_CHUNK_TABLE =
            "INSERT INTO chunkstore (username, uid, chunk_key, chunk) VALUES (?, ?, ?, ?);";
    private static final String CQL_QUERY_TREE_SINGLE =
            "SELECT root_node, root_content, root_version, k FROM treestore WHERE username = ? AND uid = ?;";
    private static final String CQL_QUERY_BLOCK_SINGLE =
            "SELECT version, content FROM treeblockstore WHERE username = ? AND uid = ? AND blockid = ?;";
    private static final String CQL_QUERY_CHUNK_RANGE =
            "SELECT chunk_key, chunk FROM chunkstore WHERE username = ? AND uid = ? AND chunk_key >= ? AND chunk_key < ?;";

    private static final String CQL_DELETE_TREESTORE =
            "DELETE FROM treestore WHERE username = ? AND uid = ?;";
    private static final String CQL_DELETE_TREEBLOCKSTORE =
            "DELETE FROM treeblockstore WHERE username = ? AND uid = ?;";
    private static final String CQL_DELETE_CHUNKS =
            "DELETE FROM chunkstore WHERE username = ? AND uid = ?;";
    private static final String CQL_CHECK_TREE_EXISTS =
            "SELECT uid FROM treestore WHERE username = ? AND uid = ?;";

    private static Logger logger = LoggerFactory.getLogger(CassandraDatabaseManager.class);
    private Cluster cassandraCluster;
    private Session sessionCassandra;
    private static boolean statementsRdy = false;

    private static PreparedStatement insertTreestore;
    private static PreparedStatement insertBLOCK;
    private static PreparedStatement querySingleBLOCK;
    private static PreparedStatement querySingleTree;
    private static PreparedStatement checkTreeExists;

    private static PreparedStatement insertChunk;
    private static PreparedStatement queryChunks;

    private static PreparedStatement deleteTreestore;
    private static PreparedStatement deleteTreeblockstore;
    private static PreparedStatement deleteChunkstore;

    private Semaphore semWrite;
    private Semaphore semRead;

    public CassandraDatabaseManager(Cluster cassandraCluster, int maxConnections) {
        this.cassandraCluster = cassandraCluster;
        int numInFligts = maxConnections * 256;
        semWrite = new Semaphore(numInFligts / 4, true);
        semRead = new Semaphore(numInFligts / 2, true);
        createSessionAndStatemnts();
    }

    public CassandraDatabaseManager(String[] serverNodes, int port, int minConnections, int maxConnections) {
        connectToCluster(serverNodes, port, minConnections, maxConnections);
        createSessionAndStatemnts();
    }

    private void connectToCluster(String[] serverNodes, int port, int minConnections, int maxConnections) {
        PoolingOptions poolingOptions = new PoolingOptions();
        poolingOptions.setConnectionsPerHost(HostDistance.LOCAL, minConnections, maxConnections);
        poolingOptions.setConnectionsPerHost(HostDistance.REMOTE, minConnections, maxConnections);
        this.cassandraCluster = Cluster.builder()
                .addContactPoints(serverNodes)
                .withPort(port)
                .withPoolingOptions(poolingOptions)
                .build();

        int numInFligts = maxConnections * 256;
        semWrite = new Semaphore(numInFligts / 4, true);
        semRead = new Semaphore(numInFligts / 2, true);
    }

    private void createSessionAndStatemnts() {
        synchronized (this) {
            // only prepare the statements if it was not already done - this is explicitly relevant for testing since
            // the CassandraDatabaseManager gets called several times there
            if (!statementsRdy) {

                StringBuilder createKeyspaceStatement = new StringBuilder("CREATE keyspace IF NOT EXISTS ");
                createKeyspaceStatement.append(KEY_SPACE);
                createKeyspaceStatement.append(" WITH REPLICATION = {'class':'SimpleStrategy', 'replication_factor':1}");
                String query = createKeyspaceStatement.toString();
                LOGGER.info("Creating keyspace if not existing: " + query);

                sessionCassandra = cassandraCluster.connect();
                sessionCassandra.execute(query);
                sessionCassandra.close();

                MigrationTask migration = new MigrationTask(new Database(cassandraCluster, KEY_SPACE),
                        new MigrationRepository());
                migration.migrate();

                sessionCassandra = cassandraCluster.connect(KEY_SPACE);

                insertTreestore = sessionCassandra.prepare(CQL_INSERT_TREE_TABLE);
                insertBLOCK = sessionCassandra.prepare(CQL_INSERT_BLOCK_TABLE);
                querySingleBLOCK = sessionCassandra.prepare(CQL_QUERY_BLOCK_SINGLE);
                querySingleTree = sessionCassandra.prepare(CQL_QUERY_TREE_SINGLE);
                checkTreeExists = sessionCassandra.prepare(CQL_CHECK_TREE_EXISTS);
                insertChunk = sessionCassandra.prepare(CQL_INSERT_CHUNK_TABLE);
                queryChunks = sessionCassandra.prepare(CQL_QUERY_CHUNK_RANGE);
                deleteTreestore = sessionCassandra.prepare(CQL_DELETE_TREESTORE);
                deleteTreeblockstore = sessionCassandra.prepare(CQL_DELETE_TREEBLOCKSTORE);
                deleteChunkstore = sessionCassandra.prepare(CQL_DELETE_CHUNKS);

                statementsRdy = true;
            } else {
                sessionCassandra = cassandraCluster.connect(KEY_SPACE);
            }
        }
    }

    private void throttleRead() {
        try {
            semRead.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private ResultSetFuture addCallbackRead(ResultSetFuture future) {
        Futures.addCallback(future, new FutureCallback<ResultSet>() {
                    @Override
                    public void onSuccess(ResultSet rows) {
                        semRead.release();
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                        logger.info("Read failed : " + throwable.getMessage());
                        semRead.release();
                    }
                }
                , MoreExecutors.directExecutor());
        return future;
    }

    private void throttleWrite() {
        try {
            semWrite.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private ResultSetFuture addCallbackWrite(ResultSetFuture future) {
        Futures.addCallback(future, new FutureCallback<ResultSet>() {
                    @Override
                    public void onSuccess(ResultSet rows) {
                        semWrite.release();
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                        logger.info("Write failed : " + throwable.getMessage());
                        semWrite.release();
                    }
                }
                , MoreExecutors.directExecutor());
        return future;
    }

    public ResultSetFuture deleteAllFor(String user, long uid) {
        throttleWrite();
        BatchStatement statement = new BatchStatement();
        statement.add(deleteTreeblockstore.bind().setString(0, user).setLong(1, uid));
        statement.add(deleteChunkstore.bind().setString(0, user).setLong(1, uid));
        statement.add(deleteTreestore.bind().setString(0, user).setLong(1, uid));
        return addCallbackWrite(sessionCassandra.executeAsync(statement));
    }

    public ResultSetFuture deleteAllIndexFor(String user, long uid) {
        throttleWrite();
        BatchStatement statement = new BatchStatement();
        statement.add(deleteTreeblockstore.bind().setString(0, user).setLong(1, uid));
        statement.add(deleteTreestore.bind().setString(0, user).setLong(1, uid));
        return addCallbackWrite(sessionCassandra.executeAsync(statement));
    }

    public ResultSetFuture deleteAllChunksFor(String user, long uid) {
        throttleWrite();
        BatchStatement statement = new BatchStatement();
        statement.add(deleteChunkstore.bind().setString(0, user).setLong(1, uid));
        return addCallbackWrite(sessionCassandra.executeAsync(statement));
    }

    public ResultSetFuture insertTree(String user, long uid, BlockNode rootnode, int verison, int k) {
        throttleWrite();
        BoundStatement bound = insertTreestore.bind()
                .setString(0, user)
                .setLong(1, uid)
                .setLong(2, rootnode.getId())
                .setBytes(3, ByteBuffer.wrap(rootnode.encodeContent()))
                .setInt(4, verison)
                .setInt(5, k);


        return addCallbackWrite(sessionCassandra.executeAsync(bound));
    }

    public ResultSetFuture insertBlocks(String user, long uid, List<BlockNode> nodes) {
        throttleWrite();
        BatchStatement batch = new BatchStatement();
        for (BlockNode node : nodes) {
            BoundStatement bound = insertBLOCK.bind()
                    .setString(0, user)
                    .setLong(1, uid)
                    .setLong(2, node.getId())
                    .setInt(3, node.getVersion())
                    .setBytes(4, ByteBuffer.wrap(node.encodeContent()));
            batch.add(bound);

        }
        return addCallbackWrite(sessionCassandra.executeAsync(batch));
    }

    public ResultSetFuture insertBlock(String user, long uid, BlockNode node) {
        throttleWrite();
        BoundStatement bound = insertBLOCK.bind()
                .setString(0, user)
                .setLong(1, uid)
                .setLong(2, node.getId())
                .setInt(3, node.getVersion())
                .setBytes(4, ByteBuffer.wrap(node.encodeContent()));
        return addCallbackWrite(sessionCassandra.executeAsync(bound));
    }

    public ResultSetFuture loadBlock(String user, long uid, long blockid) {
        throttleRead();
        BoundStatement bound = querySingleBLOCK.bind()
                .setString(0, user)
                .setLong(1, uid)
                .setLong(2, blockid);
        return addCallbackRead(sessionCassandra.executeAsync(bound));
    }

    public ResultSetFuture loadTree(String user, long uid) {
        throttleRead();
        BoundStatement bound = querySingleTree.bind()
                .setString(0, user)
                .setLong(1, uid);
        return addCallbackRead(sessionCassandra.executeAsync(bound));
    }

    public BlockTree getTree(ResultSetFuture futureSet) throws Exception {
        ResultSet result = futureSet.get();
        Row row = result.one();
        if (row == null)
            throw new TimeCryptStorageException("No tree found", 1);
        long id = row.getLong("root_node");
        int from = BlockIdUtil.getFrom(id), to = BlockIdUtil.getTo(id);
        BlockNode root = new BlockNode(row.getInt("root_version"), from, to,
                BlockNode.decodeNodeContent(row.getBytes("root_content").array()));
        return new BlockTree(row.getInt("k"), root);
    }

    public BlockNode getNode(ResultSetFuture futureSet, long blockid) throws Exception {
        ResultSet result = futureSet.get();
        Row row = result.one();
        if (row == null)
            throw new TimeCryptStorageException("No tree found", 1);
        int from = BlockIdUtil.getFrom(blockid), to = BlockIdUtil.getTo(blockid);
        return new BlockNode(row.getInt("version"), from, to,
                BlockNode.decodeNodeContent(row.getBytes("content").array()));
    }

    public ResultSetFuture insertChunk(String user, long uid, Chunk chunk) {
        throttleWrite();
        BoundStatement bound = insertChunk.bind()
                .setString(0, user)
                .setLong(1, uid)
                .setInt(2, chunk.getStorageKey())
                .setBytes(3, ByteBuffer.wrap(chunk.getData()));
        return addCallbackWrite(sessionCassandra.executeAsync(bound));
    }

    public ResultSetFuture loadChunks(String user, long uid, int from, int to) {
        throttleRead();
        BoundStatement bound = queryChunks.bind()
                .setString(0, user)
                .setLong(1, uid)
                .setInt(2, from)
                .setInt(3, to);
        return addCallbackRead(sessionCassandra.executeAsync(bound));
    }

    public boolean checkTreeExists(String user, long uid) {
        ResultSet set = sessionCassandra.execute(checkTreeExists.bind().setString(0, user).setLong(1, uid));
        return set.one() != null;
    }

    public List<Chunk> getChunks(ResultSetFuture future) throws Exception {
        List<Chunk> chunks = new ArrayList<>();
        ResultSet result = future.get();
        for (Row row : result) {
            chunks.add(new Chunk(row.getInt("chunk_key"), row.getBytes("chunk").array()));
        }
        return chunks;
    }

    public void close() {
        this.sessionCassandra.close();
        this.cassandraCluster.close();
    }

}
