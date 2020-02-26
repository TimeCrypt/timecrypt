/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt;

import ch.ethz.dsg.timecrypt.index.blockindex.IBlockTreeFetcher;
import ch.ethz.dsg.timecrypt.index.blockindex.InMemoryCacheBlockTreeManager;
import ch.ethz.dsg.timecrypt.db.*;
import ch.ethz.dsg.timecrypt.db.debug.DebugStorage;
import ch.ethz.dsg.timecrypt.index.MemoryTreeManager;
import ch.ethz.dsg.timecrypt.server.TimeCryptRequestManager;
import ch.ethz.dsg.timecrypt.server.TimeCryptServerChannelInitializer;
import com.datastax.driver.core.Cluster;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;

public class Server implements Runnable {

    public static final int DEFAULT_PORT = 15000;
    public TimeCryptRequestManager manager = null;
    private final int block_tree_k;
    private final int port;
    private final int aThreads;
    private final int cThreads;
    private final int wThreads;
    private final int treeCache;
    private final int blockCache;
    private final String[] cassandraHosts;
    private final int cassandraPort;
    private final boolean inMemoryOnly;
    private final int cassandraMinConnections;
    private final int cassandraMaxConnections;
    private final Cluster cluster;

    public Server(int port, int aThreads, int cThreads, int wThreads, int treeCache, int blockCache,
                  String[] cassandraHosts, int cassandraPort, boolean inMemoryOnly, int cassandraMinConnections,
                  int cassandraMaxConnections) {
        this(port, aThreads, cThreads, wThreads, treeCache, blockCache, cassandraHosts, cassandraPort, 64,
                inMemoryOnly, cassandraMinConnections, cassandraMaxConnections);
    }

    public Server(int port, int aThreads, int cThreads, int wThreads, int treeCache, int blockCache,
                  Cluster cluster, boolean inMemoryOnly,
                  int cassandraMinConnections, int cassandraMaxConnections) {
        this.block_tree_k = 64;
        this.port = port;
        this.aThreads = aThreads;
        this.cThreads = cThreads;
        this.wThreads = wThreads;
        this.treeCache = treeCache;
        this.blockCache = blockCache;
        this.cassandraHosts = new String[0];
        this.cassandraPort = 0;
        this.cluster = cluster;
        this.inMemoryOnly = inMemoryOnly;
        this.cassandraMinConnections = cassandraMinConnections;
        this.cassandraMaxConnections = cassandraMaxConnections;
    }
    public Server(int port, int aThreads, int cThreads, int wThreads, int treeCache, int blockCache,
                  String[] cassandraHosts, int cassandraPort, int kfactor, boolean inMemoryOnly,
                  int cassandraMinConnections, int cassandraMaxConnections) {
        this.block_tree_k = kfactor;
        this.port = port;
        this.aThreads = aThreads;
        this.cThreads = cThreads;
        this.wThreads = wThreads;
        this.treeCache = treeCache;
        this.blockCache = blockCache;
        this.cassandraHosts = cassandraHosts;
        this.cassandraPort = cassandraPort;
        this.cluster = null;
        this.inMemoryOnly = inMemoryOnly;
        this.cassandraMinConnections = cassandraMinConnections;
        this.cassandraMaxConnections = cassandraMaxConnections;
    }

    private static String getStringFromEnv(String envVarName, String defaultValue) throws RuntimeException {
        String val = System.getenv(envVarName);
        if (val == null && defaultValue != null) {
            val = defaultValue;
        } else if (val == null) {
            throw new RuntimeException("Environment Variable " + envVarName +
                    " not defined and no default provided");
        }
        return val;
    }

    private static boolean getBoolFromEnv(String envVarName, boolean defaultValue) throws RuntimeException {
        String str_val = System.getenv(envVarName);
        boolean val;
        if (str_val != null) {
            val = Boolean.parseBoolean(str_val);
        } else {
            val = defaultValue;
        }
        return val;
    }

    private static int getIntFromEnv(String envVarName, Integer defaultValue) throws RuntimeException {
        String str_val = System.getenv(envVarName);
        int val;
        if (str_val != null) {
            val = Integer.parseInt(str_val);
        } else if (defaultValue != null) {
            val = defaultValue;
        } else {
            throw new RuntimeException("Environment Variable " + envVarName +
                    " not defined and no default provided");
        }
        return val;
    }

