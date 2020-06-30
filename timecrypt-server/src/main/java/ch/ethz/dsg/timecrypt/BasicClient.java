/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt;

import ch.ethz.dsg.timecrypt.crypto.CryptoContentFactory;
import ch.ethz.dsg.timecrypt.index.Chunk;
import ch.ethz.dsg.timecrypt.index.blockindex.node.NodeContent;
import ch.ethz.dsg.timecrypt.protocol.TimeCryptNettyProtocol.*;
import com.google.protobuf.ByteString;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class BasicClient implements Closeable, AutoCloseable {

    private Socket net;
    private OutputStream outStream;
    private InputStream inStream;

    private byte[] buffer = new byte[1024 * 8];

    public BasicClient(String ip, int port) throws IOException {
        this.net = new Socket(ip, port);
        this.net.setTcpNoDelay(true);
        outStream = this.net.getOutputStream();
        inStream = this.net.getInputStream();
    }


    static void writeRawVarint32(OutputStream out, int value) throws IOException {
        while ((value & -128) != 0) {
            out.write(value & 127 | 128);
            value >>>= 7;
        }

        out.write(value);
    }

    private static int readRawVarint32(InputStream buffer) throws IOException {
        if (false) {
            return 0;
        } else {
            byte tmp = (byte) buffer.read();
            if (tmp >= 0) {
                return tmp;
            } else {
                int result = tmp & 127;
                if (false) {
                    return 0;
                } else {
                    if ((tmp = (byte) buffer.read()) >= 0) {
                        result |= tmp << 7;
                    } else {
                        result |= (tmp & 127) << 7;

                        if ((tmp = (byte) buffer.read()) >= 0) {
                            result |= tmp << 14;
                        } else {
                            result |= (tmp & 127) << 14;

                            if ((tmp = (byte) buffer.read()) >= 0) {
                                result |= tmp << 21;
                            } else {
                                result |= (tmp & 127) << 21;

                                result |= (tmp = (byte) buffer.read()) << 28;
                                if (tmp < 0) {
                                    throw new RuntimeException("ERROR on Decode");
                                }
                            }
                        }
                    }

                    return result;
                }
            }
        }
    }

    private void writeRequest(RequestMessage requestMessage) throws IOException {
        writeRawVarint32(outStream, requestMessage.getSerializedSize());
        requestMessage.writeTo(outStream);
        outStream.flush();
    }

    private ResponseMessage loadResponse() throws IOException {
        int len = readRawVarint32(inStream);
        int curOffset = 0;
        if (buffer.length < len)
            buffer = new byte[len];
        do {
            curOffset = inStream.read(buffer, curOffset, len);
        } while (curOffset < len);

        return ResponseMessage.parser().parseFrom(buffer, 0, len);
    }


    public boolean createStream(long uid, String owner, int numDigests) throws IOException {

        CreateStream.Builder cMsgBuilder = CreateStream.newBuilder()
                .setUid(uid)
                .setOwner(owner)
                .setMetadataConfig(MetaConfig.newBuilder().setNumdigests(numDigests).build());

        writeRequest(RequestMessage.newBuilder()
                .setType(MessageRequestType.CREATE_STREAM)
                .setCreateStream(cMsgBuilder)
                .build());

        ResponseMessage msg = loadResponse();

        if (msg.hasSuccessResponse())
            return true;
        else
            return false;
    }

    public boolean deleteStream(String owner, long uid) throws IOException {
        DeleteStream.Builder dMsgBuilder = DeleteStream.newBuilder()
                .setUid(uid)
                .setOwner(owner);

        writeRequest(RequestMessage.newBuilder()
                .setType(MessageRequestType.DELETE_STREAM)
                .setDeleteStream(dMsgBuilder)
                .build());

        ResponseMessage msg = loadResponse();

        if (msg.hasSuccessResponse())
            return true;
        else
            return false;
    }

    private static NodeContent[] parseMetadata(StatisticsResponse response) {
        NodeContent[] result = new NodeContent[response.getDataCount()];
        int iter = 0;
        for (Metadata meta : response.getDataList()) {
            result[iter] = CryptoContentFactory.decodeNodeContent(meta.getData().toByteArray());
            iter++;
        }
        return result;
    }

    public List<NodeContent[]> getStatistics(String owner, long uid, long from, long to,
                                             long granularity, int[] types) throws IOException {

        writeRequest(RequestMessage.newBuilder().setType(MessageRequestType.GET_STATISTICS)
                .setGetStatistics(GetStatistics.newBuilder()
                        .setOwner(owner)
                        .setUid(uid).setFrom(from)
                        .setTo(to)
                        .setGranularity(granularity)
                        .addAllDigestid(Arrays.stream(types).boxed().collect(Collectors.toList()))).build());
        int numMsgs = (int) ((to - from) / granularity);
        int cur = 0;
        boolean hasError = false;
        List<NodeContent[]> result = new ArrayList<NodeContent[]>();
        do {
            ResponseMessage msg = loadResponse();
            switch (msg.getType()) {
                case STATISTICS_RESPONSE:
                    result.add(parseMetadata(msg.getStatisticsResponse()));
                    cur++;
                    break;
                case ERROR_RESPONSE:
                    result.add(new NodeContent[0]);
                    hasError = true;
                    cur++;
                    break;
                case MULTIRESPONSE:
                    assert (numMsgs == msg.getMultiTransfer().getNumTransfers());
                    numMsgs = msg.getMultiTransfer().getNumTransfers();
                    break;

                default:
                    hasError = true;
            }
        } while (cur < numMsgs);
        if (hasError)
            throw new RuntimeException("Error Occured");
        return result;
    }

    public List<NodeContent[]> getStatisticsMulti(String owner, long uidFrom, long uidTo, long from, long to,
                                                  long granularity, int[] types) throws IOException {

        writeRequest(RequestMessage.newBuilder().setType(MessageRequestType.GET_MULTI)
                .setGetStatisticsMulti(GetStatisticsMulti.newBuilder()
                        .setOwner(owner)
                        .setUidFrom(uidFrom)
                        .setUidTo(uidTo)
                        .setFrom(from)
                        .setTo(to)
                        .setGranularity(granularity)
                        .addAllDigestid(Arrays.stream(types).boxed().collect(Collectors.toList()))).build());
        int numMsgs = (int) ((to - from) / granularity);
        int cur = 0;
        boolean hasError = false;
        List<NodeContent[]> result = new ArrayList<NodeContent[]>();
        do {
            ResponseMessage msg = loadResponse();
            switch (msg.getType()) {
                case STATISTICS_RESPONSE:
                    result.add(parseMetadata(msg.getStatisticsResponse()));
                    cur++;
                    break;
                case ERROR_RESPONSE:
                    result.add(new NodeContent[0]);
                    hasError = true;
                    cur++;
                    break;
                case MULTIRESPONSE:
                    assert (numMsgs == msg.getMultiTransfer().getNumTransfers());
                    numMsgs = msg.getMultiTransfer().getNumTransfers();
                    break;

                default:
                    hasError = true;
            }
        } while (cur < numMsgs);
        if (hasError)
            throw new RuntimeException("Error Occured");
        return result;
    }

    public List<Chunk> getChunks(String owner, long uid, long from, long to) throws IOException {
        writeRequest(RequestMessage.newBuilder().setType(MessageRequestType.GET_CHUNKS)
                .setGetChunks(GetChunks.newBuilder()
                        .setOwner(owner)
                        .setUid(uid)
                        .setFrom(from)
                        .setTo(to)).build());

        outStream.flush();
        int numMsgs = (int) ((to - from));
        int cur = 0;
        boolean hasError = false;
        List<Chunk> result = new ArrayList<Chunk>();
        do {
            ResponseMessage msg = loadResponse();
            switch (msg.getType()) {
                case DATA_RESPONSE:
                    DataResponse resp = msg.getDataResponse();
                    result.add(new Chunk(resp.getKey(), resp.getData().toByteArray()));
                    cur++;
                    break;
                case ERROR_RESPONSE:
                    result.add(new Chunk(0, null));
                    hasError = true;
                    cur++;
                    break;
                case MULTIRESPONSE:
                    assert (numMsgs == msg.getMultiTransfer().getNumTransfers());
                    numMsgs = msg.getMultiTransfer().getNumTransfers();
                    break;

                default:
                    hasError = true;
            }
        } while (cur < numMsgs);
        if (hasError)
            throw new RuntimeException("Error Occured");
        return result;
    }

    public boolean insertChunk(Chunk chunk, long uid, String user, long from, long to, int id, NodeContent[] metadata) throws IOException {
        InsertChunk.Builder iMsgBuilder = InsertChunk.newBuilder()
                .setUid(uid).setOwner(user)
                .setFrom(from)
                .setTo(to)
                .setKey(id)
                .setChunk(ByteString.copyFrom(chunk.getData()));

        for (int iter = 0; iter < metadata.length; iter++) {
            iMsgBuilder.addMetadata(Metadata.newBuilder()
                    .setDigestid(iter)
                    .setData(ByteString.copyFrom(metadata[iter].encode())));
        }


        writeRequest(RequestMessage.newBuilder()
                .setType(MessageRequestType.INSERT_CHUNK)
                .setInsertChunk(iMsgBuilder)
                .build());

        ResponseMessage msg = loadResponse();
        if (msg.hasSuccessResponse())
            return true;
        else
            return false;
    }

    public void close() throws IOException {
        net.close();
    }
}