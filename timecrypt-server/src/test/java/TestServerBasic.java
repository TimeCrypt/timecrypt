/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

import ch.ethz.dsg.timecrypt.BasicClient;
import ch.ethz.dsg.timecrypt.Server;
import ch.ethz.dsg.timecrypt.crypto.LongNodeContent;
import ch.ethz.dsg.timecrypt.db.CassandraBlockTreeManager;
import ch.ethz.dsg.timecrypt.db.CassandraDatabaseManager;
import ch.ethz.dsg.timecrypt.index.Chunk;
import ch.ethz.dsg.timecrypt.index.blockindex.BlockTree;
import ch.ethz.dsg.timecrypt.index.blockindex.DebugBlockTreeManager;
import ch.ethz.dsg.timecrypt.index.blockindex.node.NodeContent;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.IOException;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestServerBasic {
    private static Thread server;
    private static Server runnable;

    private static GenericContainer cassandra;
    private static String[] cassandraHost;
    private static int cassandraPort;

    //@BeforeClass
    public static void startServer() {
        // memory limit memory to 2 GB since cassandra somehow eats 5GB
        cassandra = new GenericContainer<>("cassandra:3.11")
                .withCreateContainerCmdModifier(cmd -> cmd.withMemory((long) 4 * 1024 * 1024 * 1024))
                .withExposedPorts(Server.DEFAULT_CASSANDRA_PORT);
        cassandra.start();

        cassandra.waitingFor(Wait.forLogMessage(".*Starting listening for CQL clients.*\\n", 1));

        cassandraHost = new String[1];
        cassandraHost[0] = cassandra.getContainerIpAddress();
        cassandraPort = cassandra.getMappedPort(Server.DEFAULT_CASSANDRA_PORT);

        runnable = new Server(Server.DEFAULT_PORT, 2, 16, 32, 2000,
                1000, cassandraHost, cassandraPort, false,
                2, 16, Server.InterfaceProvider.NETTY_SERVER_INTERFACE);
        server = new Thread(runnable);
        server.start();

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    //@AfterClass
    public static void stopServer() throws InterruptedException {
        server.interrupt();
        try {
            server.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Shutdown of the embedded Cassandra instance
        cassandra.stop();
    }

    //@Test
    public void testTree() throws Exception {
        DebugBlockTreeManager man = new DebugBlockTreeManager();
        BlockTree tree = man.createTree(1, "1", 2, 1);
        for (int i = 0; i < 16; i++) {
            tree.insert(0, new NodeContent[]{new LongNodeContent(1)}, i, i + 1);
            tree.updateToLatest();
//            System.out.println(tree.toString());
        }

        LongNodeContent cont = (LongNodeContent) tree.getAggregation(0, 10)[0];
//        System.out.println(cont.i);
    }

    //@Test
    public void testTreeSum() throws Exception {
        int iter = 100;
        DebugBlockTreeManager man = new DebugBlockTreeManager();
        BlockTree tree = man.createTree(1, "1", 2, 1);
        for (int i = 0; i < iter; i++) {
            tree.insert(0, new NodeContent[]{new LongNodeContent(1)}, i, i + 1);
            tree.updateToLatest();
        }

        for (int i = 0; i < iter; i++) {
            for (int j = i + 1; j < iter; j++) {
                assertEquals(String.valueOf(j - i), tree.getAggregation(i, j)[0].getStringRepresentation());
                //               System.out.format("From %d To %d ok\n", i, j);
            }
        }
    }

    //@Test
    public void testTreeCassandra() throws Exception {
        CassandraDatabaseManager db = new CassandraDatabaseManager(cassandraHost, cassandraPort, 1);
        CassandraBlockTreeManager man = new CassandraBlockTreeManager(db, 10, 10000);
        BlockTree tree = man.createTree(1, "1", 2, 1);
        for (int i = 0; i < 16; i++) {
            tree.insert(0, new NodeContent[]{new LongNodeContent(1)}, i, i + 1);
            tree.updateToLatest();
//            System.out.println(tree.toString());
        }

        LongNodeContent cont = (LongNodeContent) tree.getAggregation(0, 10)[0];
        //       System.out.println(cont.i);
    }

    //@Test
    public void testTreeCassandraSum() throws Exception {
        CassandraDatabaseManager db = new CassandraDatabaseManager(cassandraHost, cassandraPort, 1);
        CassandraBlockTreeManager man = new CassandraBlockTreeManager(db, 10, 10000);
        int iter = 100;
        BlockTree tree = man.createTree(1, "1", 32, 1);
        for (int i = 0; i < iter; i++) {
            tree.insert(0, new NodeContent[]{new LongNodeContent(1)}, i, i + 1);
            tree.updateToLatest();
        }

        for (int i = 0; i < iter; i++) {
            for (int j = i + 1; j < iter; j++) {
                assertEquals(String.valueOf(j - i), tree.getAggregation(i, j)[0].getStringRepresentation());
                System.out.format("From %d to %d ok\n", i, j);
            }
        }

        man.invalidateCache();

        for (int i = 0; i < iter; i++) {
            for (int j = i + 1; j < iter; j++) {
                assertEquals(String.valueOf(j - i), tree.getAggregation(i, j)[0].getStringRepresentation());
                System.out.format("From %d To %d ok\n", i, j);
            }
            System.out.println("Invalidating caches 2");
            man.invalidateCache();
        }
    }

    //@Test
    public void testTreeCassandraInsertFlush() throws Exception {
        CassandraDatabaseManager db = new CassandraDatabaseManager(cassandraHost, cassandraPort, 1);
        CassandraBlockTreeManager man = new CassandraBlockTreeManager(db, 10, 10000);
        int iter = 100;
        BlockTree tree = man.createTree(1, "1", 32, 1);
        for (int i = 0; i < iter; i++) {
            man.invalidateCache();
            tree.insert(0, new NodeContent[]{new LongNodeContent(1)}, i, i + 1);
            tree.updateToLatest();
        }

        for (int i = 0; i < iter; i++) {
            man.invalidateCache();
            for (int j = i + 1; j < iter; j++) {
                assertEquals(String.valueOf(j - i), tree.getAggregation(i, j)[0].getStringRepresentation());
//                System.out.format("From %d To %d ok\n", i, j);
            }
        }
    }

    //@Test
    public void testTreeCassandraInsertSmallCache() throws Exception {
        CassandraDatabaseManager db = new CassandraDatabaseManager(cassandraHost, cassandraPort, 1);
        CassandraBlockTreeManager man = new CassandraBlockTreeManager(db, 1, 1);
        int iter = 100;
        BlockTree tree = man.createTree(1, "1", 32, 1);
        for (int i = 0; i < iter; i++) {
            man.invalidateCache();
            tree.insert(0, new NodeContent[]{new LongNodeContent(1)}, i, i + 1);
            tree.updateToLatest();
        }

        for (int i = 0; i < iter; i++) {
            man.invalidateCache();
            for (int j = i + 1; j < iter; j++) {
                assertEquals(String.valueOf(j - i), tree.getAggregation(i, j)[0].getStringRepresentation());
//                System.out.format("From %d To %d ok\n", i, j);
            }
        }
    }

    //@Test
    public void testTreeCassandraPar() throws Exception {
        CassandraDatabaseManager db1 = new CassandraDatabaseManager(cassandraHost, cassandraPort, 1);
        CassandraBlockTreeManager man1 = new CassandraBlockTreeManager(db1, 10, 10000);

        CassandraDatabaseManager db2 = new CassandraDatabaseManager(cassandraHost, cassandraPort, 1);
        CassandraBlockTreeManager man2 = new CassandraBlockTreeManager(db2, 10, 10000);

        final int iter = 100;
        TestServerBasic.QueryTask task = new TestServerBasic.QueryTask(man2, iter);
        Thread other = new Thread(task);


        BlockTree tree = man1.createTree(1, "1", 32, 1);
        for (int i = 0; i < iter; i++) {
            tree.insert(0, new NodeContent[]{new LongNodeContent(1)}, i, i + 1);
            tree.updateToLatest();
        }

        other.start();
        for (int i = 0; i < iter; i++) {
            for (int j = i + 1; j < iter; j++) {
                assertEquals(String.valueOf(j - i), tree.getAggregation(i, j)[0].getStringRepresentation());
//                System.out.format("From %d To %d ok\n", i, j);
            }
        }
        other.join();
        assertTrue(task.success);
    }

    //@Test
    public void testTreeCassandraParInsert() throws Exception {
        String user = "6";
        CassandraDatabaseManager db1 = new CassandraDatabaseManager(cassandraHost, cassandraPort, 1);
        CassandraBlockTreeManager man1 = new CassandraBlockTreeManager(db1, 10, 10000);

        CassandraDatabaseManager db2 = new CassandraDatabaseManager(cassandraHost, cassandraPort, 1);
        CassandraBlockTreeManager man2 = new CassandraBlockTreeManager(db2, 10, 10000);

        final int iter = 100;

        BlockTree tree1 = man1.createTree(1, user, 32, 1);
        Thread.sleep(100);
        BlockTree tree2 = man2.fetchTree(1, user);
        for (int i = 0; i < iter; i++) {
            //Thread.sleep(10);
            if (i % 2 == 0) {
                //tree1 = man1.fetchTreeMinVersion(1, user, i);
                tree1.insert(0, new NodeContent[]{new LongNodeContent(1)}, i, i + 1);
            } else {
                //tree2 = man2.fetchTreeMinVersion(1, user, i);
                tree2.insert(0, new NodeContent[]{new LongNodeContent(1)}, i, i + 1);
            }
        }

        for (int i = 0; i < iter; i++) {
            for (int j = i + 1; j < iter; j++) {
                assertEquals(String.valueOf(j - i), tree1.getAggregation(i, j)[0].getStringRepresentation());
                //System.out.format("From %d To %d ok\n", i, j);
            }
        }
    }

    //@Test
    public void basicClient() throws IOException {
        String user = "User1";
        int num = 100;
        int granulatrity = 4;
        BasicClient client = new BasicClient("localhost", Server.DEFAULT_PORT);


        client.createStream(1, user, 1);
        for (int i = 0; i < num; i++) {
            client.insertChunk(new Chunk(i, new byte[1]), 1, user, i, i + 1, i, new LongNodeContent[]{
                    new LongNodeContent(1)
            });
        }

        List<NodeContent[]> res = client.getStatistics(user, 1, 0, num, granulatrity, new int[]{0});
        assertEquals(res.size(), num / granulatrity);

        for (int i = 0; i < res.size(); i++) {
            LongNodeContent cont = (LongNodeContent) res.get(i)[0];
            assertEquals(cont.i, granulatrity);
        }


        List<Chunk> chunks = client.getChunks(user, 1, 0, num);
        assertEquals(chunks.size(), num);

        client.deleteStream(user, 1);

        //assertEquals(((DebugStorage) runnable.manager.getStorage()).keys.size(), 0);

        client.close();
    }

    //@Test
    public void basicClientRand() throws IOException {
        Random rand = new Random(111);
        int numValues = 1024;
        long[] val = new long[numValues];
        for (int i = 0; i < val.length; i++) {
            val[i] = rand.nextInt();
        }

        String user = "User2";
        int[] granularities = new int[]{2, 4, 8, 16, 32, 64};
        BasicClient client = new BasicClient("localhost", Server.DEFAULT_PORT);


        client.createStream(1, user, 1);
        for (int i = 0; i < numValues; i++) {
            client.insertChunk(new Chunk(i, new byte[1]), 1, user, i, i + 1, i, new LongNodeContent[]{
                    new LongNodeContent(val[i])
            });
        }

        for (int granularity : granularities) {
            List<NodeContent[]> res = client.getStatistics(user, 1, 0, numValues, granularity, new int[]{0});
            assertEquals(res.size(), numValues / granularity);


            for (int i = 0; i < res.size(); i++) {
                LongNodeContent cont = (LongNodeContent) res.get(i)[0];
                long expectedRes = 0;
                for (int iter = i * granularity; iter < (i + 1) * granularity; iter++) {
                    expectedRes += val[iter];
                }

                assertEquals(expectedRes, cont.i);
            }
        }
        client.deleteStream(user, 1);
        client.close();
    }

    /*
    @Test
    public void basicCryptoClient() throws IOException {
        String user = "User2";
        int num = 100;
        int granulatrity = 4;

        IRegressionFunction func = new FastAESRegressionFunction();
        TreeKeyRegression reg = new TreeKeyRegression(func, new byte[16], 20, 2, 0);
        CastelluciaRange cat = new CastelluciaRange(reg, 64, new SecureRandom());
        BasicCryptoCastClient client = new BasicCryptoCastClient("localhost", Server.DEFAULT_PORT, cat);

        MetaInformationConfig conf = new MetaInformationConfig(user, 1, new TimeCryptProtocol.StatisticsType[]{
                TimeCryptProtocol.StatisticsType.SUM
        });

        client.createStream(conf);
        for (int i = 0; i < num; i++) {
            client.insertChunk(new Chunk(i, new byte[1]), conf, i, new long[]{
                    1L
            });
        }
        List<TimeCryptProtocol.StatisticsType> types = new ArrayList<TimeCryptProtocol.StatisticsType>();
        types.add(TimeCryptProtocol.StatisticsType.SUM);
        List<long[]> res = client.getStatistics(user, 1, 0, num, granulatrity, types);
        assertEquals(res.size(), num / granulatrity);

        for (int i = 0; i < res.size(); i++) {
            long decRes = res.get(i)[0];
            assertEquals(decRes, granulatrity);
        }


        List<Chunk> chunks = client.getChunks(user, 1, 0, num);
        assertEquals(chunks.size(), num);

        //client.deleteStream(user, 1);

        //assertEquals(((DebugStorage) runnable.manager.getStorage()).keys.size(), 0);

        client.close();
    }*/

    /*
    @Test
    public void basicCryptoClientMulti() throws IOException {
        String user = "User2";
        int num = 100;
        int granulatrity = 4;

        IRegressionFunction func = new FastAESRegressionFunction();
        TreeKeyRegression reg = new TreeKeyRegression(func, new byte[16], 20, 2, 0);
        CastelluciaRange cat = new CastelluciaRange(reg, 64, new SecureRandom());
        BasicCryptoCastClient client = new BasicCryptoCastClient("localhost", Server.DEFAULT_PORT, cat);

        MetaInformationConfig conf1 = new MetaInformationConfig(user, 1, new TimeCryptProtocol.StatisticsType[]{
                TimeCryptProtocol.StatisticsType.SUM
        });

        MetaInformationConfig conf2 = new MetaInformationConfig(user, 2, new TimeCryptProtocol.StatisticsType[]{
                TimeCryptProtocol.StatisticsType.SUM
        });

        client.createStream(conf1);
        client.createStream(conf2);
        for (int i = 0; i < num; i++) {
            client.insertChunk(new Chunk(i, new byte[1]), conf1, i, new long[]{
                    1L
            });
            client.insertChunk(new Chunk(i, new byte[1]), conf2, i, new long[]{
                    1L
            });
        }
        List<TimeCryptProtocol.StatisticsType> types = new ArrayList<TimeCryptProtocol.StatisticsType>();
        types.add(TimeCryptProtocol.StatisticsType.SUM);
        List<long[]> res = client.getStatisticsMulti(user, 1, 2, 0, num, granulatrity, types);
        assertEquals(res.size(), num / granulatrity);

        for (int i = 0; i < res.size(); i++) {
            long decRes = res.get(i)[0];
            assertEquals(decRes, (long) granulatrity * 2);
        }


        List<Chunk> chunks = client.getChunks(user, 1, 0, num);
        assertEquals(chunks.size(), num);

        //client.deleteStream(user, 1);

        //assertEquals(((DebugStorage) runnable.manager.getStorage()).keys.size(), 0);

        client.close();
    }*/

    /*
    @Test
    public void metaConfig() throws IOException {
        String user = "User1";
        long uid = 2;
        ch.ethz.dsg.timecrypt.BasicClient client = new ch.ethz.dsg.timecrypt.BasicClient("localhost", Server.DEFAULT_PORT);
        MetaInformationConfig conf = new MetaInformationConfig(user, uid, new TimeCryptProtocol.StatisticsType[]{
                TimeCryptProtocol.StatisticsType.SUM,
                TimeCryptProtocol.StatisticsType.AVG,
                TimeCryptProtocol.StatisticsType.COUNT
        });

        client.createStream(conf);

        ITreeMetaInfo after = client.getMetaconfigurationForStream(user, uid);

        assertEquals(conf.getNumTypes(), after.getNumTypes());

        for (int i = 0; i < conf.getNumTypes(); i++) {
            assertEquals(conf.getStatisticsTypeForID(i), after.getStatisticsTypeForID(i));
        }

        client.close();
    }*/

    private static class QueryTask implements Runnable {

        public boolean success = true;
        private CassandraBlockTreeManager man;
        private int iter;

        public QueryTask(CassandraBlockTreeManager man, int iter) {
            this.man = man;
            this.iter = iter;
        }

        @Override
        public void run() {
            BlockTree tree;
            try {
                tree = man.fetchNewestTreeAndAwait(1, "1");


                for (int i = 0; i < iter; i++) {
                    for (int j = i + 1; j < iter; j++) {
                        boolean tmp = String.valueOf(j - i).equals(tree.getAggregation(i, j)[0].getStringRepresentation());
                        if (!tmp)
                            success = tmp;
//                        System.out.format("TID: %d From %d To %d ok\n", Thread.currentThread().getId(), i, j);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                success = false;
            }
        }
    }
}
