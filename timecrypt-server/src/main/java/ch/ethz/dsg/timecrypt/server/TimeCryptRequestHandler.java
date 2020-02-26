/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.server;

import ch.ethz.dsg.timecrypt.crypto.CryptoContentFactory;
import ch.ethz.dsg.timecrypt.index.Chunk;
import ch.ethz.dsg.timecrypt.exceptions.TimeCryptRequestException;
import ch.ethz.dsg.timecrypt.index.blockindex.node.NodeContent;
import ch.ethz.dsg.timecrypt.protocol.TimeCryptProtocol.*;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TimeCryptRequestHandler extends SimpleChannelInboundHandler<RequestMessage> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimeCryptRequestHandler.class);

    private TimeCryptRequestManager manager;

    public TimeCryptRequestHandler(TimeCryptRequestManager manager) {
        super();
        this.manager = manager;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RequestMessage msg) throws Exception {
        try {
            MessageRequestType type = msg.getType();
            switch (type) {
                case CREATE_STREAM:
                    CreateStream msgC = msg.getCreateStream();
                    manager.createStream(ctx, msgC.getUid(), msgC.getOwner());
                    break;
                case DELETE_STREAM:
                    DeleteStream msgD = msg.getDeleteStream();
                    manager.deleteStream(ctx, msgD.getUid(), msgD.getOwner());
                    break;
                case GET_CHUNKS:
                    GetChunks msgG = msg.getGetChunks();
                    manager.getChunks(ctx, msgG.getUid(), msgG.getOwner(), msgG.getFrom(), msgG.getTo());
                    break;
                case INSERT_CHUNK:
                    InsertChunk msgI = msg.getInsertChunk();
                    Chunk chunk = new Chunk(msgI.getKey(), msgI.getChunk().toByteArray());
                    NodeContent[] meta = CryptoContentFactory.createNodeContentsForRequest(msgI.getMetadataList());
                    manager.insertChunk(ctx, msgI.getUid(), msgI.getOwner(), msgI.getFrom(), msgI.getTo(), meta, chunk);
                    break;
                case GET_STATISTICS:
                    GetStatistics msgS = msg.getGetStatistics();
                    manager.getStatistics(ctx, msgS.getUid(), msgS.getOwner(), msgS.getFrom(),
                            msgS.getTo(), msgS.getGranularity(), msgS.getDigestidList().stream().mapToInt(i->i).toArray());
                    break;
                case GET_MULTI:
                    GetStatisticsMulti msgSM = msg.getGetStatisticsMulti();
                    manager.getStatisticsMulti(ctx, msgSM.getUidFrom(), msgSM.getUidTo(), msgSM.getOwner(), msgSM.getFrom(),
                            msgSM.getTo(), msgSM.getGranularity(), msgSM.getDigestidList().stream().mapToInt(i->i).toArray());
                    break;
                case GET_METAINFO:
                    GetMetaConfiguration getMetaMsg = msg.getGetMetaConfig();
                    //manager.getMetaconfigurationForStream(ctx, getMetaMsg.getUid(), getMetaMsg.getOwner());
                    throw new RuntimeException("Not supported");
                    //break;
            }
        } catch (TimeCryptRequestException r) {
            LOGGER.error("Exception during message Processing. Msg: " + msg, r);
            ctx.writeAndFlush(ResponseMessage.newBuilder()
                    .setType(MessageResponseType.ERROR_RESPONSE)
                    .setErrorResponse(r.getErrorRespons())
                    .build());
        }

    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.error("Exception caught - closing channel", cause);
        ctx.close();
    }
}
