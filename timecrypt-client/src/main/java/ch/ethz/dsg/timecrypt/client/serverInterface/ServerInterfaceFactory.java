/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.serverInterface;

import ch.ethz.dsg.timecrypt.client.serverInterface.grpcServer.GrpcServerClient;
import ch.ethz.dsg.timecrypt.client.serverInterface.mockServer.MockServerInterface;
import ch.ethz.dsg.timecrypt.client.serverInterface.nettyServer.NettyServerClient;
import ch.ethz.dsg.timecrypt.client.state.TimeCryptProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

import static ch.ethz.dsg.timecrypt.client.serverInterface.mockServer.MockServerInterface.getMockServerInterface;

/**
 * Allows to return different server interfaces based on some configuration.
 */
public class ServerInterfaceFactory {

    private static final String DEFAULT_PERSISTENT_FILE_NAME = "TimeCrypt_MockServer_Data.yml";
    private static final String SERVER_INTERFACE_ENVIRONMENT_VARIABLE = "SERVER_INTERFACE";
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerInterfaceFactory.class);
    private static InterfaceProvider interfaceProvider;

    private static boolean defaultProvider;

    static {
        String implementation = System.getenv(SERVER_INTERFACE_ENVIRONMENT_VARIABLE);

        if (implementation == null) {
            interfaceProvider = InterfaceProvider.GRPC_SERVER_INTERFACE;
            defaultProvider = true;
        } else {
            LOGGER.info("Using the value from environment variable " + SERVER_INTERFACE_ENVIRONMENT_VARIABLE +
                    " for selecting the right server interface.");

            interfaceProvider = null;
            for (InterfaceProvider value : InterfaceProvider.values()) {
                if (value.getExplanation().equals(implementation)) {
                    interfaceProvider = value;
                }
            }

            if (interfaceProvider == null) {
                interfaceProvider = InterfaceProvider.GRPC_SERVER_INTERFACE;
                LOGGER.error("Could not identify the right server interface implementation based on the value" +
                        implementation + " for selecting the right server interface. Falling back to " +
                        interfaceProvider.name());
                defaultProvider = true;
            } else {
                LOGGER.info("Selected " + interfaceProvider.name() + " as server interface - based on environment " +
                        "variable");
                defaultProvider = false;
            }
        }
    }

    public static boolean isDefaultProvider() {
        return defaultProvider;
    }

    public static void setInterfaceProvider(InterfaceProvider interfaceProvider) {
        ServerInterfaceFactory.interfaceProvider = interfaceProvider;
    }

    /**
     * Factory method for retrieving a MockServer that is optionally deserialized from a default path in the
     * operating systems temp folder.
     *
     * @return A MockServer implementation of the Server interface.
     * @throws IOException Exception that is thrown if something fails while deserializing an existing object.
     */
    // TODO switch this to reflection
    public static ServerInterface getServerInterface(TimeCryptProfile profile) throws IOException {
        switch (interfaceProvider) {
            case PERSISTENT_MOCK_SERVER_INTERFACE:
                return getMockServerInterface(System.getProperty("java.io.tmpdir") + File.separator +
                        DEFAULT_PERSISTENT_FILE_NAME);
            case IN_MEMORY_MOCK_SERVER_INTERFACE:
                return new MockServerInterface();
            case GRPC_SERVER_INTERFACE:
                return new GrpcServerClient(profile.getServerAddress(), profile.getServerPort());
            case NETTY_SERVER_INTERFACE:
                return new NettyServerClient(profile.getServerAddress(), profile.getServerPort());
            default:
                return null;
        }
    }

    public enum InterfaceProvider {

        IN_MEMORY_MOCK_SERVER_INTERFACE("IN_MEMORY_MOCK_SERVER_INTERFACE"),
        PERSISTENT_MOCK_SERVER_INTERFACE("PERSISTENT_MOCK_SERVER_INTERFACE"),
        NETTY_SERVER_INTERFACE("NETTY_SERVER_INTERFACE"),
        GRPC_SERVER_INTERFACE("GRPC_SERVER_INTERFACE"),
        ;

        private final String explanation;

        InterfaceProvider(String s) {
            this.explanation = s;
        }

        public String getExplanation() {
            return explanation;
        }
    }
}
