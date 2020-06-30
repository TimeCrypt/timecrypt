/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.serverInterface.nettyServer;

import ch.ethz.dsg.timecrypt.client.exceptions.CouldNotReceiveException;
import ch.ethz.dsg.timecrypt.client.exceptions.InvalidQueryException;
import ch.ethz.dsg.timecrypt.client.serverInterface.EncryptedChunk;
import ch.ethz.dsg.timecrypt.client.serverInterface.EncryptedDigest;
import ch.ethz.dsg.timecrypt.client.serverInterface.EncryptedMetadata;
import ch.ethz.dsg.timecrypt.client.serverInterface.nettyServer.TimeCryptNettyProtocol.*;
import ch.ethz.dsg.timecrypt.client.streamHandling.metaData.StreamMetaData;
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

public class NettyClient implements Closeable, AutoCloseable {

    private Socket net;
    private OutputStream outStream;
    private InputStream inStream;

    private byte[] buffer = new byte[1024 * 8];

    public NettyClient(String ip, int port) throws IOException {
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

    private static EncryptedDigest parseMetadata(List<StreamMetaData> info, long streamId, long chunkIdFrom, long chunkIdTo, StatisticsResponse response) {
        List<EncryptedMetadata> result = new ArrayList<>(response.getDataCount());
        int id = 0;
        for (Metadata meta : response.getDataList()) {
            StreamMetaData metaInfo = info.get(id++);
            result.add(NodeContentSerialization.decodeNodeContent(meta.getData().toByteArray(), metaInfo.getId(), metaInfo.getEncryptionScheme()));
        }
        return new EncryptedDigest(streamId, chunkIdFrom, chunkIdTo, result);
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

    public boolean createStream(long uid, String owner, int numDigest) throws IOException {

        CreateStream.Builder cMsgBuilder = CreateStream.newBuilder()
                .setUid(uid)
                .setOwner(owner)
                .setMetadataConfig(MetaConfig.newBuilder().setNumdigests(numDigest).build());

        writeRequest(RequestMessage.newBuilder()
                .setType(MessageRequestType.CREATE_STREAM)
                .setCreateStream(cMsgBuilder)
                .build());

        ResponseMessage msg = loadResponse();

        return msg.hasSuccessResponse();
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

        return msg.hasSuccessResponse();
    }

    public List<EncryptedDigest> getStatistics(Long streamId, String owner, long from, long to,
                                               long granularity, List<StreamMetaData> metaData) throws IOException, InvalidQueryException {

        int[] types = new int[metaData.size()];
        for (int i = 0; i < metaData.size(); i++) {
            types[i] = metaData.get(i).getId();
        }
        writeRequest(RequestMessage.newBuilder().setType(MessageRequestType.GET_STATISTICS)
                .setGetStatistics(GetStatistics.newBuilder()
                        .setOwner(owner)
                        .setUid(streamId).setFrom(from)
                        .setTo(to)
                        .setGranularity(granularity)
                        .addAllDigestid(Arrays.stream(types).boxed().collect(Collectors.toList()))).build());
        int numMsgs = (int) ((to - from) / granularity);
        int cur = 0;
        boolean hasError = false;
        List<EncryptedDigest> result = new ArrayList<>();
        String errMsg = "";
        long fromCur = from;
        long toCur = from + granularity;
        do {
            ResponseMessage msg = loadResponse();
            switch (msg.getType()) {
                case STATISTICS_RESPONSE:
                    result.add(parseMetadata(metaData, streamId, fromCur, toCur, msg.getStatisticsResponse()));
                    cur++;
                    fromCur += granularity;
                    toCur = from + granularity;
                    break;
                case ERROR_RESPONSE:
                    errMsg = msg.getErrorResponse().getMessage();
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
        if (hasError) {
            throw new InvalidQueryException("Error message from server: '" + errMsg + "'");
        }
        return result;
    }

    public List<EncryptedDigest> getStatisticsMulti(String owner, long uidFrom, long uidTo, long from, long to,
                                                    long granularity, List<StreamMetaData> metaData) throws IOException {
        // THIS IS NOT READY TO BE USED
        int[] types = new int[metaData.size()];
        for (int i = 0; i < metaData.size(); i++) {
            types[i] = metaData.get(i).getId();
        }
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
        List<EncryptedDigest> result = new ArrayList<>();
        do {
            ResponseMessage msg = loadResponse();
            switch (msg.getType()) {
                case STATISTICS_RESPONSE:
                    result.add(parseMetadata(metaData, uidFrom, from, to, msg.getStatisticsResponse()));
                    cur++;
                    break;
                case ERROR_RESPONSE:
                    result.add(null);
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
        if (hasError) {
            throw new RuntimeException("Error Occured");
        }
        return result;
    }

    public List<EncryptedChunk> getChunks(String owner, long uid, long from, long to) throws IOException,
            CouldNotReceiveException {
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
        String errMsg = "";
        List<EncryptedChunk> result = new ArrayList<>();
        do {
            ResponseMessage msg = loadResponse();
            switch (msg.getType()) {
                case DATA_RESPONSE:
                    DataResponse resp = msg.getDataResponse();
                    result.add(new EncryptedChunk(uid, resp.getKey(), resp.getData().toByteArray()));
                    cur++;
                    break;
                case ERROR_RESPONSE:
                    result.add(new EncryptedChunk(uid, 0, null));
                    hasError = true;
                    errMsg = msg.getErrorResponse().getMessage();
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
        if (hasError) {
            throw new CouldNotReceiveException("Query failed " + errMsg);
        }
        return result;
    }

    public boolean insertChunk(EncryptedChunk chunk, long streamId, String owner, EncryptedDigest digest) throws IOException {
        InsertChunk.Builder iMsgBuilder = InsertChunk.newBuilder()
                .setUid(streamId).setOwner(owner)
                .setFrom(chunk.getChunkId())
                .setTo(chunk.getChunkId() + 1)
                .setKey((int) chunk.getChunkId())
                .setChunk(ByteString.copyFrom(chunk.getPayload()));

        for (EncryptedMetadata meta : digest.getPayload()) {
            iMsgBuilder.addMetadata(Metadata.newBuilder()
                    .setDigestid(meta.getMetadataId())
                    .setData(ByteString.copyFrom(NodeContentSerialization.encodeToNodeContent(meta))));
        }


        writeRequest(RequestMessage.newBuilder()
                .setType(MessageRequestType.INSERT_CHUNK)
                .setInsertChunk(iMsgBuilder)
                .build());

        ResponseMessage msg = loadResponse();
        return msg.hasSuccessResponse();
    }

    /*
    public ITreeMetaInfo getMetaconfigurationForStream(String owner, long uid) throws IOException {
        writeRequest(RequestMessage.newBuilder()
                .setType(MessageRequestType.GET_METAINFO)
                .setGetMetaConfig(GetMetaConfiguration.newBuilder()
                        .setUid(uid)
                        .setOwner(owner))
                .build());

        ResponseMessage msg = loadResponse();
        if (msg.hasMetConfigResponse()) {
            MetaConfigResponse resp = msg.getMetConfigResponse();
            List<MetaConfig> configs = resp.getMetaConfigList();
            return new MetaInformationConfig(owner, uid, configs);
        } else {
            throw new RuntimeException("Fetch failed");
        }
        return null;
    }*/

    public void close() throws IOException {
        net.close();
    }
}
