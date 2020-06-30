/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.server.grpc;

import io.grpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthServerInterceptor implements ServerInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthServerInterceptor.class);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> serverCall, Metadata metadata,
                                                                 ServerCallHandler<ReqT, RespT> serverCallHandler) {

        String user = metadata.get(GrpcAuthConstants.AUTH_USER_METADATA_KEY);

        if (user == null || user.equals("")) {
            LOGGER.warn("Unauthenicatd request. User = " + user);
            serverCall.close(Status.UNAUTHENTICATED, metadata);
            return new ServerCall.Listener<ReqT>() {};
        }

        String pass = metadata.get(GrpcAuthConstants.AUTH_PASSWORD_METADATA_KEY);

        // TODO: Do sth. with the password

        Context ctx = Context.current().withValue(GrpcAuthConstants.USER_INFO_KEY, user);
        return Contexts.interceptCall(ctx, serverCall, metadata, serverCallHandler);
    }
}
