/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.crypto.prf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PRFFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(PRFFactory.class);

    private static final int PRF_AESNI = 1;
    private static final int PRF_OPENSSL = 2;
    private static final int PRF_JAVA = 3;

    private static int defaultPRF = -1;

    private static void findDefaultPRF() {
        if (defaultPRF != -1)
            return;
        LOGGER.info("Determining PRF.");
        try {
            IPRF out = new PRFAesNi();
            out.apply(new byte[16], 0);
            defaultPRF = PRF_AESNI;
        } catch (UnsatisfiedLinkError e1) {
            LOGGER.info("Could not use AES-NI PRF - falling back to OpenSSL.");
            try {
                IPRF out = new PRFAesOpenSSL();
                out.apply(new byte[16], 0);
                defaultPRF = PRF_OPENSSL;
            } catch (UnsatisfiedLinkError e2) {
                LOGGER.info("Could not use OpenSSL PRF - falling back to Java default.");
                defaultPRF = PRF_JAVA;
            }
        }
        LOGGER.info("PRF is " + defaultPRF);
    }

    public static IPRF getDefaultPRF() {
        findDefaultPRF();
        if (defaultPRF == PRF_AESNI) {
            return new PRFAesNi();
        } else if (defaultPRF == PRF_OPENSSL) {
            return new PRFAesOpenSSL();
        } else {
            return new PRFAes();
        }
    }
}
