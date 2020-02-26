/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

import ch.ethz.dsg.timecrypt.protocol.TimeCryptProtocol;
import ch.ethz.dsg.timecrypt.protocol.TimeCryptProtocol.CreateStream;
import ch.ethz.dsg.timecrypt.protocol.TimeCryptProtocol.MessageRequestType;
import ch.ethz.dsg.timecrypt.protocol.TimeCryptProtocol.RequestMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestProtocol {

    @Test
    public void checkBasicMessage() throws InvalidProtocolBufferException {
        TimeCryptProtocol.MetaConfig metaConfig = TimeCryptProtocol.MetaConfig.newBuilder().setNumdigests(5).build();

        CreateStream streamCreate = CreateStream.newBuilder()
                .setUid(1)
                .setMetadataConfig(metaConfig)
                .setOwner("Lukas")
                .build();

        RequestMessage msg = RequestMessage.newBuilder()
                .setType(MessageRequestType.CREATE_STREAM)
                .setCreateStream(streamCreate)
                .build();
        byte[] data = msg.toByteArray();

        RequestMessage after = RequestMessage.parseFrom(data);
        if (after.getType().equals(MessageRequestType.CREATE_STREAM)) {
            CreateStream streamafter = after.getCreateStream();
            assertEquals(streamafter.getUid(), streamCreate.getUid());
            assertEquals(streamafter.getOwner(), streamCreate.getOwner());

        } else {
            assertTrue(false);
        }

    }

}
