/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.server;

import ch.ethz.dsg.timecrypt.protocol.TimeCryptNettyProtocol;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import io.netty.util.concurrent.EventExecutorGroup;

public class TimeCryptServerChannelInitializer extends ChannelInitializer<SocketChannel> {

    private NettyRequestManager manager;
    private EventExecutorGroup dbHandlerPool;

    public TimeCryptServerChannelInitializer(NettyRequestManager manager, EventExecutorGroup dbHandlerPool) {
        this.manager = manager;
        this.dbHandlerPool = dbHandlerPool;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline p = ch.pipeline();
        p.addLast(new ProtobufVarint32FrameDecoder());
        p.addLast(new ProtobufDecoder(TimeCryptNettyProtocol.RequestMessage.getDefaultInstance()));


        p.addLast(new ProtobufVarint32LengthFieldPrepender());
        p.addLast(new ProtobufEncoder());
        p.addLast(dbHandlerPool, new TimeCryptRequestHandler(manager));
    }
}
