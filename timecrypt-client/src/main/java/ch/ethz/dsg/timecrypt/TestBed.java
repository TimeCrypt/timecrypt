/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt;

import ch.ethz.dsg.timecrypt.client.exceptions.StreamNotYetStartedException;
import ch.ethz.dsg.timecrypt.client.state.LocalTimeCryptKeystore;
import ch.ethz.dsg.timecrypt.client.state.LocalTimeCryptProfile;
import ch.ethz.dsg.timecrypt.client.state.TimeCryptKeystore;
import ch.ethz.dsg.timecrypt.client.state.TimeCryptProfile;
import ch.ethz.dsg.timecrypt.client.streamHandling.DataPoint;
import ch.ethz.dsg.timecrypt.client.streamHandling.Stream;
import ch.ethz.dsg.timecrypt.client.streamHandling.TimeUtil;
import ch.ethz.dsg.timecrypt.client.streamHandling.metaData.StreamMetaData;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.*;
import java.util.concurrent.Callable;

@Command(name = "timecrypt-testbed", mixinStandardHelpOptions = true, description = "Testbed for TimeCrypt. Can send " +
        "measurements to a stream or multiple streams after optionally creating them. ", version = "1.0-SNAPSHOT")
public class TestBed implements Callable<Integer> {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(TestBed.class);

    private static final String DEFAULT_STREAM_DESCRIPTION = "Stream created by the TimeCrypt testbed";

    @Option(names = {"-v", "--verbose"},
            description = "Show verbose output. ",
            defaultValue = "false",
            negatable = true)
    private static boolean verbose;

    @Option(names = {"-p", "--password"},
            description = "The keystore password to use for this execution. WATCH OUT: Providing the password on the " +
                    "commandline might be insecure. Consider providing it by specifying the environment variable " +
                    "TIMECRYPT_KEYSTORE_PASSWORD")
    private String keyStorePassword = System.getenv(CliHelper.KEY_STORE_PASSWORD_VARIABLE);

    @Option(names = {"-c", "--config-folder"},
            description = "The folder to expect all the config in. Default: $HOME/.TimeCryptClient")
    private String configFolderPath = CliHelper.CONFIG_FOLDER;

    @Option(names = "--no-create",
            description = "Create keystore, profile and stream(s) if they are not existing? Default: yes",
            defaultValue = "true", negatable = true)
    private boolean create;

    @Option(names = "--no-delete",
            description = "Remove every resource that was created during execution? Default: yes",
            defaultValue = "true",
            negatable = true)
    private boolean delete;

    @Option(names = {"--keystore"},
            description = "The keystore to use for this execution. Default: timecrypt.jks in the config folder.")
    private String keystorePath = CliHelper.KEY_STORE_FILE;

    @Option(names = {"--profile"},
            description = "The profile to use for this execution. Existing profiles will be searched in the config " +
                    "folder. If there is no profile with this name a new profile with this name will be created (if " +
                    "creation is enabled) Default: testbed.")
    private String profileName = "testbed";

    @Option(names = {"--user"},
            description = "The user to use for profile creation. WATCH OUT: This variable gets ignored if a existing " +
                    "profile is used! Default: 'testbed'")
    private String user = "testbed";

    @Option(names = {"--server"},
            description = "The server to use for profile creation. WATCH OUT: This variable gets ignored if a " +
                    "existing profile is used! Default: '127.0.0.1' / the " + CliHelper.SERVER_HOST_VARIABLE +
                    " environment variable.")
    private String server = CliHelper.getStringFromEnv(CliHelper.SERVER_HOST_VARIABLE, "127.0.0.1");

    @Option(names = {"--port"},
            description = "The port to use for profile creation. WATCH OUT: This variable gets ignored if a " +
                    "existing profile is used! Default: '15000' / the " + CliHelper.SERVER_PORT_VARIABLE +
                    " environment variable.")
    private int port = CliHelper.getIntFromEnv(CliHelper.SERVER_PORT_VARIABLE, 15000);

    @Option(names = {"--stream-name"},
            description = "The stream to use for inserting chunks. If there is no stream with this name and create " +
                    "is enabled a stream with this name will be created. This value is used as a prefix if multiple " +
                    "will be created due to the configuration options that are selected! Default: 'testbed'")
    private String streamPrefix = "testbed";

    @Option(names = "--precision", description = "The size of the chunks for stream cration. This also determines " +
            "the minimal size of statistical queries. Possible values: ${COMPLETION-CANDIDATES}. " +
            "WATCH OUT: This variable gets ignored if a existing profile is used! Default: Ten seconds.")
    private TimeUtil.Precision streamPrecision = TimeUtil.Precision.TEN_SECONDS;

