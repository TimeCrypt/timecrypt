/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.state;

import ch.ethz.dsg.timecrypt.client.exceptions.CouldNotReceiveException;
import ch.ethz.dsg.timecrypt.client.exceptions.CouldNotStoreException;
import ch.ethz.dsg.timecrypt.client.exceptions.InvalidQueryException;
import ch.ethz.dsg.timecrypt.client.exceptions.QueryFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.io.*;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

/**
 * Implementation of a TimeCryptKeystore that uses a local file to store data.
 */
public class LocalTimeCryptKeystore implements TimeCryptKeystore {

    private static final String KEYSTORE_TYPE = "pkcs12";

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalTimeCryptKeystore.class);

    private final KeyStore keyStore;
    private final String path;
    private final char[] pwdArray;
    private final boolean dirty;

    /**
     * Creates a new local TimeCrypt Keystore that is associated with a file and a password.
     *
     * @param ks       The Java Keystore that this TimeCrytpt Keystore shall use.
     * @param path     The path that this keystore is associated with. If it is null the keystore will be only kept in
     *                 memory (e.g. for testing).
     * @param pwdArray The password that this keystore is associated with.
     */
    private LocalTimeCryptKeystore(KeyStore ks, String path, char[] pwdArray) {
        this.keyStore = ks;
        this.pwdArray = pwdArray;
        this.path = path;
        dirty = false;
    }

    /**
     * Try to load the keystore from a file.
     *
     * @param path     The path to the file that should be a keystore.
     * @param pwdArray The password to unlock the keystore.
     * @return The unlocked keystore.
     * @throws IOException              Could not find the keystore or could not open it.
     * @throws KeyStoreException        Could not get a keystore from the JVM.
     * @throws CertificateException     Could not load the keystore.
     * @throws NoSuchAlgorithmException Could not load the keystore.
     */
    public static LocalTimeCryptKeystore localKeystoreFromFile(String path, char[] pwdArray) throws IOException,
            KeyStoreException, CertificateException, NoSuchAlgorithmException {
        if (!new File(path).exists()) {
            throw new FileNotFoundException();
        }

        KeyStore ks = KeyStore.getInstance(KEYSTORE_TYPE);
        ks.load(new FileInputStream(path), pwdArray);

        return new LocalTimeCryptKeystore(ks, path, pwdArray);
    }

    /**
     * Create a new local TimeCrypt keystore associated to a path but does not sync it yet.
     *
     * @param path     The path that this keystore is associated with. If it is null the keystore will be only kept in
     *                 memory (e.g. for testing).
     * @param pwdArray The password to use for the new keystore.
     * @return The new keystore.
     * @throws KeyStoreException        Could not get a Keystore from the JVM.
     * @throws IOException              Could not load the keystore.
     * @throws CertificateException     Could not load the keystore.
     * @throws NoSuchAlgorithmException Could not load the keystore.
     */
    public static LocalTimeCryptKeystore createLocalKeystore(String path, char[] pwdArray) throws KeyStoreException,
            CertificateException, NoSuchAlgorithmException, IOException {
        KeyStore ks = KeyStore.getInstance(KEYSTORE_TYPE);
        ks.load(null, pwdArray);
        return new LocalTimeCryptKeystore(ks, path, pwdArray);
    }

    @Override
    public void syncKeystore(boolean force) throws QueryFailedException, CouldNotStoreException {
        if (path != null) {
            if (new File(path).exists() && !force) {
                throw new QueryFailedException(QueryFailedException.FailReason.FILE_ALREADY_EXISTING, "File: " + path
                        + " Please force writing if you are sure to overwrite it.");
            }

            try {
                keyStore.store(new FileOutputStream(path), pwdArray);
            } catch (KeyStoreException | NoSuchAlgorithmException | IOException | CertificateException e) {
                LOGGER.error("Error occurred during the storage of the keystore", e);
                throw new CouldNotStoreException("Error occurred during storing of the keystore: " + e.getMessage());
            }
        }
    }

    @Override
    public boolean isDirty() {
        return dirty;
    }

    @Override
    public void storeStreamKey(String keyID, SecretKey streamMasterKey) throws CouldNotStoreException {
        // Don't put a password to the key because the keystore is already encrypted.
        try {
            keyStore.setEntry(keyID, new KeyStore.SecretKeyEntry(streamMasterKey),
                    new KeyStore.PasswordProtection("".toCharArray()));
        } catch (KeyStoreException e) {
            LOGGER.error("Error occurred during the storage of the keystore", e);
            throw new CouldNotStoreException("Error occurred during storing of the keystore: " + e.getMessage());
        }

    }

    @Override
    public SecretKey receiveStreamKey(String keyId) throws CouldNotReceiveException, InvalidQueryException {
        // Don't put a password to the key because the keystore is already encrypted.
        try {
            return (SecretKey) keyStore.getKey(keyId, "".toCharArray());
        } catch (KeyStoreException | NoSuchAlgorithmException e) {
            LOGGER.error("Error occurred during the recival a key", e);
            throw new CouldNotReceiveException("Error occurred during reciving the key with id " + keyId
                    + " Error is: " + e.getMessage());
        } catch (UnrecoverableKeyException e) {
            throw new InvalidQueryException("Error occurred during storing of the keystore: " + e.getMessage());
        }
    }
}
