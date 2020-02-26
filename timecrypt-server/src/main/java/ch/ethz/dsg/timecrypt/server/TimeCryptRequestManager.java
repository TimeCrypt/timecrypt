/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.server;

import ch.ethz.dsg.timecrypt.exceptions.TimeCryptRequestException;
import ch.ethz.dsg.timecrypt.index.Chunk;
import ch.ethz.dsg.timecrypt.index.IStorage;
import ch.ethz.dsg.timecrypt.index.ITreeManager;
import ch.ethz.dsg.timecrypt.index.UserStreamTree;
import ch.ethz.dsg.timecrypt.index.blockindex.node.NodeContent;
import ch.ethz.dsg.timecrypt.protocol.TimeCryptProtocol.*;
import com.google.protobuf.ByteString;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TimeCryptRequestManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimeCryptRequestManager.class);

    private ITreeManager treeManager;
    private IStorage storage;

    public TimeCryptRequestManager(ITreeManager treeManager, IStorage storage) {
        this.treeManager = treeManager;
        this.storage = storage;
    }

    private ResponseMessage createSuccessResponse(String response, int id) {
        return ResponseMessage.newBuilder()
                .setType(MessageResponseType.SUCCESS_RESPONSE)
                .setSuccessResponse(SuccessResponse.newBuilder()
                        .setId(id)
                        .setMessage(response))
                .build();
    }

    private ResponseMessage createErrorResponse(String response, int id) {
        return ResponseMessage.newBuilder()
                .setType(MessageResponseType.ERROR_RESPONSE)
                .setErrorResponse(ErrorResponse.newBuilder()
                        .setId(id)
                        .setMessage(response))
                .build();
    }

    public ITreeManager getTreeManager() {
        return treeManager;
    }

    public IStorage getStorage() {
        return storage;
    }

    public void createStream(ChannelHandlerContext ctx, long uid, String owner) throws TimeCryptRequestException {
        try {
            treeManager.createTree(uid, owner);
            ctx.writeAndFlush(createSuccessResponse("Success Create", 1));
        } catch (Exception e) {
            LOGGER.error("Exception caught - while processing create stream request {uid " + uid +
                    " owner " + owner + " }", e);
            ctx.write(createErrorResponse("Message: " + e.getMessage(), 1));
        }
    }

    public void deleteStream(ChannelHandlerContext ctx, long uid, String owner) throws TimeCryptRequestException {
        try {
            treeManager.deleteTree(uid, owner);
            storage.deleteALL(uid, owner);
            ctx.writeAndFlush(createSuccessResponse("Success Delete", 1));
        } catch (Exception e) {
            LOGGER.error("Exception caught - while processing delete stream request {uid " + uid +
                    " owner " + owner + " }", e);
            ctx.write(createErrorResponse("Message: " + e.getMessage(), 1));
        }
    }

    public void getStatistics(ChannelHandlerContext ctx, long uid, String owner, long from, long to,
                              long granularity, int[] ids) throws TimeCryptRequestException {
        UserStreamTree userTree = treeManager.getTreeForUser(uid, owner, (int) to);
        long fromIter = from, toIter = from + granularity;
        int numIter = (int) ((to - from) / granularity);

        if (numIter > 1) {
            ResponseMessage multiTransfer = ResponseMessage.newBuilder()
                    .setType(MessageResponseType.MULTIRESPONSE)
                    .setMultiTransfer(MultiDataTransfer.newBuilder()
                            .setMessageType(MessageResponseType.STATISTICS_RESPONSE)
                            .setNumTransfers(numIter)
                            .build())
                    .build();
            ctx.write(multiTransfer);
        }

        NodeContent[] content;
        while (toIter <= to) {
            try {
                content = userTree.getTree().getAggregation(fromIter, toIter, ids);

                if (content == null) {
                    LOGGER.warn("Could not find any statistics data for the given request {uid " + uid +
                            " owner " + owner + " from " + from + " to " + to + " granularity " + granularity +
                                    " ids " + Arrays.toString(ids) + " }");
                    ctx.write(createErrorResponse("Could not find any statistics data for the given request "
                            , 1));
                }

                List<Metadata> metadata = new ArrayList<>(content.length);
                for (int iter : ids) {
                    metadata.add(Metadata.newBuilder()
                            .setDigestid(iter)
                            .setData(ByteString.copyFrom(content[iter].encode()))
                            .build());
                }


                ResponseMessage response = ResponseMessage.newBuilder()
                        .setType(MessageResponseType.STATISTICS_RESPONSE)
                        .setStatisticsResponse(StatisticsResponse.newBuilder().addAllData(metadata))
                        .build();
                ctx.write(response);
            } catch (Exception e) {
                LOGGER.error("Exception caught - while processing node content of statistic request {uid " + uid +
                        " owner " + owner + " from " + from + " to " + to + " granularity " + granularity +
                        " ids " + Arrays.toString(ids) + " }", e);
                ctx.write(createErrorResponse("Message: " + e.getMessage(), 1));
            }

            toIter += granularity;
            fromIter += granularity;

        }
        ctx.flush();
    }

    public void getStatisticsMulti(ChannelHandlerContext ctx, long uidFrom, long uidTo, String owner, long from, long to,
                                   long granularity, int[] ids) throws TimeCryptRequestException {
        //TODO: This method is a hack for benchmarks
        int numStreams = (int) (uidTo - uidFrom) + 1;
        UserStreamTree[] trees = new UserStreamTree[numStreams];
        int count = 0;
        for (long id = uidFrom; id <= uidTo; id++) {
            trees[count++] = treeManager.getTreeForUser(id, owner, (int) to);
        }
        //ITreeMetaInfo metaInfo = userTree.getInfo();
        //int[] ids = userTree.getInfo().getIDsForTypes(types);
        long fromIter = from, toIter = from + granularity;
        int numIter = (int) ((to - from) / granularity);

        if (numIter > 1) {
            ResponseMessage multiTransfer = ResponseMessage.newBuilder()
                    .setType(MessageResponseType.MULTIRESPONSE)
                    .setMultiTransfer(MultiDataTransfer.newBuilder()
                            .setMessageType(MessageResponseType.STATISTICS_RESPONSE)
                            .setNumTransfers(numIter)
                            .build())
                    .build();
            ctx.write(multiTransfer);
        }

        NodeContent[] content = null;
        while (toIter <= to) {
            try {
                count = 0;
                for (long id = uidFrom; id <= uidTo; id++) {
                    if (count == 0)
                        content = trees[count++].getTree().getAggregation(fromIter, toIter, ids);
                    else {
                        NodeContent[] tmp = trees[count++].getTree().getAggregation(fromIter, toIter, ids);
                        for (int j = 0; j < content.length; j++) {
                            content[j].mergeOther(tmp[j]);
                        }
                    }

                }
                if (content == null) {
                    LOGGER.warn("Could not find any statistics data for the given request {uidFrom "
                                    + uidFrom + " uidTo " + uidTo + " owner " + owner + " from " + from + " to " + to +
                                    " granularity " + granularity + " ids " + Arrays.toString(ids) + "}");
                    ctx.write(createErrorResponse("Could not find any statistics data for the given request "
                            , 1));
                }

                List<Metadata> metadata = new ArrayList<>(content.length);
                for (int iter : ids) {
                    metadata.add(Metadata.newBuilder()
                            .setDigestid(iter)
                            .setData(ByteString.copyFrom(content[iter].encode()))
                            .build());
                }


                ResponseMessage response = ResponseMessage.newBuilder()
                        .setType(MessageResponseType.STATISTICS_RESPONSE)
                        .setStatisticsResponse(StatisticsResponse.newBuilder().addAllData(metadata))
                        .build();
                ctx.write(response);
            } catch (Exception e) {
                LOGGER.error("Exception caught - while processing node content of statistic multi request {uidFrom "
                        + uidFrom + " uidTo " + uidTo + " owner " + owner + " from " + from + " to " + to +
                        " granularity " + granularity + " ids " + Arrays.toString(ids) + "}", e);
                ctx.write(createErrorResponse("Message: " + e.getMessage(), 1));
            }

            toIter += granularity;
            fromIter += granularity;

        }
        ctx.flush();
    }

    public void getChunks(ChannelHandlerContext ctx, long uid, String owner, long from,
                          long to) throws TimeCryptRequestException {
        /*
        UserStreamTree userTree = treeManager.getTreeForUser(uid, owner);
        List<Integer> getKeys = userTree.getTree().getRange(from, to);*/
        List<Integer> getKeys = new ArrayList<>();
        for (int i = (int) from; i < to; i++) {
            getKeys.add(i);
        }
        if (getKeys.size() > 1) {
            ResponseMessage multiTransfer = ResponseMessage.newBuilder()
                    .setType(MessageResponseType.MULTIRESPONSE)
                    .setMultiTransfer(MultiDataTransfer.newBuilder()
                            .setMessageType(MessageResponseType.DATA_RESPONSE)
                            .setNumTransfers(getKeys.size())
                            .build())
                    .build();
            ctx.write(multiTransfer);
        }

        for (int key : getKeys) {
            try {
                Chunk curChunk = storage.getChunk(uid, owner, key);
                if (curChunk == null) {
                    LOGGER.warn("Could not find any chunks data for the given request {uid " + uid +
                            " owner " + owner + " from " + from + " to " + to + "}");
                    ctx.write(createErrorResponse("Could not find any chunks data for the given request "
                            , 1));
                }
                ResponseMessage chunkResponse = ResponseMessage.newBuilder()
                        .setType(MessageResponseType.DATA_RESPONSE)
                        .setDataResponse(DataResponse.newBuilder()
                                .setKey(curChunk.getStorageKey())
                                .setData(ByteString.copyFrom(curChunk.getData())))
                        .build();
                ctx.write(chunkResponse);
            } catch (Exception e) {
                LOGGER.error("Exception caught - while processing chunks of get chunk request {uid "
                        + uid + " owner " + owner + " from " + from + " to " + to + "}", e);
                ctx.write(createErrorResponse("Message: " + e.getMessage(), 1));
            }
        }
        ctx.flush();
    }

    public void insertChunk(ChannelHandlerContext ctx, long uid, String owner, long from, long to, NodeContent[] metadata,
                            Chunk chunk) throws TimeCryptRequestException {

        try {
            UserStreamTree userTree = treeManager.getTreeForUser(uid, owner, (int) from);

            storage.putChunk(uid, owner, chunk);
            userTree.getTree().insert(chunk.getStorageKey(), metadata, from, to);
            ctx.writeAndFlush(createSuccessResponse("Success Insert", 1));
        } catch (Exception e) {
            LOGGER.error("Exception caught - while processing insert chunk request {uid "
                    + uid + " owner " + owner + " from " + from + " to " + to + " metadata " + Arrays.toString(metadata)
                    + " chunk " + chunk + "}", e);
            throw new TimeCryptRequestException(e.getMessage(), e.hashCode());
        }
    }
    /*
    public void getMetaconfigurationForStream(ChannelHandlerContext ctx, long uid, String owner) {
        try {
            UserStreamTree userTree = treeManager.getTreeForUser(uid, owner);
            ITreeMetaInfo metainfo = userTree.getInfo();
            MetaConfigResponse.Builder mrMsgB = MetaConfigResponse.newBuilder();
            for (int i = 0; i < metainfo.getNumTypes(); i++) {
                mrMsgB.addMetaConfig(MetaConfig.newBuilder()
                        .setNumdigests(metainfo.getStatisticsTypeForID(i)));
            }
            ResponseMessage metaResponse = ResponseMessage.newBuilder()
                    .setType(MessageResponseType.META_CONFIG_RESPONSE)
                    .setMetConfigResponse(mrMsgB)
                    .build();
            ctx.writeAndFlush(metaResponse);
        } catch (Exception e) {
            throw new TimeCryptRequestException(e.getMessage(), e.hashCode());
        }
    }*/

}
