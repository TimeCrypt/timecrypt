/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.server.grpc;


import ch.ethz.dsg.timecrypt.crypto.BigintMacNodeContent;
import ch.ethz.dsg.timecrypt.crypto.BigintNodeContent;
import ch.ethz.dsg.timecrypt.crypto.LongMacNodeNodeContent;
import ch.ethz.dsg.timecrypt.crypto.LongNodeContent;
import ch.ethz.dsg.timecrypt.exceptions.TimeCryptStorageException;
import ch.ethz.dsg.timecrypt.exceptions.TimeCryptTreeAlreadyExistsException;
import ch.ethz.dsg.timecrypt.exceptions.TimeCryptTreeException;
import ch.ethz.dsg.timecrypt.index.Chunk;
import ch.ethz.dsg.timecrypt.index.IStorage;
import ch.ethz.dsg.timecrypt.index.ITreeManager;
import ch.ethz.dsg.timecrypt.index.UserStreamTree;
import ch.ethz.dsg.timecrypt.index.blockindex.node.NodeContent;
import ch.ethz.dsg.timecrypt.protocol.*;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class TimeCryptGRPCServer extends TimecryptGrpc.TimecryptImplBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimeCryptGRPCServer.class);
    private final Random rand = new Random();
    private final ITreeManager treeManager;
    private final IStorage storage;

    public TimeCryptGRPCServer(ITreeManager treeManager, IStorage storage) {
        this.treeManager = treeManager;
        this.storage = storage;
    }

    @Override
    public void createStream(streamMessage request, StreamObserver<streamId> responseObserver) {

        List<metadataConfig> requestMetadataConfigList = request.getMetadataConfigList();
        metadataConfig[] validationArray = new metadataConfig[requestMetadataConfigList.size()];

        // Basic validation - also orders the meta data for future processing
        for (metadataConfig metaData : requestMetadataConfigList) {
            if (metaData.getId() >= requestMetadataConfigList.size()) {
                responseObserver.onError(Status.INVALID_ARGUMENT
                        .withDescription("Invalid ID for metadata item " + metaData)
                        .asRuntimeException());
                return;
            }
            if (metaData.getId() < 0) {
                responseObserver.onError(Status.INVALID_ARGUMENT
                        .withDescription("Invalid ID for metadata item " + metaData)
                        .asRuntimeException());
                return;
            }
            if (validationArray[metaData.getId()] == null) {
                validationArray[metaData.getId()] = metaData;
            } else {
                responseObserver.onError(Status.INVALID_ARGUMENT
                        .withDescription("Duplicate ID for metadata item " + metaData + " item with same ID:"
                                + validationArray[metaData.getId()])
                        .asRuntimeException());
                return;
            }
        }

        String owner = GrpcAuthConstants.USER_INFO_KEY.get();

        // TODO: Do something with the stream meta data .. Arrays.asList(validationArray)

        // TODO: This should come from the Database... trying 3 times should be okay for now ...
        long streamId = rand.nextLong();

        try {
            treeManager.createTree(streamId, owner);
        } catch (TimeCryptTreeAlreadyExistsException e) {
            streamId = rand.nextLong();
            try {
                treeManager.createTree(streamId, owner);
            } catch (TimeCryptTreeAlreadyExistsException e1) {
                treeManager.createTree(streamId, owner);
            }
        } catch (TimeCryptTreeException e2) {
            LOGGER.error("Error crating stream", e2);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Error creating stream")
                    .withCause(e2)
                    .asRuntimeException());
            return;
        }

        responseObserver.onNext(ch.ethz.dsg.timecrypt.protocol.streamId.newBuilder().setStreamId(streamId).build());
        responseObserver.onCompleted();
        LOGGER.info("Created stream with id " + streamId + " for user: " + owner);
    }

    @Override
    public void deleteStream(streamId request, StreamObserver<Empty> responseObserver) {
        String owner = GrpcAuthConstants.USER_INFO_KEY.get();
        long streamId = request.getStreamId();

        // Check tree existence
        try {
            //TODO: This should really not be int ...
            treeManager.getTreeForUser(streamId, owner, 0);
        } catch (TimeCryptTreeException e) {
            String msg = "Could not get stream tree with id " + streamId + " for owner: " + owner;
            LOGGER.error(msg);
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription(msg)
                    .withCause(e)
                    .asRuntimeException());
            return;
        }

        try {
            treeManager.deleteTree(streamId, owner);
        } catch (TimeCryptTreeException e) {
            String msg = "Could not delete stream with id " + streamId + " for owner: " + owner;
            LOGGER.error(msg, e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription(msg)
                    .withCause(e)
                    .asRuntimeException());
            return;
        }

        try {
            storage.deleteALL(streamId, owner);
        } catch (TimeCryptStorageException e) {
            String msg = "Could not delete stream with id " + streamId + " for owner: " + owner;
            LOGGER.error(msg, e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription(msg)
                    .withCause(e)
                    .asRuntimeException());
            return;
        }

        responseObserver.onNext(Empty.newBuilder().build());
        responseObserver.onCompleted();
        LOGGER.info("Deleted stream with id " + streamId + " for user: " + owner);
    }

    @Override
    public void insertChunk(chunkCreationMessage request, StreamObserver<chunkId> responseObserver) {
        String owner = GrpcAuthConstants.USER_INFO_KEY.get();
        long streamId = request.getChunk().getStreamId().getStreamId();
        long chunkId = request.getChunk().getChunkId().getId();

        //TODO: This should really not be int ...
        Chunk chunk = new Chunk((int) chunkId, request.getChunk().getChunkContent().toByteArray());
        List<metadataContent> requestMetadataList = request.getDigest().getMetadataContentList();
        NodeContent[] metadata = new NodeContent[requestMetadataList.size()];

        // TODO: The provided meta data should match the meta data of the
        for (metadataContent content : requestMetadataList) {
            metadataConfig config = content.getConfig();
            NodeContent node;

            switch (content.getPayloadCase().getNumber()) {
                case metadataContent.LONG_PAYLOAD_FIELD_NUMBER:
                    node = new LongNodeContent(content.getLongPayload().getEncryptedLong());
                    break;
                case metadataContent.LONG_MAC_PAYLOAD_FIELD_NUMBER:
                    node = new LongMacNodeNodeContent(content.getLongMacPayload().getEncryptedLong(),
                            new BigInteger(content.getLongMacPayload().getAuthCode().toByteArray()));
                    break;
                case metadataContent.BIG_INT_PAYLOAD_FIELD_NUMBER:
                    node = new BigintNodeContent
                            (new BigInteger(content.getBigIntPayload().getEncryptedBigInt().toByteArray()));
                    break;
                case metadataContent.BIG_INT_MAC_PAYLOAD_FIELD_NUMBER:
                    node = new BigintMacNodeContent
                            (new BigInteger(content.getBigIntMacPayload().getEncryptedBigInt().toByteArray()),
                                    new BigInteger(content.getBigIntMacPayload().getAuthCode().toByteArray()));
                    break;
                default:
                    String msg = "Unknown metadata content payload type " + content.getPayloadCase().getNumber();
                    LOGGER.error(msg);
                    responseObserver.onError(Status.INTERNAL
                            .withDescription(msg)
                            .asRuntimeException());
                    return;
            }
            metadata[config.getId()] = node;
        }

        UserStreamTree userTree;
        try {
            //TODO: This should really not be int ...
            userTree = treeManager.getTreeForUser(streamId, owner, (int) chunkId);
        } catch (TimeCryptTreeException e) {
            String msg = "Could not get stream tree with id " + streamId + " for owner: " + owner + " at chunk id "
                    + chunkId;
            LOGGER.error(msg);
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription(msg)
                    .withCause(e)
                    .asRuntimeException());
            return;
        }

        try {
            storage.putChunk(streamId, owner, chunk);
        } catch (TimeCryptStorageException e) {
            String msg = "Could not put chunk to  stream with id " + streamId + " for owner: " + owner +
                    " at chunk id " + chunkId;
            LOGGER.error(msg, e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription(msg)
                    .withCause(e)
                    .asRuntimeException());
            return;
        }

        try {
            //TODO: This should really not be int ...
            userTree.getTree().insert((int) chunkId, metadata, chunkId, chunkId + 1);
        } catch (Exception e) {
            String msg = "Could not insert digest to stream with id " + streamId + " for owner: " + owner +
                    " at chunk id " + chunkId;
            LOGGER.error(msg, e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription(msg)
                    .withCause(e)
                    .asRuntimeException());
            return;
        }

        responseObserver.onNext(ch.ethz.dsg.timecrypt.protocol.chunkId.newBuilder().setId(chunkId).build());
        responseObserver.onCompleted();
        LOGGER.info("Inserted chunk with id " + chunkId + " to stream with id " + streamId + " for owner: " + owner);
    }

    @Override
    public void getLastWrittenChunk(streamId request, StreamObserver<chunkId> responseObserver) {
        String owner = GrpcAuthConstants.USER_INFO_KEY.get();
        long streamId = request.getStreamId();

        UserStreamTree userTree = treeManager.getTreeForUser(streamId, owner);
        long returnChunkId = userTree.getTree().getLastWrittenChunk();

        responseObserver.onNext(chunkId.newBuilder().setId(returnChunkId).build());
        responseObserver.onCompleted();
    }

    @Override
    public void getRawData(chunkRequestMessage request, StreamObserver<chunk> responseObserver) {
        String owner = GrpcAuthConstants.USER_INFO_KEY.get();
        long streamId = request.getStreamId().getStreamId();

        long chunkIdFrom = request.getStart().getId();
        long chunkIdTo = request.getEnd().getId();

        if (chunkIdFrom >= chunkIdTo) {
            String msg = "Invalid query range - FROM (" + chunkIdFrom + ") >= TO (" + chunkIdTo + ")";
            LOGGER.warn(msg);
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(msg)
                    .asRuntimeException());
            return;
        }

        if (chunkIdFrom < 0) {
            String msg = "Invalid query range - FROM (" + chunkIdFrom + ") < 0 ";
            LOGGER.warn(msg);
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(msg)
                    .asRuntimeException());
            return;
        }

        // Check tree existence
        try {
            //TODO: This should really not be int ...
            treeManager.getTreeForUser(streamId, owner, (int) chunkIdTo);
        } catch (TimeCryptTreeException e) {
            String msg = "Could not get stream tree with id " + streamId + " for owner: " + owner;
            LOGGER.error(msg);
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription(msg)
                    .withCause(e)
                    .asRuntimeException());
            return;
        }

        List<Long> getKeys = new ArrayList<>();
        for (long i = chunkIdFrom; i < chunkIdTo; i++) {
            getKeys.add(i);
        }

        for (long key : getKeys) {
            try {
                //TODO: This should really not be int ...
                Chunk curChunk = storage.getChunk(streamId, owner, (int) key);
                if (curChunk == null) {
                    String msg = "Could not find any chunks data for the given request {streamId " + streamId +
                            " owner " + owner + " from " + chunkIdFrom + " to " + chunkIdTo + "}";
                    LOGGER.warn(msg);
                    responseObserver.onError(Status.INVALID_ARGUMENT
                            .withDescription(msg)
                            .asRuntimeException());
                }
                responseObserver.onNext(chunk.newBuilder()
                        .setChunkId(chunkId.newBuilder().setId(key).build())
                        .setStreamId(ch.ethz.dsg.timecrypt.protocol.streamId.newBuilder().setStreamId(streamId).build())
                        .setChunkContent(ByteString.copyFrom(curChunk.getData()))
                        .build());
            } catch (TimeCryptStorageException e) {
                String msg = "Exception caught - while processing chunks of get chunk request {uid "
                        + streamId + " owner " + owner + " from " + chunkIdFrom + " to " + chunkIdTo + "}";
                LOGGER.error(msg, e);
                responseObserver.onError(Status.INTERNAL
                        .withDescription(msg)
                        .withCause(e)
                        .asRuntimeException());
                return;
            }
        }
        responseObserver.onCompleted();
        LOGGER.info("finished sending chunks of get chunk request {uid " + streamId + " owner " + owner +
                " from " + chunkIdFrom + " to " + chunkIdTo + "}");
    }

    @Override
    public void getStatisticalData(statisticRequestMessage request, StreamObserver<digest> responseObserver) {

        // TODO: There is no validation based on the meta data of the stream - cant request metadata that are not there

        String owner = GrpcAuthConstants.USER_INFO_KEY.get();
        long streamId = request.getStreamId().getStreamId();

        UserStreamTree userTree = treeManager.getTreeForUser(streamId, owner);
        long maxChunkId = userTree.getTree().getLastWrittenChunk();

        long chunkIdFrom = request.getStart().getId();
        long chunkIdTo = request.getEnd().getId();
        long granularity = request.getGranularity();

        if (maxChunkId < 0) {
            String msg = "Invalid query - stream has no data yet";
            LOGGER.warn(msg);
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(msg)
                    .asRuntimeException());
            return;
        }

        if (chunkIdFrom > maxChunkId) {
            String msg = "Invalid query range - FROM (" + chunkIdFrom + ") > maximum chunk ID (" + maxChunkId + ")";
            LOGGER.warn(msg);
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(msg)
                    .asRuntimeException());
            return;
        }

        // Chunk ID TO is exclusive
        if ((chunkIdTo - 1) > maxChunkId) {
            String msg = "Invalid query range - TO (" + chunkIdTo + ") > maximum chunk ID (" + maxChunkId + ")";
            LOGGER.warn(msg);
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(msg)
                    .asRuntimeException());
            return;
        }

        if (chunkIdFrom >= chunkIdTo) {
            String msg = "Invalid query range - FROM (" + chunkIdFrom + ") >= TO (" + chunkIdTo + ")";
            LOGGER.warn(msg);
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(msg)
                    .asRuntimeException());
            return;
        }

        if (chunkIdFrom < 0) {
            String msg = "Invalid query range - FROM (" + chunkIdFrom + ") < 0 ";
            LOGGER.warn(msg);
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(msg)
                    .asRuntimeException());
            return;
        }

        if (granularity < 1) {
            String msg = "Invalid granularity " + granularity;
            LOGGER.warn(msg);
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(msg)
                    .asRuntimeException());
            return;
        }

        if ((chunkIdTo - chunkIdFrom) % (float) granularity != 0) {
            String msg = "Invalid query granularity - the requested length of interval " +
                    (chunkIdTo - chunkIdFrom) + " (TO = " + chunkIdTo + ", FROM = " + chunkIdFrom +
                    ") can not be divided by requested granularity " + granularity;
            LOGGER.warn(msg);
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(msg)
                    .asRuntimeException());
            return;
        }

        List<metadataConfig> requestMetadataConfig = request.getMetadataConfigList();

        if (requestMetadataConfig.size() < 1) {
            String msg = "No meta data requested " + granularity;
            LOGGER.warn(msg);
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(msg)
                    .asRuntimeException());
            return;
        }

        int[] ids = new int[requestMetadataConfig.size()];

        for (int i = 0; i < requestMetadataConfig.size(); i++) {
            int id = requestMetadataConfig.get(i).getId();

            if (id < 0) {
                String msg = "Invalid ID " + id;
                LOGGER.warn(msg);
                responseObserver.onError(Status.INVALID_ARGUMENT
                        .withDescription(msg)
                        .asRuntimeException());
                return;
            }
            ids[i] = id;
        }

        long fromIter = chunkIdFrom;
        long toIter = chunkIdFrom + granularity;
        userTree = treeManager.getTreeForUser(streamId, owner, (int) chunkIdTo);

        NodeContent[] content;
        while (toIter <= chunkIdTo) {
            try {
                content = userTree.getTree().getAggregation(fromIter, toIter, ids);
                if (content == null) {
                    String msg = "Could not find any statistics data for the given request {uid " + streamId +
                            " owner " + owner + " from " + chunkIdFrom + " to " + chunkIdTo + " granularity " +
                            granularity + " ids " + Arrays.toString(ids) + " }";
                    LOGGER.warn(msg);
                    responseObserver.onError(Status.INTERNAL
                            .withDescription(msg)
                            .asRuntimeException());
                    return;
                }

                digest.Builder returnDigestBuilder = digest.newBuilder();
                returnDigestBuilder.setStart(chunkId.newBuilder().setId(fromIter).build());
                returnDigestBuilder.setEnd(chunkId.newBuilder().setId(toIter).build());

                for (int id : ids) {
                    NodeContent curNode = content[id];

                    metadataContent.Builder metadataContentBuilder = metadataContent.newBuilder();
                    metadataConfig.Builder metadataConfigBuilder = metadataConfig.newBuilder();

                    if (curNode instanceof LongMacNodeNodeContent) {
                        LongMacNodeNodeContent nodeContent = (LongMacNodeNodeContent) curNode;
                        metadataContentBuilder.setConfig(metadataConfigBuilder.setId(id)
                                .setSchemaValue(EncryptionSchema.LONG_MAC_VALUE).build())
                                .setLongMacPayload(longMacPayload.newBuilder()
                                        .setEncryptedLong(nodeContent.getLong())
                                        .setAuthCode(ByteString.copyFrom(nodeContent.getMac().toByteArray()))
                                        .setAuthCodeBits(nodeContent.getMac().bitLength())
                                        .build());
                    } else if (curNode instanceof LongNodeContent) {
                        LongNodeContent nodeContent = (LongNodeContent) curNode;
                        metadataContentBuilder.setConfig(metadataConfigBuilder.setId(id)
                                .setSchemaValue(EncryptionSchema.LONG_VALUE).build())
                                .setLongPayload(longPayload.newBuilder()
                                        .setEncryptedLong(nodeContent.getLong())
                                        .build());
                    } else if (curNode instanceof BigintMacNodeContent) {
                        BigintMacNodeContent nodeContent = (BigintMacNodeContent) curNode;
                        metadataContentBuilder.setConfig(metadataConfigBuilder.setId(id)
                                .setSchemaValue(EncryptionSchema.BIG_INT_MAC_VALUE).build())
                                .setBigIntMacPayload(bigIntMacPayload.newBuilder()
                                        .setEncryptedBigInt(ByteString.copyFrom(nodeContent.getContent().toByteArray()))
                                        .setAuthCode(ByteString.copyFrom(nodeContent.getMac().toByteArray()))
                                        .setAuthCodeBits(nodeContent.getMac().bitLength())
                                        .build());
                    } else if (curNode instanceof BigintNodeContent) {
                        BigintNodeContent nodeContent = (BigintNodeContent) curNode;
                        metadataContentBuilder.setConfig(metadataConfigBuilder.setId(id)
                                .setSchemaValue(EncryptionSchema.BIG_INT_VALUE).build())
                                .setBigIntPayload(bigIntPayload.newBuilder()
                                        .setEncryptedBigInt(ByteString.copyFrom(nodeContent.getContent().toByteArray()))
                                        .build());
                    } else {
                        String msg = "Unknown node type found! " + curNode;
                        LOGGER.error(msg);
                        responseObserver.onError(Status.INTERNAL
                                .withDescription(msg)
                                .asRuntimeException());
                    }
                    returnDigestBuilder.addMetadataContent(metadataContentBuilder.build());
                }
                responseObserver.onNext(returnDigestBuilder.build());

            } catch (Exception e) {
                String msg = "Exception caught - while processing node content of statistic request {uid " + streamId +
                        " owner " + owner + " from " + chunkIdFrom + " to " + chunkIdTo + " granularity " +
                        granularity + " ids " + Arrays.toString(ids) + " }";
                LOGGER.error(msg, e);
                responseObserver.onError(Status.INTERNAL
                        .withDescription(msg)
                        .withCause(e)
                        .asRuntimeException());
                return;
            }

            toIter += granularity;
            fromIter += granularity;

        }
        responseObserver.onCompleted();
        LOGGER.info("finished sending statistic data of get statistic request {uid " + streamId + " owner " + owner +
                " from " + chunkIdFrom + " to " + chunkIdTo + " granularity " + granularity + "}");
    }
}
