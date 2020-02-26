/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.state;

import ch.ethz.dsg.timecrypt.client.exceptions.CouldNotReceiveException;
import ch.ethz.dsg.timecrypt.client.exceptions.CouldNotStoreException;
import ch.ethz.dsg.timecrypt.client.exceptions.InvalidQueryException;
import ch.ethz.dsg.timecrypt.client.exceptions.QueryFailedException;

import javax.crypto.SecretKey;

/**
 * Abstraction for TimeCrypts key management. Allows storing of TimeSeries Keys and User Keys.
 */
public interface TimeCryptKeystore {

    /**
     * Synchronize the keystore with its data source. In case of conflicts like existing files or content that differs
     * between client and server you can force the synchronization.
     *
     * @param force Should the synchronization be forced?
     * @throws QueryFailedException   Exception if the query to sync the keystore failed due to reasons in the query.
     * @throws CouldNotStoreException Exception if the query was tried to execute but did not succeed due to external
     *                                reasons like an unavailable server.
     */
    void syncKeystore(boolean force) throws QueryFailedException, CouldNotStoreException;

    /**
     * Indicates that a change occurred since the last write.
     *
     * @return True if the Keystore is "dirty" i.e. that a change occurred since the last sync. False if it is in sync
     * with its storage.
     */
    boolean isDirty();

    /**
     * Store a stream master key in the keystore.
     *
     * @param keyId           The ID for the key. This has to be unique across all entries in the keystore.
     * @param streamMasterKey The key for the the keystore.
     * @throws CouldNotStoreException Thrown if the corresponding implementation of the keystore could not save the key.
     */
    void storeStreamKey(String keyId, SecretKey streamMasterKey) throws CouldNotStoreException;

    /**
     * Get the master key of a stream from the keystore.
     *
     * @param keyId The ID for the key. This has to be unique across all entries in the keystore.
     * @return The key from the the keystore.
     * @throws CouldNotReceiveException Exception that is thrown if the key could not be recovered.
     * @throws InvalidQueryException    Exception that is thrown if the keystore does not know this keyId
     */
    SecretKey receiveStreamKey(String keyId) throws CouldNotReceiveException, InvalidQueryException;
}
