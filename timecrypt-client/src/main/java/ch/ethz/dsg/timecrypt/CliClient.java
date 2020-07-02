/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt;

import ch.ethz.dsg.timecrypt.client.exceptions.CouldNotReceiveException;
import ch.ethz.dsg.timecrypt.client.exceptions.QueryNeedsChunkScanException;
import ch.ethz.dsg.timecrypt.client.queryInterface.Interval;
import ch.ethz.dsg.timecrypt.client.queryInterface.Query;
import ch.ethz.dsg.timecrypt.client.state.LocalTimeCryptKeystore;
import ch.ethz.dsg.timecrypt.client.state.LocalTimeCryptProfile;
import ch.ethz.dsg.timecrypt.client.state.TimeCryptKeystore;
import ch.ethz.dsg.timecrypt.client.state.TimeCryptProfile;
import ch.ethz.dsg.timecrypt.client.streamHandling.Chunk;
import ch.ethz.dsg.timecrypt.client.streamHandling.DataPoint;
import ch.ethz.dsg.timecrypt.client.streamHandling.Stream;
import ch.ethz.dsg.timecrypt.client.streamHandling.TimeUtil;
import ch.ethz.dsg.timecrypt.client.streamHandling.metaData.StreamMetaData;
import org.beryx.textio.TextIO;
import org.beryx.textio.TextTerminal;
import org.beryx.textio.system.SystemTextTerminal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.sql.Timestamp;
import java.util.*;


/**
 * CLI client example implementation of a TimeCrypt client. The CLI client can be used for interactive work with a
 * TimeCrypt server.
 */
