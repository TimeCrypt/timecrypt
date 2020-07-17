/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.serverInterface.grpcServer;

import ch.ethz.dsg.timecrypt.client.exceptions.CouldNotReceiveException;
import ch.ethz.dsg.timecrypt.client.exceptions.CouldNotStoreException;
import ch.ethz.dsg.timecrypt.client.exceptions.InvalidQueryException;
import ch.ethz.dsg.timecrypt.client.exceptions.UnsupportedOperationException;
import ch.ethz.dsg.timecrypt.client.serverInterface.EncryptedChunk;
import ch.ethz.dsg.timecrypt.client.serverInterface.EncryptedDigest;
import ch.ethz.dsg.timecrypt.client.serverInterface.EncryptedMetadata;
import ch.ethz.dsg.timecrypt.client.serverInterface.ServerInterface;
import ch.ethz.dsg.timecrypt.client.streamHandling.metaData.StreamMetaData;
import ch.ethz.dsg.timecrypt.protocol.*;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;

public class GrpcServerClient implements ServerInterface {

    // TODO: add real authentication
    public static final String DUMMY_OWNER = "NONE";
    /**
     * Key that retrieves the user from the metadata.
     */
    static final Metadata.Key<String> AUTH_USER_METADATA_KEY = Metadata.Key.of("User", ASCII_STRING_MARSHALLER);
    private static final Logger LOGGER = LoggerFactory.getLogger(GrpcServerClient.class);
    private TimecryptGrpc.TimecryptBlockingStub stub;


    public GrpcServerClient(String serverAddress, int serverPort) {

        // create a custom header
        Metadata header = new Metadata();
        header.put(AUTH_USER_METADATA_KEY, "user");

        // create client stub
        ManagedChannel channel = ManagedChannelBuilder.forAddress(serverAddress, serverPort)
                .usePlaintext()
                .build();
        stub = TimecryptGrpc.newBlockingStub(channel);
        stub = MetadataUtils.attachHeaders(stub, header);
    }

    @Override
    public long createStream(List<StreamMetaData> metadataConfigValue) throws CouldNotStoreException {
        streamId response;
        streamMessage.Builder streamMessageBuilder = streamMessage.newBuilder();
        streamMessageBuilder.setOwnerId(DUMMY_OWNER);

        for (StreamMetaData streamMetaData : metadataConfigValue) {
            streamMessageBuilder.addMetadataConfig(getMetadataConfig(streamMetaData.getId(),
                    streamMetaData.getEncryptionScheme()));
        }

        try {
            response = stub.createStream(streamMessageBuilder.build());
        } catch (StatusRuntimeException e) {
            String msg = ("GRPC failed: " + e.getStatus());
            LOGGER.error(msg);
            throw new CouldNotStoreException(msg);
        }

        return response.getStreamId();
    }

    @Override
    public long getLastWrittenChunkId(long inputStreamId) throws InvalidQueryException, UnsupportedOperationException {
        chunkId response;
        try {
            response = stub.getLastWrittenChunk(streamId.newBuilder().setStreamId(inputStreamId).build());
        } catch (StatusRuntimeException e) {
            String msg = ("GRPC failed: " + e.getStatus());
            LOGGER.error(msg);
            throw new InvalidQueryException(msg);
        }

        return response.getId();
    }

