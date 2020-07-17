package ch.ethz.dsg.timecrypt.crypto.serialize;

import ch.ethz.dsg.timecrypt.crypto.keyRegression.SeedNode;
import ch.ethz.dsg.timecrypt.crypto.keyRegression.TreeKeyRegressionFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class KDNodesSerialization {

    private static final ByteOrder BYTE_ORDER = ByteOrder.BIG_ENDIAN;
    private static final int SEED_LEN = 16;
    private static final int NODE_ENCODE_LEN = 10 + SEED_LEN;


    private static void writeNodeLen(int nodeLen, ByteBuffer buff) {
        buff.put((byte) ((nodeLen >> 8) & 0xFF));
        buff.put((byte) (nodeLen & 0xFF));
    }

    private static int readNodeLen(ByteBuffer buff) {
        int res = ((int) buff.get()) << 8;
        res |= buff.get();
        return res;
    }

    // ENCODING:
    // | 16-bit node-len | 64-bit node-nr| 128-bit seed |

    public static void serializeNode(SeedNode node, byte[] buffer, int offset, int length) throws IOException {
        if (length < NODE_ENCODE_LEN)
            throw new IOException("Buffer is too small");
        ByteBuffer buff = ByteBuffer.wrap(buffer, offset, length).order(BYTE_ORDER);
        writeNodeLen(node.getDepth(), buff);
        buff.putLong(node.getNodeNr());
        buff.put(node.getSeed());
    }

    public static SeedNode decodeNode(byte[] buffer, int offset, int length) throws IOException {
        if (length < NODE_ENCODE_LEN)
            throw new IOException("Buffer is too small");
        ByteBuffer buff = ByteBuffer.wrap(buffer, offset, length).order(BYTE_ORDER);
        int len = readNodeLen(buff);
        long nodeNr = buff.getLong();
        byte[] seed = new byte[SEED_LEN];
        for (int i = 0; i < SEED_LEN; i++)
            seed[i] = buff.get();
        return TreeKeyRegressionFactory.getSeedNode(len, nodeNr, seed);
    }


}
