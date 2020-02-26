/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.crypto.prf;

import org.scijava.nativelib.NativeLibraryUtil;

public class PRFAesOpenSSL implements IPRF {

    static {
        NativeLibraryUtil.loadNativeLibrary(PRFAesOpenSSL.class, "sslaes-jni-wrapper");
    }

    private static native byte[] encrypt(byte[] key_oct, byte[] to_encrypt);

    private static native byte[] multiapply(byte[] key_oct, int[] path);

    @Override
    public byte[] apply(byte[] prfKey, byte[] input) {
        return encrypt(prfKey, input);
    }

    @Override
    public native byte[] apply(byte[] prfKey, int input);

    @Override
    public byte[] muliApply(byte[] prfKey, int[] inputs) {
        return multiapply(prfKey, inputs);
    }
}