    @Override
    public long addChunk(long streamIdValue, EncryptedChunk encryptedChunk, EncryptedDigest encryptedDigest)
            throws CouldNotStoreException {
        chunkId response;
        digest.Builder digestBuilder = digest.newBuilder()
                .setStart(chunkId.newBuilder().setId(encryptedDigest.getChunkIdFrom()))
                .setEnd(chunkId.newBuilder().setId(encryptedDigest.getChunkIdTo()));

        for (EncryptedMetadata metaDataValue : encryptedDigest.getPayload()) {
            metadataContent.Builder metadataContentBuilder = metadataContent.newBuilder()
                    .setConfig(getMetadataConfig(metaDataValue.getMetadataId(), metaDataValue.getEncryptionScheme()));

            switch (metaDataValue.getEncryptionScheme()) {
                case LONG:
                    metadataContentBuilder.setLongPayload(longPayload.newBuilder()
                            .setEncryptedLong(metaDataValue.getPayloadAsLong())
                            .build());
                    break;
                case LONG_MAC:
                    metadataContentBuilder.setLongMacPayload(longMacPayload.newBuilder()
                            .setEncryptedLong(metaDataValue.getPayloadAsLong())
                            .setAuthCode(ByteString.copyFrom(metaDataValue.getMac()))
                            .setAuthCodeBits(metaDataValue.getNumMacBits())
                            .build());
                    break;
                case BIG_INT_128:
                    metadataContentBuilder.setBigIntPayload(bigIntPayload.newBuilder()
                            .setEncryptedBigInt(ByteString.copyFrom(metaDataValue.getPayload()))
                            .setBits(metaDataValue.getNumPayloadBits())
                            .build());
                    break;
                case BIG_INT_128_MAC:
                    metadataContentBuilder.setBigIntMacPayload(bigIntMacPayload.newBuilder()
                            .setEncryptedBigInt(ByteString.copyFrom(metaDataValue.getPayload()))
                            .setBits(metaDataValue.getNumPayloadBits())
                            .setAuthCode(ByteString.copyFrom(metaDataValue.getMac()))
                            .setAuthCodeBits(metaDataValue.getNumMacBits())
                            .build());
                    break;
                default:
                    String msg = "Unknown encryption type " + metaDataValue.getEncryptionScheme();
                    LOGGER.error(msg);
                    throw new CouldNotStoreException(msg);
            }
            digestBuilder.addMetadataContent(metadataContentBuilder.build());
        }

        chunk chunkMessage = chunk.newBuilder()
                .setChunkId(chunkId.newBuilder().setId(encryptedChunk.getChunkId()))
                .setStreamId(streamId.newBuilder().setStreamId(streamIdValue).build())
                .setChunkContent(ByteString.copyFrom(encryptedChunk.getPayload()))
                .build();

        try {
            response = stub.insertChunk(chunkCreationMessage.newBuilder()
                    .setChunk(chunkMessage)
                    .setDigest(digestBuilder.build()).build());
        } catch (StatusRuntimeException e) {
            String msg = ("GRPC failed: " + e.getStatus());
            LOGGER.error(msg);
            throw new CouldNotStoreException(msg);
        }

        return response.getId();
    }

    @Override
    public List<EncryptedChunk> getChunks(long streamIdValue, long chunkIdFrom, long chunkIdTo)
            throws CouldNotReceiveException {
        Iterator<chunk> response;
        chunkRequestMessage.Builder builder = chunkRequestMessage.newBuilder()
                .setStreamId(streamId.newBuilder().setStreamId(streamIdValue).build())
                .setStart(chunkId.newBuilder().setId(chunkIdFrom))
                .setEnd(chunkId.newBuilder().setId(chunkIdTo));

        try {
            response = stub.getRawData(builder.build());
        } catch (StatusRuntimeException e) {
            String msg = ("GRPC failed: " + e.getStatus());
            LOGGER.error(msg);
            throw new CouldNotReceiveException(msg);
        }
        ArrayList<EncryptedChunk> returnList = new ArrayList<>();
        try {
            while (response.hasNext()) {
                chunk next;
                next = response.next();

                returnList.add(new EncryptedChunk(next.getStreamId().getStreamId(), next.getChunkId().getId(),
                        next.getChunkContent().toByteArray()));
            }
        } catch (StatusRuntimeException e) {
            String msg = ("GRPC failed: " + e.getStatus());
            LOGGER.error(msg);
            throw new CouldNotReceiveException(msg);
        }

        return returnList;
    }

    @Override
    public void deleteStream(long streamIdValue) throws InvalidQueryException {
        try {
            stub.deleteStream(streamId.newBuilder().setStreamId(streamIdValue).build());
        } catch (StatusRuntimeException e) {
            String msg = ("GRPC failed: " + e.getStatus());
            LOGGER.error(msg);
            throw new InvalidQueryException(msg);
        }
    }

