/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.state;

import ch.ethz.dsg.timecrypt.client.exceptions.CouldNotStoreException;
import ch.ethz.dsg.timecrypt.client.streamHandling.Stream;

import java.util.Map;

/**
 * Abstraction for TimeCrypts user profiles. A profile is used to store the private information that are associated
 * with a TimeCrypt time series as well as other meta information like the server that is used or the username of
 * the client on the server.
 */
public interface TimeCryptProfile {

    Map<Long, Stream> getStreams();

    /**
     * The address of the server that is associated with this profile.
     *
     * @return A server address.
     */
    String getServerAddress();

    void setServerAddress(String serverAddress);

    int getServerPort();

    void setServerPort(int port);

    String getProfileName();

    void setProfileName(String profileName);

    String getUserName();

    void setUserName(String userName);

    void addStream(Stream stream);

    Stream getStream(long id);

    /**
     * Synchronize the profile with its data source. In case of conflicts like existing files or content that differs
     * between client and server you can force the synchronization.
     *
     * @param force Should the synchronization be forced?
     * @return True in case of successful write, false if not.
     * @throws Exception Can throw exceptions that are defined by the corresponding implementation of the profile.
     */
    boolean syncProfile(boolean force) throws CouldNotStoreException;

    /**
     * Indicates that a change occurred since the last write.
     *
     * @return True if the profile is "dirty" i.e. that a change occurred since the last sync. False if it is in sync
     * with its storage.
     */
    boolean isDirty();

    void deleteStream(long id);

//    void deleteProfile();
}