    @Option(names = "--resolution-levels", split = ",",
            description = "The precisions to use for additional resolution levels " +
                    "for of stream sharing. Possible values: ${COMPLETION-CANDIDATES}. " +
                    "WATCH OUT: This variable gets ignored if a existing profile is used! Default: All precision " +
                    "levels that are greater than you chosen precision level.")
    private TimeUtil.Precision[] streamResulutionLevels = null;

    @Option(names = "--stream-encryption", split = ",",
            description = "The encryption to use for the stream statistical data " +
                    "Possible values: ${COMPLETION-CANDIDATES}. " +
                    "WATCH OUT: This variable gets ignored if a existing stream is used! " +
                    "WATCH OUT: You can specify multiple encryption types. In this case multiple streams would " +
                    "be created. Default: LONG")
    private StreamMetaData.MetadataEncryptionScheme[] metadataEncryptionSchemes = null;

    @Option(names = "--stream-metadata", split = ",",
            description = "The metadata to provide for the stream statistical data " +
                    "Possible values: ${COMPLETION-CANDIDATES}. " +
                    "WATCH OUT: This variable gets ignored if a existing stream is used! Default: All possible")
    private StreamMetaData.MetadataType[] metadataTypes = null;

    @Option(names = {"-f", "--insert-frequency"}, defaultValue = "1000",
            description = "The frequency to use for inserting chunks in milliseconds. Default: 'testbed'")
    private int frequency;

    @Option(names = {"-n", "--number"}, defaultValue = "100",
            description = "The number of chunks to insert before terminating the program.")
    private int numberOfChunks;

    @Option(names = {"-t", "--threads"}, defaultValue = "1",
            description = "The number of threads for inserting chunks per stream.")
    private int threads;

    @Option(names = {"--min"}, defaultValue = "1",
            description = "The minimum value for inserting.")
    private int min;

    @Option(names = {"--max"}, defaultValue = "42",
            description = "The maximum value for inserting.")
    private int max;

    public static void main(String... args) {
        int exitCode = new CommandLine(new TestBed()).execute(args);
        System.exit(exitCode);
    }

    private static void log(String msg) {
        // TODO this should be done by rerouting the logger to stdout (add a new appender) but configuring the root
        //  logger from software did not work.
        if (verbose) {
            System.out.println(msg);
        }
        LOGGER.info(msg);
    }

