/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt;

import ch.ethz.dsg.timecrypt.db.CassandraBlockTreeManager;
import ch.ethz.dsg.timecrypt.db.CassandraDatabaseManager;
import ch.ethz.dsg.timecrypt.db.CassandraStorage;
import ch.ethz.dsg.timecrypt.db.CassandraTreeManager;
import ch.ethz.dsg.timecrypt.db.debug.DebugStorage;
import ch.ethz.dsg.timecrypt.index.IStorage;
import ch.ethz.dsg.timecrypt.index.ITreeManager;
import ch.ethz.dsg.timecrypt.index.MemoryTreeManager;
import ch.ethz.dsg.timecrypt.index.blockindex.IBlockTreeFetcher;
import ch.ethz.dsg.timecrypt.index.blockindex.InMemoryCacheBlockTreeManager;
import ch.ethz.dsg.timecrypt.server.NettyRequestManager;
import ch.ethz.dsg.timecrypt.server.TimeCryptServerChannelInitializer;
import ch.ethz.dsg.timecrypt.server.grpc.AuthServerInterceptor;
import ch.ethz.dsg.timecrypt.server.grpc.TimeCryptGRPCServer;
import com.datastax.oss.driver.api.core.AllNodesFailedException;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class Server implements Runnable {

    public static final int DEFAULT_PORT = 15000;
    public static final int DEFAULT_CASSANDRA_PORT = 9042;
    private static final String SERVER_INTERFACE_ENVIRONMENT_VARIABLE = "TIMECRYPT_SERVER_INTERFACE";
    private static final Logger LOGGER = LoggerFactory.getLogger(Server.class);

    private final int block_tree_k_factor;
    private final int timeCryptPort;
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
    private final InterfaceProvider interfaceProvider;

    public Server(int timeCryptPort, int aThreads, int cThreads, int wThreads, int treeCache, int blockCache,
                  String[] cassandraHosts, int cassandraPort, boolean inMemoryOnly, int cassandraMinConnections,
                  int cassandraMaxConnections, InterfaceProvider interfaceProvider) {
        this(timeCryptPort, aThreads, cThreads, wThreads, treeCache, blockCache, cassandraHosts, cassandraPort, 64,
                inMemoryOnly, cassandraMinConnections, cassandraMaxConnections, interfaceProvider);
    }

    public Server(int timeCryptPort, int aThreads, int cThreads, int wThreads, int treeCache, int blockCache,
                  String[] cassandraHosts, int cassandraPort, int kfactor, boolean inMemoryOnly,
                  int cassandraMinConnections, int cassandraMaxConnections, InterfaceProvider interfaceProvider) {
        this.block_tree_k_factor = kfactor;
        this.timeCryptPort = timeCryptPort;
        this.aThreads = aThreads;
        this.cThreads = cThreads;
        this.wThreads = wThreads;
        this.treeCache = treeCache;
        this.blockCache = blockCache;
        this.cassandraHosts = cassandraHosts;
        this.cassandraPort = cassandraPort;
        this.inMemoryOnly = inMemoryOnly;
        this.cassandraMinConnections = cassandraMinConnections;
        this.cassandraMaxConnections = cassandraMaxConnections;
        this.interfaceProvider = interfaceProvider;
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

    public static void main(String[] args) throws RuntimeException {
        int timecryptPort = getIntFromEnv("TIMECRYPT_PORT", DEFAULT_PORT);
        int aThreads = getIntFromEnv("TIMECRYPT_SERVER_GROUP_THREADS", 2);
        int cThreads = getIntFromEnv("TIMECRYPT_WORKER_GROUP_THREADS", 16);
        int wThreads = getIntFromEnv("TIMECRYPT_EVENT_EXECUTOR_THREADS", 32);
        int treeCache = getIntFromEnv("TIMECRYPT_TREE_CACHE", 2000);
        int blockCache = getIntFromEnv("TIMECRYPT_BLOCK_CACHE", 1000);
        int kfactor = getIntFromEnv("TIMECRYPT_K_FACTOR", 64);

        boolean inMemoryTree = getBoolFromEnv("TIMECRYPT_IN_MEMORY", true);
        String cassandraHost = getStringFromEnv("TIMECRYPT_CASSANDRA_HOST", "127.0.0.1");
        int cassandraPort = getIntFromEnv("TIMECRYPT_CASSANDRA_PORT", DEFAULT_CASSANDRA_PORT);
        String[] cassandraHosts = new String[]{cassandraHost};
        int cassandraMinConnections = getIntFromEnv("TIMECRYPT_CASSANDRA_MIN_CONNECTIONS", 2);
        int cassadndraMaxConnections = getIntFromEnv("TIMECRYPT_CASSANDRA_MAX_CONNECTIONS", 16);
        InterfaceProvider implementation = determineImplementation();

        if (args.length >= 8) {
            timecryptPort = Integer.parseInt(args[0]);
            aThreads = Integer.parseInt(args[1]);
            cThreads = Integer.parseInt(args[2]);
            wThreads = Integer.parseInt(args[3]);
            treeCache = Integer.parseInt(args[4]);
            blockCache = Integer.parseInt(args[5]);
            kfactor = Integer.parseInt(args[6]);
            cassandraHosts = new String[args.length - 7];
            System.arraycopy(args, 7, cassandraHosts, 0, cassandraHosts.length);
        }

        Server server = new Server(timecryptPort, aThreads, cThreads, wThreads, treeCache, blockCache, cassandraHosts, cassandraPort, kfactor,
                inMemoryTree, cassandraMinConnections, cassadndraMaxConnections, implementation);
        server.run();
    }

    private static InterfaceProvider determineImplementation() {

        String implementation = System.getenv(SERVER_INTERFACE_ENVIRONMENT_VARIABLE);
        InterfaceProvider interfaceProvider;

        if (implementation == null) {
            interfaceProvider = InterfaceProvider.GRPC_SERVER_INTERFACE;
        } else {
            LOGGER.info("Using the value from environment variable " + SERVER_INTERFACE_ENVIRONMENT_VARIABLE +
                    " for selecting the right server interface.");

            interfaceProvider = null;
            for (InterfaceProvider value : InterfaceProvider.values()) {
                if (value.getExplanation().equals(implementation)) {
                    interfaceProvider = value;
                }
            }

            if (interfaceProvider == null) {
                interfaceProvider = InterfaceProvider.GRPC_SERVER_INTERFACE;
                LOGGER.error("Could not identify the right server interface implementation based on the value" +
                        implementation + " for selecting the right server interface. Falling back to " +
                        interfaceProvider.name());
            } else {
                LOGGER.info("Selected " + interfaceProvider.name() + " as server interface - based on environment " +
                        "variable");
            }
        }
        return interfaceProvider;
    }

    public void run() {
        IBlockTreeFetcher blockTreeFetcher = null;
        ITreeManager treeManager = null;
        IStorage storage = null;

        if (inMemoryOnly) {
            blockTreeFetcher = new InMemoryCacheBlockTreeManager(blockCache);
            treeManager = new MemoryTreeManager(blockTreeFetcher, block_tree_k_factor);
            storage = new DebugStorage();
        } else {
            try {
                CassandraDatabaseManager db;
                db = new CassandraDatabaseManager(cassandraHosts, cassandraPort, cassandraMaxConnections);
                blockTreeFetcher = new CassandraBlockTreeManager(db, treeCache, blockCache);
                treeManager = new CassandraTreeManager((CassandraBlockTreeManager) blockTreeFetcher, db, block_tree_k_factor);
                storage = new CassandraStorage(db);
            } catch (AllNodesFailedException e) {
                LOGGER.error("Could not connect to cassandra", e);
                System.exit(1);
            }
        }

        if (interfaceProvider.equals(InterfaceProvider.NETTY_SERVER_INTERFACE)) {
            runNettyServer(treeManager, storage);
        } else {
            runGrpcServer(treeManager, storage);
        }
    }

    private void runGrpcServer(ITreeManager treeManager, IStorage storage) {

        // TODO: check for nodelay
        // TODO: configure worker groups

        io.grpc.Server server = NettyServerBuilder.forPort(timeCryptPort)
                .addService(new TimeCryptGRPCServer(treeManager, storage))
                .intercept(new AuthServerInterceptor())
                .build();
        try {
            server.start();
        } catch (IOException e) {
            e.printStackTrace();
            LOGGER.error("GRPC server exception", e);
        }

        LOGGER.info("Server started, listening on " + timeCryptPort);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            io.grpc.Server server;

            @Override
            public void run() {
                // Use stderr here since the logger may have been reset by its JVM shutdown hook.
                System.err.println("*** shutting down gRPC server since JVM is shutting down");
                server.shutdown();
                System.err.println("*** server shut down");
            }

            public Thread addServer(io.grpc.Server server) {
                this.server = server;
                return this;
            }
        }.addServer(server));

        LOGGER.info("GRPC server started");

        try {
            server.awaitTermination();
        } catch (InterruptedException e) {
            LOGGER.error("GRPC server interrupted", e);
        }
        LOGGER.info("GRPC server terminated");
    }

    private void runNettyServer(ITreeManager treeManager, IStorage storage) {
        EventLoopGroup serverGroup = new NioEventLoopGroup(aThreads);
        EventLoopGroup workerGroup = new NioEventLoopGroup(cThreads);
        EventExecutorGroup group = new DefaultEventExecutorGroup(wThreads);

        TimeCryptServerChannelInitializer initializer = new TimeCryptServerChannelInitializer(
                new NettyRequestManager(treeManager, storage), group);

        try {
            ServerBootstrap bootStrap = new ServerBootstrap();
            bootStrap.group(serverGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(initializer)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.TCP_NODELAY, true);

            // Bind to port
            bootStrap.bind(timeCryptPort).sync().channel().closeFuture().sync();
        } catch (InterruptedException e) {
            LOGGER.error("Netty server interrupted", e);
        } finally {
            serverGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
            group.shutdownGracefully();
        }
        LOGGER.info("Netty server shut down");
    }

    public enum InterfaceProvider {

        NETTY_SERVER_INTERFACE("NETTY_SERVER_INTERFACE"),
        GRPC_SERVER_INTERFACE("GRPC_SERVER_INTERFACE"),
        ;

        private final String explanation;

        InterfaceProvider(String s) {
            this.explanation = s;
        }

        public String getExplanation() {
            return explanation;
        }
    }
}