public class CliClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(CliClient.class);
    private final TimeCryptProfile profile;

    public CliClient() throws IOException {

        // Change the terminal here to use sth like a Swing based terminal
        TextTerminal<?> terminal = new SystemTextTerminal();
        TextIO textIO = new TextIO(terminal);

        terminal.printf("\nWelcome to TimeCrypt.\n");
        CliHelper.ensureConfigFolder(CliHelper.CONFIG_FOLDER);

        TimeCryptKeystore keyStore;
        if (new File(CliHelper.KEY_STORE_FILE).exists()) {
            keyStore = openKeystore(textIO);
        } else {
            CliHelper.ensureConfigFolder(CliHelper.CONFIG_FOLDER);
            keyStore = createKeystore(textIO);
        }

        profile = selectProfile(textIO);

        TimeCryptClient timeCryptClient = new TimeCryptClient(keyStore, profile);

        executeTimeCryptCommands(textIO, timeCryptClient);

        textIO.dispose("Session ended..");
    }

    public static void main(String[] args) throws IOException {
        new CliClient();
    }

    private TimeCryptKeystore openKeystore(TextIO textIO) {
        TextTerminal<?> terminal = textIO.getTextTerminal();

        terminal.println();
        terminal.println("Unlocking Keystore. ");
        String password = System.getenv(CliHelper.KEY_STORE_PASSWORD_VARIABLE);

        if (password == null) {
            terminal.println("Please enter the password for the Keystore (You can avoid this dialog by providing an");
            terminal.println("environment variable called " + CliHelper.KEY_STORE_PASSWORD_VARIABLE);

            password = textIO.newStringInputReader()
                    .withMinLength(10)
                    .withInputMasking(true)
                    .read("Password");
        } else {
            terminal.println("Using the value from environment variable " + CliHelper.KEY_STORE_PASSWORD_VARIABLE);
        }

        TimeCryptKeystore fileKeystore = null;
        try {
            fileKeystore = LocalTimeCryptKeystore.localKeystoreFromFile(CliHelper.KEY_STORE_FILE, password.toCharArray());

        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException e) {
            LOGGER.error("Error opening Keystore", e);
            terminal.println("Error opening Keystore");
            System.exit(1);
        }

        return fileKeystore;
    }

    private TimeCryptKeystore createKeystore(TextIO textIO) {
        TextTerminal<?> terminal = textIO.getTextTerminal();

        terminal.println();
        terminal.println("Could not detect existing Keystore. You need to create a keystore in order to save all");
        terminal.println("cryptographic material that is needed for TimeCrypt");
        terminal.println("The Keystore will be saved at: " + CliHelper.KEY_STORE_FILE);

        String password = System.getenv(CliHelper.KEY_STORE_PASSWORD_VARIABLE);
        terminal.println();

        if (password == null) {
            terminal.println("Please enter a password for the Keystore.");
            password = textIO.newStringInputReader()
                    .withMinLength(10)
                    .withInputMasking(true)
                    .read("Password");

            String password_rep = textIO.newStringInputReader()
                    .withMinLength(10)
                    .withInputMasking(true)
                    .read("Please repeat the Password");
            while (!password.equals(password_rep)) {
                terminal.printf("\nPasswords did not match - please repeat.\n");

                password = textIO.newStringInputReader()
                        .withMinLength(10)
                        .withInputMasking(true)
                        .read("Password");

                password_rep = textIO.newStringInputReader()
                        .withMinLength(10)
                        .withInputMasking(true)
                        .read("Please repeat the Password");
            }
            terminal.printf("\nPasswords match - creating keystore.\n");
        } else {
            terminal.println("Using the password from environment variable " + CliHelper.KEY_STORE_PASSWORD_VARIABLE);
        }

        TimeCryptKeystore fileKeystore = null;

        try {
            fileKeystore = LocalTimeCryptKeystore.createLocalKeystore(CliHelper.KEY_STORE_FILE, password.toCharArray());
            fileKeystore.syncKeystore(false);

        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException e) {
            LOGGER.error("Error creating Keystore", e);
            terminal.print(e.toString());
            System.exit(1);
        } catch (Exception e) {
            LOGGER.error("Error saving Keystore", e);
            terminal.print(e.toString());
            System.exit(1);
        }

        return fileKeystore;
    }

    private TimeCryptProfile selectProfile(TextIO textIO) {
        TextTerminal<?> terminal = textIO.getTextTerminal();
        TimeCryptProfile selectedProfile = null;

        boolean validProfile = false;

        List<TimeCryptProfile> profiles = CliHelper.getLocalProfiles(CliHelper.CONFIG_FOLDER,
                CliHelper.DEFAULT_PROFILE_FILE_ENDING);
        if (profiles.size() > 0) {
            terminal.println("Select profile: ");
            int cnt = 1;
            for (TimeCryptProfile profile : profiles) {
                terminal.printf("\n[%d] %s", cnt, profile.getProfileName());
                cnt++;
            }
            terminal.printf("\n[%d] create new profile\n", cnt);

            int selection = textIO.newIntInputReader().withMinVal(1).withMaxVal(cnt).withDefaultValue(1)
                    .read("Selection");
            if (selection != cnt) {
                selectedProfile = profiles.get(selection - 1);
                validProfile = true;
            }
        } else {
            terminal.println("No valid profile found");
        }

        if (!validProfile) {
            selectedProfile = createProfile(textIO);
            terminal.println("Switching to new profile.");

            try {
                selectedProfile.syncProfile(false);
            } catch (Exception e) {
                LOGGER.error("Error creating Profile", e);
                terminal.print(e.toString());
                System.exit(1);
            }
        }
        return selectedProfile;
    }

    private TimeCryptProfile createProfile(TextIO textIO) {
        TextTerminal<?> terminal = textIO.getTextTerminal();

        terminal.println();
        terminal.println("Creating new profile");

        String user = textIO.newStringInputReader()
                .withDefaultValue(System.getProperty("user.name"))
                .read("Username");

        String server = textIO.newStringInputReader()
                .withDefaultValue(CliHelper.getStringFromEnv(CliHelper.SERVER_HOST_VARIABLE, "127.0.0.1"))
                .withValueChecker((val, itemName) -> {
                    try {
                        // WORKAROUND: add any scheme to make the resulting URI valid.
                        URI uri = new URI("my://" + val);
                        String host = uri.getHost();

                        if (host.equals("")) {
                            // validation failed
                            return Collections.singletonList(val + " is not a valid host.");
                        }

                        // validation succeeded
                        return null;

                    } catch (URISyntaxException ex) {
                        // validation failed
                        return Collections.singletonList(val + " is not a valid host.");
                    }
                })
                .read("TimeCrypt server address");

        int port = textIO.newIntInputReader()
                .withDefaultValue(CliHelper.getIntFromEnv(CliHelper.SERVER_PORT_VARIABLE, 15000))
                .withMinVal(0)
                .withMaxVal(65535)
                .read("TimeCrypt server port");

        String profileName = textIO.newStringInputReader()
                .withDefaultValue(user + "@" + server)
                .read("Profile name");

        TimeCryptProfile newProfile = new LocalTimeCryptProfile(CliHelper.CONFIG_FOLDER + File.separator +
                profileName + CliHelper.DEFAULT_PROFILE_FILE_ENDING, user, profileName, server, port);
        try {
            newProfile.syncProfile(false);
        } catch (Exception e) {
            LOGGER.error("Error saving Profile.", e);
            terminal.print(e.toString());
            System.exit(1);
        }

        return newProfile;
    }

    private void executeTimeCryptCommands(TextIO textIO, TimeCryptClient timeCryptClient) {
        boolean exit = false;
        Command cmd;
        TextTerminal<?> terminal = textIO.getTextTerminal();

        while (!exit) {
            terminal.println();
            cmd = textIO.newEnumInputReader(Command.class).read("Command: ");

            switch (cmd) {
                case EXIT:
                    exit(timeCryptClient, textIO);
                    exit = true;
                    break;
                case CREATE_PROFILE:
                    createProfile(textIO);
                    break;
                case SELECT_PROFILE:
                    selectProfile(textIO);
                    break;
                case CREATE_STREAM:
                    createStream(textIO, timeCryptClient);
                    break;
                case SHOW_STREAM:
                    showStream(textIO, timeCryptClient);
                    break;
                case PERFORM_QUERY_WITH_SUB_INTERVALS:
                    performQuery(textIO, timeCryptClient, true);
                    break;
                case PERFORM_QUERY:
                    performQuery(textIO, timeCryptClient, false);
                    break;
                case LIST_STREAMS:
                    listStreams(textIO, timeCryptClient);
                    break;
                case ADD_DATA_POINT_TO_STREAM:
                    addDataPointToStream(textIO, timeCryptClient);
                    break;
                case GET_CHUNKS:
                    getChunks(textIO, timeCryptClient);
                    break;
                default:
                    terminal.println("Could understand command.");
            }
        }
    }

    private void performQuery(TextIO textIO, TimeCryptClient timeCryptClient, boolean hasSubIntervals) {
        TextTerminal<?> terminal = textIO.getTextTerminal();

        terminal.println();
        terminal.println("Perform query on stream");

        Stream stream = selectStream(textIO, timeCryptClient);
        if (stream != null) {

            if (stream.getLastWrittenChunkId() < 0) {
                terminal.println("The stream has no written chunks - can not query it.");
                return;
            }

            List<Interval> intervals;
            Query.SupportedOperation operation = textIO.newEnumInputReader(Query.SupportedOperation.class)
                    .read("Query to execute");

            boolean allowChunkScan = textIO.newBooleanInputReader().withDefaultValue(false)
                    .read("Allow chunk scans for this query");

            terminal.println("Do you want to perform a time-based or a chunk-based query?");
            terminal.println("0 - Chunk based");
            terminal.println("1 - Time based");

            long kind = textIO.newLongInputReader().withDefaultValue(0L).withMinVal(0L).withMaxVal(1L)
                    .read("Selection: ");

            long precision;
            try {
                if (kind == 0L) {

                    terminal.println("Min chunk ID: 0 Max chunk ID " + stream.getLastWrittenChunkId());
                    long chunkIdFrom = textIO.newLongInputReader().withMinVal(0L).withMaxVal(stream.getLastWrittenChunkId())
                            .read("First chunk ID to retrieve: ");

                    terminal.println("Min chunk ID: " + chunkIdFrom + " Max chunk ID " + stream.getLastWrittenChunkId());
                    long chunkIdTo = textIO.newLongInputReader().withMinVal(chunkIdFrom).withMaxVal(stream.getLastWrittenChunkId())
                            .read("Last chunk ID to retrieve: ");

                    if ((chunkIdTo - chunkIdFrom) < 1) {
                        terminal.println("First chunk ID is after last chunk ID - switching them");
                        long tmp = chunkIdFrom;
                        chunkIdFrom = chunkIdTo;
                        chunkIdTo = tmp;
                    }

                    if (hasSubIntervals) {
                        terminal.println("Granularity of sub-intervals in number of chunks");
                        if (allowChunkScan) {
                            terminal.println("You have chosen to allow chunk scans. Therefore you can perform " +
                                    "queries on arbitrary intervals. Note that the interval of the complete query " +
                                    "has to be a multiple of the chosen sub-intervals. ");
                            precision = textIO.newLongInputReader()
                                    .withMaxVal((chunkIdTo - chunkIdFrom) * stream.getChunkSize())
                                    .read("Granularity of the sub-intervals in milliseconds: ");

                        } else {
                            terminal.println("Minimum: 1 chunk, maximum " + (chunkIdTo - chunkIdFrom) + "chunks");
                            precision = textIO.newLongInputReader()
                                    .withMaxVal(chunkIdTo - chunkIdFrom)
                                    .withMinVal(stream.getChunkSize())
                                    .read("Granularity of the sub-intervals: ") * stream.getChunkSize();
                        }
                        intervals = timeCryptClient.performRangeQueryForChunkId(stream.getId(), chunkIdFrom, chunkIdTo,
                                operation, allowChunkScan, precision);
                    } else {
                        intervals = Collections.singletonList(timeCryptClient.performQueryForChunkId(stream.getId(),
                                chunkIdFrom, chunkIdTo, operation, allowChunkScan));
                    }

                } else {

                    terminal.println("Please enter the times in the format yyyy-m[m]-d[d] hh:mm:ss[.f…]");
                    Date start = new Date(Timestamp.valueOf(textIO.newStringInputReader()
                            .read("Start time: ")).getTime());

                    terminal.println("Min time: " + start + " max time " + TimeUtil.getChunkEndTime(stream,
                            stream.getLastWrittenChunkId()));
                    Date stop = new Date(Timestamp.valueOf(textIO.newStringInputReader()
                            .read("End time: ")).getTime());

                    if (hasSubIntervals) {
                        terminal.println("Granularity of sub-intervals in number of chunks");

                        if (allowChunkScan) {
                            terminal.println("You have chosen to allow chunk scans. Therefore you can perform " +
                                    "queries on arbitrary intervals. Note that the interval of the complete query " +
                                    "has to be a multiple of the chosen sub-intervals. ");
                            precision = textIO.newLongInputReader()
                                    .withMaxVal(stop.getTime() - start.getTime())
                                    .read("Granularity of the sub-intervals in milliseconds: ");

                        } else {
                            terminal.println("Minimum: 1 chunk, maximum " + (TimeUtil.getChunkIdAtTime(stream, stop) -
                                    TimeUtil.getChunkIdAtTime(stream, start)) + "chunks");
                            precision = textIO.newLongInputReader()
                                    .withMaxVal(TimeUtil.getChunkIdAtTime(stream, stop) -
                                            TimeUtil.getChunkIdAtTime(stream, start))
                                    .withMinVal(stream.getChunkSize())
                                    .read("Granularity of the sub-intervals: ") * stream.getChunkSize();
                        }
                        intervals = timeCryptClient.performRangeQuery(stream.getId(), start, stop,
                                operation, allowChunkScan, precision);
                    } else {
                        intervals = Collections.singletonList(timeCryptClient.performQuery(stream.getId(),
                                start, stop, operation, allowChunkScan));
                    }
                }
            } catch (QueryNeedsChunkScanException e) {
                terminal.printf("This Query can not be performed on this stream without chunk scan!",
                        e.getMessage());
                terminal.println();
                return;
            } catch (Exception e) {
                LOGGER.error("Error executing query", e);
                terminal.printf("Error executing query", e.getMessage());
                terminal.println();
                return;
            }

            for (Interval interval : intervals) {
                terminal.println(interval.toString());
            }
        }
    }

    private void getChunks(TextIO textIO, TimeCryptClient timeCryptClient) {
        TextTerminal<?> terminal = textIO.getTextTerminal();

        terminal.println();
        terminal.println("Retrieving chung");

        Stream stream = selectStream(textIO, timeCryptClient);
        if (stream != null) {
            if (stream.getLastWrittenChunkId() >= 0) {
                terminal.println();
                terminal.println("Min chunk ID: 0 Max chunk ID " + stream.getLastWrittenChunkId());
                long chunkIdFrom = textIO.newLongInputReader().withMinVal(0L).withMaxVal(stream.getLastWrittenChunkId())
                        .read("First chunkId to retrieve: ");

                terminal.println("Min chunk ID: " + chunkIdFrom + " Max chunk ID " + stream.getLastWrittenChunkId());
                long chunkIdTo = textIO.newLongInputReader().withMinVal(chunkIdFrom).withMaxVal(stream.getLastWrittenChunkId())
                        .read("Last chunkId to retrieve: ");
                List<Chunk> chunks;
                try {
                    chunks = timeCryptClient.getChunks(stream.getId(), chunkIdFrom, chunkIdTo);
                } catch (CouldNotReceiveException e) {
                    LOGGER.error("Error receiving chunk", e);
                    terminal.printf("Error receiving chunk", e.getMessage());
                    terminal.println();
                    return;
                } catch (Exception e) {
                    LOGGER.error("Error decrypting chunk", e);
                    terminal.printf("Error decrypting chunk", e.getMessage());
                    terminal.println();
                    return;
                }

                for (Chunk chunk : chunks) {
                    terminal.println("");
                    terminal.println(chunk.toString());
                }

            } else {
                terminal.println("Stream does not have any written chunks yet.");
            }
        }
    }

    private void exit(TimeCryptClient timeCryptClient, TextIO textIO) {
        TextTerminal<?> terminal = textIO.getTextTerminal();

        terminal.println();
        terminal.println("Shutting down... ");
        timeCryptClient.terminateAllHandlers();
        terminal.println("... shutdown complete.");
    }

    private void addDataPointToStream(TextIO textIO, TimeCryptClient timeCryptClient) {
        TextTerminal<?> terminal = textIO.getTextTerminal();

        terminal.println();
        terminal.println("Adding data point to stream.");
        Stream stream = selectStream(textIO, timeCryptClient);
        if (stream != null) {
            long value = textIO.newLongInputReader().read("Value at data point: ");
            try {
                timeCryptClient.addDataPointLiveToStream(stream.getId(), new DataPoint(new Date(), value));
            } catch (Exception e) {
                LOGGER.error("Error adding data point to chunk ", e);
                terminal.printf("... could not add data point to stream: " + e.getMessage());
                terminal.println();
            }
        }
    }

    private Stream selectStream(TextIO textIO, TimeCryptClient timeCryptClient) {
        TextTerminal<?> terminal = textIO.getTextTerminal();

        Map<Long, Stream> streams = listStreams(textIO, timeCryptClient);
        if (streams.size() < 1) {
            terminal.println("Could not find a stream for this profile.");
            return null;
        } else {
            terminal.println();
            terminal.println("Select stream");
            long streamId = textIO.newLongInputReader().withDefaultValue(0L).read("Stream ID: ");
            Stream stream = streams.get(streamId);

            if (stream == null) {
                terminal.println("Could not find a stream with the given StreamId.");
                return null;
            }
            return stream;
        }
    }

    private Map<Long, Stream> listStreams(TextIO textIO, TimeCryptClient timeCryptClient) {
        TextTerminal<?> terminal = textIO.getTextTerminal();

        terminal.println();

        Map<Long, Stream> streams = timeCryptClient.listStreams();
        if (streams.size() > 0) {
            terminal.println("Streams in this profile: ");
            for (long streamId : streams.keySet()) {
                terminal.printf("%d - %s \n", streamId, streams.get(streamId).getName());
            }
        } else {
            terminal.println("No streams in this profile yet!");
        }

        return streams;
    }

    private void showStream(TextIO textIO, TimeCryptClient timeCryptClient) {
        TextTerminal<?> terminal = textIO.getTextTerminal();

        Stream stream = selectStream(textIO, timeCryptClient);

        if (stream != null) {
            terminal.println("");
            terminal.println("  Id: " + stream.getId());
            terminal.println("  Name: " + stream.getName());
            terminal.println("  Description: " + stream.getDescription());
            terminal.println("  ChunkSize: " + stream.getChunkSize());
            terminal.println("  StartDate: " + stream.getStartDate());
            terminal.println("  LastWrittenChunkId: " + stream.getLastWrittenChunkId());
            terminal.println("  MetaData: ");
            for (StreamMetaData metaData : stream.getMetaData()) {
                terminal.printf("   * %s - %s \n", metaData.getType(), metaData.getEncryptionScheme());
            }

            terminal.println("  Sharing resolution levels: ");
            for (TimeUtil.Precision precision : stream.getResolutionLevels()) {
                terminal.printf("   * %s \n", precision);
            }
        }
    }

    private void createStream(TextIO textIO, TimeCryptClient timeCryptClient) {
        TextTerminal<?> terminal = textIO.getTextTerminal();

        terminal.println();
        terminal.println("Creating new stream");

        String name = textIO.newStringInputReader()
                .read("Stream name");

        terminal.println();

        String description = textIO.newStringInputReader()
                .read("Stream description");

        terminal.println();
        terminal.println("The precision will affect the speed of analytical queries since fast analytical queries");
        terminal.println("can only be computed with this granularity (for all other queries a full scan of all data");
        terminal.println("point would be needed.");

        TimeUtil.Precision precision = textIO.newEnumInputReader(TimeUtil.Precision.class)
                .withDefaultValue(TimeUtil.Precision.ONE_SECOND)
                .read("Stream precision (granularity / chunk size)");

        boolean more;
        List<StreamMetaData.MetadataType> metaDataTypes = new ArrayList<>();
        List<TimeUtil.Precision> resolutionLevels = new ArrayList<>();

        List<TimeUtil.Precision> possiblePrecisions = TimeUtil.Precision.getGreaterPrecisions(precision);

        if (possiblePrecisions.size() < 1) {
            more = false;
            terminal.println();
            terminal.println("Due to the coarse granularity of your stream the TimeCrypt client currently does not");
            terminal.println("support any higher precision levels for sharing");
        } else {
            more = textIO.newBooleanInputReader().withDefaultValue(true)
                    .read("Do you want to add precision levels for sharing?");
        }

        while (more) {
            TimeUtil.Precision newPrecision = getFromList(possiblePrecisions, "Add a precision level", textIO);
            resolutionLevels.add(newPrecision);
            possiblePrecisions.remove(newPrecision);
            if (possiblePrecisions.size() > 0) {
                more = textIO.newBooleanInputReader().withDefaultValue(true)
                        .read("Do you want to add more precision levels?");
            } else {
                more = false;
                terminal.println("No more precision levels available.");
            }
        }

        terminal.println("");

        terminal.println();
        StreamMetaData.MetadataEncryptionScheme encryptionScheme = textIO.newEnumInputReader(StreamMetaData.MetadataEncryptionScheme.class)
                .withDefaultValue(StreamMetaData.MetadataEncryptionScheme.LONG)
                .read("Stream meta data (digest) encryption scheme");

        List<StreamMetaData.MetadataType> possibleMetadata = new ArrayList<>(Arrays.asList(StreamMetaData.MetadataType.values()));

        terminal.println();
        if (possibleMetadata.size() < 1) {
            more = false;
            terminal.println("Did not find any metadata types ");
        } else {
            more = textIO.newBooleanInputReader().withDefaultValue(true)
                    .read("Do you want to add stream meta data for faster statistical queries?");
        }

        while (more) {
            StreamMetaData.MetadataType newMetaData = getFromList(possibleMetadata,
                    "Add a meta data (digest) type", textIO);
            metaDataTypes.add(newMetaData);
            possibleMetadata.remove(newMetaData);
            if (possibleMetadata.size() > 0) {
                more = textIO.newBooleanInputReader().withDefaultValue(true)
                        .read("Do you want to add more meta data?");
            } else {
                more = false;
                terminal.println("No more metadata types available.");
            }
        }

        terminal.println();
        terminal.println("Creating stream ...");
        long streamID;
        try {

            streamID = timeCryptClient.createStream(name, description, precision, resolutionLevels, metaDataTypes,
                    encryptionScheme, CliHelper.CONFIG_FOLDER + File.separator + profile.getProfileName() +
                            "_" + name + CliHelper.DEFAULT_CHUNK_STORE_FILE_ENDING);
        } catch (Exception e) {
            terminal.printf("... could not create stream: " + e.getMessage());
            terminal.println();
            return;
        }
        terminal.printf("... stream with ID %d was created.", streamID);
        terminal.println();
    }

    private <T> T getFromList(List<T> inputList, String prompt, TextIO textIO) {
        TextTerminal<?> terminal = textIO.getTextTerminal();

        int cnt = 1;
        for (Object item : inputList) {
            terminal.printf("\n[%d] %s", cnt, item.toString());
            cnt++;
        }

        terminal.println();
        int selection = textIO.newIntInputReader().withMinVal(1).withMaxVal(cnt).withDefaultValue(1).read(prompt);

        return inputList.get(selection - 1);
    }

    public enum Command {
        CREATE_STREAM, LIST_STREAMS, SHOW_STREAM,
        ADD_DATA_POINT_TO_STREAM, GET_CHUNKS,
        PERFORM_QUERY, PERFORM_QUERY_WITH_SUB_INTERVALS,
        CREATE_PROFILE, SELECT_PROFILE,
        EXIT,
    }
}