    @Override
    public Integer call() throws Exception {
        boolean keystoreCreated = false;
        boolean profileCreated = false;
        boolean streamsCreated = false;
        String profilePath = configFolderPath + File.separator + profileName + CliHelper.DEFAULT_PROFILE_FILE_ENDING;

        TimeCryptKeystore keyStore;
        if (keyStorePassword == null) {
            LOGGER.error("No password defined");
            return 1;
        }
        if (new File(keystorePath).exists()) {
            try {
                keyStore = LocalTimeCryptKeystore.localKeystoreFromFile(keystorePath, keyStorePassword.toCharArray());
                log("Found existing keystore at: " + keystorePath);
            } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException e) {
                LOGGER.error("Error opening Keystore", e);
                return 1;
            }
        } else if (create) {
            CliHelper.ensureConfigFolder(configFolderPath);
            keystoreCreated = true;
            try {
                keyStore = LocalTimeCryptKeystore.createLocalKeystore(keystorePath, keyStorePassword.toCharArray());
                keyStore.syncKeystore(false);
                log("Keystore created at: " + keystorePath);

            } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException e) {
                LOGGER.error("Error creating Keystore", e);
                return 1;
            } catch (Exception e) {
                LOGGER.error("Error saving Keystore", e);
                return 1;
            }
        } else {
            LOGGER.error("No key store defined and create not enabled.");
            return 1;
        }

        TimeCryptProfile profile = null;

        List<TimeCryptProfile> profiles = CliHelper.getLocalProfiles(configFolderPath, CliHelper.DEFAULT_PROFILE_FILE_ENDING);

        for (TimeCryptProfile curProfile : profiles) {
            if (curProfile.getProfileName().equals(profileName)) {
                profile = curProfile;
                log("Found profile: " + profileName);
            }
        }

        if (profile == null && create) {
            profile = new LocalTimeCryptProfile(profilePath, user, profileName, server, port);
            log("Created profile: " + profileName);
            profileCreated = true;
        } else if (profile == null) {
            LOGGER.error("No profile defined and create not enabled.");
            return 1;
        }

        TimeCryptClient timeCryptClient = new TimeCryptClient(keyStore, profile);

        Map<Long, Stream> existingStreams = timeCryptClient.listStreams();
        List<Stream> streams = new ArrayList<>();

        for (long curStream : existingStreams.keySet()) {
            if (existingStreams.get(curStream).getName().equals(streamPrefix)) {
                streams.add(existingStreams.get(curStream));
                log("Found stream '" + existingStreams.get(curStream).getName() + "' with ID " + curStream);
            }
        }

        if (streams.size() > 0) {
            LOGGER.warn("Found existing stream - ignoring all provided stream options.");
        } else if (create) {
            if (metadataEncryptionSchemes == null) {
                metadataEncryptionSchemes =
                        new StreamMetaData.MetadataEncryptionScheme[]{StreamMetaData.MetadataEncryptionScheme.LONG};
            }

            if (metadataTypes == null) {
                metadataTypes = StreamMetaData.MetadataType.values();
            }
            List<StreamMetaData.MetadataType> metadataTypeList = Arrays.asList(metadataTypes);

            List<TimeUtil.Precision> streamResulutionLevelList;
            if (streamResulutionLevels == null) {
                streamResulutionLevelList = TimeUtil.Precision.getGreaterPrecisions(streamPrecision);
            } else {
                streamResulutionLevelList = Arrays.asList(streamResulutionLevels);
            }

            for (StreamMetaData.MetadataEncryptionScheme enc : metadataEncryptionSchemes) {
                String streamName = streamPrefix;

                if (metadataEncryptionSchemes.length > 1) {
                    streamName = streamPrefix + "_" + enc.name();
                }
                long streamID = timeCryptClient.createStream(streamName, DEFAULT_STREAM_DESCRIPTION, streamPrecision,
                        streamResulutionLevelList, metadataTypeList,
                        enc, configFolderPath + File.separator + profile.getProfileName() +
                                "_" + streamName + CliHelper.DEFAULT_CHUNK_STORE_FILE_ENDING);
                streams.add(timeCryptClient.getStream(streamID));
                log("Stream " + streamName + " created ");
            }
            streamsCreated = true;
        } else {
            LOGGER.error("No stream defined and create not enabled.");
            return 1;
        }

        List<Thread> threadList = new ArrayList<>();
        for (Stream curStream : streams) {
            for (int i = 0; i < threads; i++) {
                Thread thread = new Thread(new InsertionThread(timeCryptClient, numberOfChunks, curStream.getId(), min,
                        max, frequency));
                threadList.add(thread);
                thread.start();
            }
        }

        log("Start waiting for threads");
        for (Thread thread : threadList) {
            thread.join();
        }
        log("Finished waiting for threads");

        timeCryptClient.terminate();
        log("TimeCrypt terminated");

        if (delete) {
            if (streamsCreated) {
                log("Deleting created streams");
                for (Stream curStream : streams) {
                    log("Deleting stream " + curStream.getName());
                    timeCryptClient.deleteStream(curStream.getId());
                }
            }
            if (profileCreated) {
                log("Deleting created profile");
                File profileFile = new File(profilePath);
                // TODO maybe add this to the Client / TimeCryptProfile
                profileFile.delete();
            }
            if (keystoreCreated) {
                log("Deleting created keystore");
                File keystoreFile = new File(keystorePath);
                // TODO maybe add this to the Client / TimeCryptKeystore
                keystoreFile.delete();
            }
        }
        return 0;
    }

    private static class InsertionThread implements Runnable {
        private final Random r = new Random();
        private final int numInserts;
        private final TimeCryptClient timeCryptClient;
        private final long streamId;
        private final int min;
        private final int max;
        private final int frequency;

        private InsertionThread(TimeCryptClient timeCryptClient, int numInserts, long streamId, int min,
                                int max, int frequency) {
            this.numInserts = numInserts;
            this.timeCryptClient = timeCryptClient;
            this.streamId = streamId;
            this.min = min;
            this.max = max;
            this.frequency = frequency;
        }

        @Override
        public void run() {
            for (int i = 0; i < numInserts; i++) {
                int rnd = r.nextInt(max - min) + min;
                log("Adding value " + rnd + " to Stream " + streamId + " (" + i + "/" + numInserts + ")");

                try {
                    timeCryptClient.addDataPointToStream(streamId, new DataPoint(new Date(), rnd));
                } catch (StreamNotYetStartedException e) {
                    log("Stream " + streamId + " not yet started. " + e.getMessage());
                } catch (Exception e) {
                    LOGGER.error("Error adding value to stream ", e);
                    return;
                }
                try {
                    Thread.sleep(frequency);
                } catch (InterruptedException e) {
                    LOGGER.error("Woken up while sleeping - exiting", e);
                    return;
                }
            }
            log("Finished adding values to Stream " + streamId);
        }
    }
}