    private static int getIntFromEnv(String envVarName) throws RuntimeException {
        return getIntFromEnv(envVarName, null);
    }

    public static void main(String[] args) throws RuntimeException {
        int port = getIntFromEnv("TIMECRYPT_PORT", DEFAULT_PORT);
        int aThreads = getIntFromEnv("TIMECRYPT_SERVER_GROUP_THREADS", 2);
        int cThreads = getIntFromEnv("TIMECRYPT_WORKER_GROUP_THREADS", 16);
        int wThreads = getIntFromEnv("TIMECRYPT_EVENT_EXECUTOR_THREADS", 32);
        int treeCache = getIntFromEnv("TIMECRYPT_TREE_CACHE", 2000);
        int blockCache = getIntFromEnv("TIMECRYPT_BLOCK_CACHE", 1000);
        int kfactor = getIntFromEnv("TIMECRYPT_K_FACTOR", 64);

        boolean inMemoryTree = getBoolFromEnv("TIMECRYPT_IN_MEMORY", false);
        String cassandraHost = getStringFromEnv("TIMECRYPT_CASSANDRA_HOST", "127.0.0.1");
        int cassandraPort = getIntFromEnv("TIMECRYPT_CASSANDRA_PORT", 9042);
        String[] cassandraHosts = new String[]{cassandraHost};
        int cassandraMinConnections = getIntFromEnv("TIMECRYPT_CASSANDRA_MIN_CONNECTIONS", 2);
        int cassadndraMaxConnections = getIntFromEnv("TIMECRYPT_CASSANDRA_MAX_CONNECTIONS", 16);

        if (args.length >= 8) {
            port = Integer.parseInt(args[0]);
            aThreads = Integer.parseInt(args[1]);
            cThreads = Integer.parseInt(args[2]);
            wThreads = Integer.parseInt(args[3]);
            treeCache = Integer.parseInt(args[4]);
            blockCache = Integer.parseInt(args[5]);
            kfactor = Integer.parseInt(args[6]);
            cassandraHosts = new String[args.length - 7];
            System.arraycopy(args, 7, cassandraHosts, 0, cassandraHosts.length);
        }

        Server server = new Server(port, aThreads, cThreads, wThreads, treeCache, blockCache, cassandraHosts, cassandraPort, kfactor,
                inMemoryTree, cassandraMinConnections, cassadndraMaxConnections);
        server.run();
    }

    public void run() {

        IBlockTreeFetcher blockTreeManager;

        if (inMemoryOnly) {
            blockTreeManager = new InMemoryCacheBlockTreeManager(blockCache);
            manager = new TimeCryptRequestManager(new MemoryTreeManager(blockTreeManager, block_tree_k), new DebugStorage());
        } else {
            CassandraDatabaseManager db;
            if(cluster == null) {
                db = new CassandraDatabaseManager(cassandraHosts, cassandraPort
                        , cassandraMinConnections, cassandraMaxConnections);

            } else {
                db = new CassandraDatabaseManager(cluster , cassandraMaxConnections);

            }
            blockTreeManager = new CassandraBlockTreeManager(db, treeCache, blockCache);
            manager = new TimeCryptRequestManager(new CassandraTreeManager((CassandraBlockTreeManager) blockTreeManager, db, block_tree_k),
                    new CassandraStorage(db));
        }

        EventLoopGroup serverGroup = new NioEventLoopGroup(aThreads);
        EventLoopGroup workerGroup = new NioEventLoopGroup(cThreads);
        EventExecutorGroup group = new DefaultEventExecutorGroup(wThreads);


        TimeCryptServerChannelInitializer initializer = new TimeCryptServerChannelInitializer(manager, group);

        try {
            ServerBootstrap bootStrap = new ServerBootstrap();
            bootStrap.group(serverGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(initializer)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.TCP_NODELAY, true);

            // Bind to port
            bootStrap.bind(port).sync().channel().closeFuture().sync();
        } catch (InterruptedException e) {
            //e.printStackTrace();
        } finally {
            serverGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
            group.shutdownGracefully();
        }
    }
}