    @Override
    public List<EncryptedDigest> getStatisticalData(long streamIdValue, long chunkIdFrom, long chunkIdTo,
                                                    int granularity, List<StreamMetaData> metaData)
            throws InvalidQueryException {
        Iterator<digest> response;
        statisticRequestMessage.Builder builder = statisticRequestMessage.newBuilder()
                .setStreamId(streamId.newBuilder().setStreamId(streamIdValue).build())
                .setStart(chunkId.newBuilder().setId(chunkIdFrom))
                .setEnd(chunkId.newBuilder().setId(chunkIdTo))
                .setGranularity(granularity);

        for (StreamMetaData streamMetaData : metaData) {
            try {
                builder.addMetadataConfig(getMetadataConfig(streamMetaData.getId(),
                        streamMetaData.getEncryptionScheme()));
            } catch (CouldNotStoreException e) {
                throw new InvalidQueryException(e.getMessage());
            }
        }

        try {
            response = stub.getStatisticalData(builder.build());
        } catch (StatusRuntimeException e) {
            String msg = ("GRPC failed: " + e.getStatus());
            LOGGER.error(msg);
            throw new InvalidQueryException(msg);
        }

        ArrayList<EncryptedDigest> returnList = new ArrayList<>();
        try {
            while (response.hasNext()) {
                digest next = response.next();

                List<EncryptedMetadata> encryptedMetadata = new ArrayList<>();
                for (metadataContent content : next.getMetadataContentList()) {
                    metadataConfig config = content.getConfig();
                    switch (content.getPayloadCase().getNumber()) {
                        case metadataContent.LONG_PAYLOAD_FIELD_NUMBER:
                            encryptedMetadata.add(new EncryptedMetadata(content.getLongPayload().getEncryptedLong(),
                                    config.getId(), getEncryptionScheme(config.getScheme())));
                            break;
                        case metadataContent.LONG_MAC_PAYLOAD_FIELD_NUMBER:
                            encryptedMetadata.add(new EncryptedMetadata(content.getLongMacPayload().getEncryptedLong(),
                                    new BigInteger(content.getLongMacPayload().getAuthCode().toByteArray()),
                                    config.getId(), getEncryptionScheme(config.getScheme())));
                            break;
                        case metadataContent.BIG_INT_PAYLOAD_FIELD_NUMBER:
                            encryptedMetadata.add(new EncryptedMetadata(
                                    new BigInteger(content.getBigIntPayload().getEncryptedBigInt().toByteArray()),
                                    config.getId(), getEncryptionScheme(config.getScheme())));
                            break;
                        case metadataContent.BIG_INT_MAC_PAYLOAD_FIELD_NUMBER:
                            encryptedMetadata.add(new EncryptedMetadata(
                                    new BigInteger(content.getBigIntMacPayload().getEncryptedBigInt().toByteArray()),
                                    new BigInteger(content.getBigIntMacPayload().getAuthCode().toByteArray()),
                                    config.getId(), getEncryptionScheme(config.getScheme())));
                            break;
                        default:
                            String msg = "Unknown metadata content payload type " + content.getPayloadCase().getNumber();
                            LOGGER.error(msg);
                            throw new InvalidQueryException(msg);
                    }
                }
                returnList.add(new EncryptedDigest(streamIdValue, next.getStart().getId(), next.getEnd().getId(),
                        encryptedMetadata));
            }
        } catch (StatusRuntimeException e) {
            String msg = ("GRPC failed: " + e.getStatus());
            LOGGER.error(msg);
            throw new InvalidQueryException(msg);
        }

        return returnList;
    }

    private metadataConfig getMetadataConfig(int id, StreamMetaData.MetadataEncryptionScheme encryptionSchemeValue)
            throws CouldNotStoreException {
        EncryptionScheme encryptionScheme;
        switch (encryptionSchemeValue) {
            case LONG:
                encryptionScheme = EncryptionScheme.LONG;
                break;
            case LONG_MAC:
                encryptionScheme = EncryptionScheme.LONG_MAC;
                break;
            case BIG_INT_128:
                encryptionScheme = EncryptionScheme.BIG_INT;
                break;
            case BIG_INT_128_MAC:
                encryptionScheme = EncryptionScheme.BIG_INT_MAC;
                break;
            default:
                String msg = "Unknown encryption type " + encryptionSchemeValue;
                LOGGER.error(msg);
                throw new CouldNotStoreException(msg);
        }
        return metadataConfig.newBuilder().setScheme(encryptionScheme).setId(id).build();
    }

    private StreamMetaData.MetadataEncryptionScheme getEncryptionScheme(EncryptionScheme encryptionScheme)
            throws InvalidQueryException {
        switch (encryptionScheme) {
            case LONG:
                return StreamMetaData.MetadataEncryptionScheme.LONG;
            case LONG_MAC:
                return StreamMetaData.MetadataEncryptionScheme.LONG_MAC;
            case BIG_INT:
                return StreamMetaData.MetadataEncryptionScheme.BIG_INT_128;
            case BIG_INT_MAC:
                return StreamMetaData.MetadataEncryptionScheme.BIG_INT_128_MAC;
            default:
                String msg = "Unknown encryption type " + encryptionScheme;
                LOGGER.error(msg);
                throw new InvalidQueryException(msg);
        }
    }
}
