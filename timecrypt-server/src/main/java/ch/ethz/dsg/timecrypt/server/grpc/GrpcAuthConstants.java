/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.server.grpc;

import io.grpc.Context;
import io.grpc.Metadata;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;

public final class GrpcAuthConstants {

    /**
     * Key that retrieves the user from the metadata.
     */
    static final Metadata.Key<String> AUTH_USER_METADATA_KEY = Metadata.Key.of("User", ASCII_STRING_MARSHALLER);

    /**
     * Key that retrieves the password from the metadata.
     */
    static final Metadata.Key<String> AUTH_PASSWORD_METADATA_KEY =
            Metadata.Key.of("Pass", ASCII_STRING_MARSHALLER);

    /**
     * Key for the UserInfo in the context
     */
    public static final Context.Key<String> USER_INFO_KEY = Context.key("userInfo");

}
