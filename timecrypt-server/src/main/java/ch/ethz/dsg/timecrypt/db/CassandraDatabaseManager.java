/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.db;

import ch.ethz.dsg.timecrypt.exceptions.TimeCryptStorageException;
import ch.ethz.dsg.timecrypt.index.Chunk;
import ch.ethz.dsg.timecrypt.index.blockindex.BlockTree;
import ch.ethz.dsg.timecrypt.index.blockindex.node.BlockIdUtil;
import ch.ethz.dsg.timecrypt.index.blockindex.node.BlockNode;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.api.core.cql.*;
import com.datastax.oss.driver.api.querybuilder.SchemaBuilder;
import com.datastax.oss.driver.api.querybuilder.schema.CreateKeyspace;
import org.cognitor.cassandra.migration.Database;
import org.cognitor.cassandra.migration.MigrationRepository;
import org.cognitor.cassandra.migration.MigrationTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
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
    private static boolean statementsRdy = false;
    private static boolean migrated = false;
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
    private CqlSession sessionCassandra;
    private Semaphore semWrite;
    private Semaphore semRead;

    public CassandraDatabaseManager(String[] serverNodes, int port, int maxConnections) {
        int numInFligts = maxConnections * 256;
        semWrite = new Semaphore(numInFligts / 4, true);
        semRead = new Semaphore(numInFligts / 2, true);

        // The migration framework explicitly advices to use a custom session for the migration
        migrate(serverNodes, port);
        this.sessionCassandra = connectToCluster(serverNodes, port, maxConnections);
        createStatements();
    }

    public CqlSession getSessionCassandra() {
        return sessionCassandra;
    }

    private CqlSession connectToCluster(String[] serverNodes, int port, int maxConnections) {
        // TODO: Before switching to Datastax 4.5 there was a way to note the min connections:
        // poolingOptions.setConnectionsPerHost(HostDistance.LOCAL, minConnections, maxConnections);
        // poolingOptions.setConnectionsPerHost(HostDistance.REMOTE, minConnections, maxConnections);

        DriverConfigLoader loader = DriverConfigLoader.programmaticBuilder()
                .withInt(DefaultDriverOption.CONNECTION_POOL_LOCAL_SIZE, maxConnections)
                .withInt(DefaultDriverOption.CONNECTION_POOL_REMOTE_SIZE, maxConnections)
                .endProfile()
                .build();

        // TODO: Maybe give possibility to explicitly give a DC
        CqlSessionBuilder builder = CqlSession.builder()
                .withLocalDatacenter("datacenter1")
                .withKeyspace(KEY_SPACE)
                .withConfigLoader(loader);

        for (String node : serverNodes) {
            builder.addContactPoint(new InetSocketAddress(node, port));
        }

        int numInFligts = maxConnections * 256;
        semWrite = new Semaphore(numInFligts / 4, true);
        semRead = new Semaphore(numInFligts / 2, true);
        return builder.build();
    }

    private void migrate(String[] serverNodes, int port) {
        synchronized (CassandraDatabaseManager.class) {
            if (!migrated) {
                // Do a super long request timeout since the migrations take up to 10 sek
                DriverConfigLoader loader = DriverConfigLoader.programmaticBuilder()
                        .withInt(DefaultDriverOption.REQUEST_TIMEOUT, 50000)
                        .endProfile()
                        .build();

                // TODO: Maybe give possibility to explicitly give a DC
                CqlSessionBuilder builder = CqlSession.builder()
                        .withLocalDatacenter("datacenter1")
                        .withConfigLoader(loader);

                for (String node : serverNodes) {
                    builder.addContactPoint(new InetSocketAddress(node, port));
                }

                CqlSession sessionCassandra = builder.build();

                CreateKeyspace createKeyspace = SchemaBuilder.createKeyspace(KEY_SPACE)
                        .ifNotExists()
                        .withSimpleStrategy(1);

                LOGGER.info("Creating keyspace if not existing.");

                sessionCassandra.execute(createKeyspace.build());

                MigrationTask migration = new MigrationTask(new Database(sessionCassandra, KEY_SPACE),
                        new MigrationRepository());
                migration.migrate();

                migrated = true;
            }
        }
    }

    private void createStatements() {
        synchronized (CassandraDatabaseManager.class) {
            // only prepare the statements if it was not already done - this is explicitly relevant for testing since
            // the CassandraDatabaseManager gets called several times there
            if (!statementsRdy) {
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

    private CompletionStage<AsyncResultSet> addCallbackRead(CompletionStage<AsyncResultSet> resultSetCompletionStage) {
        resultSetCompletionStage.whenComplete(
                ((asyncResultSet, throwable) -> {
                    if (throwable != null) {
                        logger.error("Read failed : " + throwable.getMessage());
                    }
                    semRead.release();
                }));
        return resultSetCompletionStage;
    }

    private void throttleWrite() {
        try {
            semWrite.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private CompletionStage<AsyncResultSet> addCallbackWrite(CompletionStage<AsyncResultSet> resultSetCompletionStage) {
        resultSetCompletionStage.whenComplete(
                ((asyncResultSet, throwable) -> {
                    if (throwable != null) {
                        logger.error("Write failed : " + throwable.getMessage());
                    }
                    semWrite.release();
                }));
        return resultSetCompletionStage;
    }


    public CompletionStage<AsyncResultSet> deleteAllFor(String user, long uid) {
        throttleWrite();
        BatchStatement statement = BatchStatement.newInstance(DefaultBatchType.LOGGED);
        statement.add(deleteTreeblockstore.bind().setString(0, user).setLong(1, uid));
        statement.add(deleteChunkstore.bind().setString(0, user).setLong(1, uid));
        statement.add(deleteTreestore.bind().setString(0, user).setLong(1, uid));
        CompletionStage<AsyncResultSet> var = sessionCassandra.executeAsync(statement);
        return addCallbackWrite(var);
    }

    public CompletionStage<AsyncResultSet> deleteAllIndexFor(String user, long uid) {
        throttleWrite();
        BatchStatement statement = BatchStatement.newInstance(DefaultBatchType.LOGGED);
        statement.add(deleteTreeblockstore.bind().setString(0, user).setLong(1, uid));
        statement.add(deleteTreestore.bind().setString(0, user).setLong(1, uid));
        return addCallbackWrite(sessionCassandra.executeAsync(statement));
    }

    public CompletionStage<AsyncResultSet> deleteAllChunksFor(String user, long uid) {
        throttleWrite();
        BatchStatement statement = BatchStatement.newInstance(DefaultBatchType.LOGGED);
        statement.add(deleteChunkstore.bind().setString(0, user).setLong(1, uid));
        return addCallbackWrite(sessionCassandra.executeAsync(statement));
    }

    public CompletionStage<AsyncResultSet> insertTree(String user, long uid, BlockNode rootnode, int verison, int k) {
        throttleWrite();
        BoundStatement bound = insertTreestore.bind()
                .setString(0, user)
                .setLong(1, uid)
                .setLong(2, rootnode.getId())
                .setByteBuffer(3, ByteBuffer.wrap(rootnode.encodeContent()))
                .setInt(4, verison)
                .setInt(5, k);


        return addCallbackWrite(sessionCassandra.executeAsync(bound));
    }

    public CompletionStage<AsyncResultSet> insertBlocks(String user, long uid, List<BlockNode> nodes) {
        throttleWrite();
        BatchStatementBuilder batch = BatchStatement.builder(DefaultBatchType.LOGGED);
               // newInstance(DefaultBatchType.LOGGED).;
        for (BlockNode node : nodes) {
            BoundStatement bound = insertBLOCK.bind()
                    .setString(0, user)
                    .setLong(1, uid)
                    .setLong(2, node.getId())
                    .setInt(3, node.getVersion())
                    .setByteBuffer(4, ByteBuffer.wrap(node.encodeContent()));
            batch.addStatement(bound);
        }
        return addCallbackWrite(sessionCassandra.executeAsync(batch.build()));
    }

    public CompletionStage<AsyncResultSet> insertBlock(String user, long uid, BlockNode node) {
        throttleWrite();
        BoundStatement bound = insertBLOCK.bind()
                .setString(0, user)
                .setLong(1, uid)
                .setLong(2, node.getId())
                .setInt(3, node.getVersion())
                .setByteBuffer(4, ByteBuffer.wrap(node.encodeContent()));
        return addCallbackWrite(sessionCassandra.executeAsync(bound));
    }

    public CompletionStage<AsyncResultSet> loadBlock(String user, long uid, long blockid) {
        throttleRead();
        BoundStatement bound = querySingleBLOCK.bind()
                .setString(0, user)
                .setLong(1, uid)
                .setLong(2, blockid);
        return addCallbackRead(sessionCassandra.executeAsync(bound));
    }

    public CompletionStage<AsyncResultSet> loadTree(String user, long uid) {
        throttleRead();
        BoundStatement bound = querySingleTree.bind()
                .setString(0, user)
                .setLong(1, uid);
        return addCallbackRead(sessionCassandra.executeAsync(bound));
    }

    public BlockTree getTree(CompletionStage<AsyncResultSet> futureSet) throws Exception {
        AsyncResultSet result = futureSet.toCompletableFuture().get();
        Row row = result.one();
        if (row == null)
            throw new TimeCryptStorageException("No tree found", 1);
        long id = row.getLong("root_node");
        int from = BlockIdUtil.getFrom(id), to = BlockIdUtil.getTo(id);
        BlockNode root = new BlockNode(row.getInt("root_version"), from, to,
                BlockNode.decodeNodeContent(row.getByteBuffer("root_content").array()));
        return new BlockTree(row.getInt("k"), root);
    }

    public BlockNode getNode(CompletionStage<AsyncResultSet> futureSet, long blockid) throws Exception {
        AsyncResultSet result = futureSet.toCompletableFuture().get();
        Row row = result.one();
        if (row == null)
            throw new TimeCryptStorageException("No tree found", 1);
        int from = BlockIdUtil.getFrom(blockid), to = BlockIdUtil.getTo(blockid);
        return new BlockNode(row.getInt("version"), from, to,
                BlockNode.decodeNodeContent(row.getByteBuffer("content").array()));
    }

    public CompletionStage<AsyncResultSet> insertChunk(String user, long uid, Chunk chunk) {
        throttleWrite();
        BoundStatement bound = insertChunk.bind()
                .setString(0, user)
                .setLong(1, uid)
                .setInt(2, chunk.getStorageKey())
                .setByteBuffer(3, ByteBuffer.wrap(chunk.getData()));
        return addCallbackWrite(sessionCassandra.executeAsync(bound));
    }

    public CompletionStage<AsyncResultSet> loadChunks(String user, long uid, int from, int to) {
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

    public List<Chunk> getChunks(CompletionStage<AsyncResultSet> future) throws Exception {
        List<Chunk> chunks = new ArrayList<>();
        AsyncResultSet result = future.toCompletableFuture().get();

        // TODO check for more pages?
        for (Row row : result.currentPage()) {
            chunks.add(new Chunk(row.getInt("chunk_key"), row.getByteBuffer("chunk").array()));
        }
        return chunks;
    }

    public void close() {
        this.sessionCassandra.close();
    }
}
