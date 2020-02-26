/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt;

import ch.ethz.dsg.timecrypt.client.state.LocalTimeCryptProfile;
import ch.ethz.dsg.timecrypt.client.state.TimeCryptProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Common functions and variables for the CLI based TimeCrypt clients.
 */
public class CliHelper {
    public static final String KEY_STORE_PASSWORD_VARIABLE = "TIMECRYPT_KEYSTORE_PASSWORD";
    public static final String SERVER_HOST_VARIABLE = "TIMECRYPT_HOST";
    public static final String SERVER_PORT_VARIABLE = "TIMECRYPT_PORT";

    public static final String CONFIG_FOLDER = System.getProperty("user.home") + File.separator + ".TimeCryptClient";
    public static final String KEY_STORE_FILE = CONFIG_FOLDER + File.separator + "timecrypt.jks";

    public static final String DEFAULT_CHUNK_STORE_FILE_ENDING = ".chunkStore";
    public static final String DEFAULT_PROFILE_FILE_ENDING = ".profile";
    private static final Logger LOGGER = LoggerFactory.getLogger(CliHelper.class);

    public static void ensureConfigFolder(String configFolder) throws IOException {
        File configFolderReference = new File(configFolder);
        if (!configFolderReference.exists()) {
            boolean success = (new File(configFolder)).mkdirs();
            if (!success) {
                LOGGER.error("Mkdirs reported error.");
                throw new IOException("Could not create config folder.");
            } else {
                LOGGER.debug("Created config dir");
            }
            Files.setAttribute(FileSystems.getDefault().getPath(configFolder), "dos:hidden", true);
            LOGGER.debug("Changed Windows visibility of config dir to hidden");
        }

        configFolderReference = new File(configFolder);

        if (!configFolderReference.isDirectory()) {
            LOGGER.error("Config folder exists and is not a directory - can't handle that.");
            throw new IOException("Config folder " + configFolder + " exists but is not a directory.");
        }
    }

    public static List<TimeCryptProfile> getLocalProfiles(String configFolderName, String profileFileEnding) {
        List<TimeCryptProfile> profiles = new ArrayList<>();
        File configFolder = new File(configFolderName);

        File[] matchingFiles = configFolder.listFiles((dir, name) -> name.endsWith(profileFileEnding));

        if (matchingFiles != null) {
            for (File file : matchingFiles) {
                try {
                    TimeCryptProfile profile = LocalTimeCryptProfile.localProfileFromFile(file.getAbsolutePath());
                    profiles.add(profile);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return profiles;
    }

    public static String getStringFromEnv(String envVarName, String defaultValue) throws RuntimeException {
        String val = System.getenv(envVarName);
        if (val == null && defaultValue != null) {
            val = defaultValue;
        } else if (val == null) {
            throw new RuntimeException("Environment Variable " + envVarName +
                    " not defined and no default provided");
        }
        return val;
    }


    public static int getIntFromEnv(String envVarName, Integer defaultValue) throws RuntimeException {
        String str_val = System.getenv(envVarName);
        int val;
        if (str_val != null) {
            val = Integer.parseInt(str_val);
        } else if (defaultValue != null) {
            val = defaultValue;
        } else {
            throw new RuntimeException("Environment Variable " + envVarName +
                    " not defined and no default provided");
        }
        return val;
    }

}
