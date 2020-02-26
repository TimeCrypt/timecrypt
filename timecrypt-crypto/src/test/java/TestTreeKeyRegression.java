/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

import ch.ethz.dsg.timecrypt.crypto.keyRegression.*;
import ch.ethz.dsg.timecrypt.crypto.prf.IPRF;
import ch.ethz.dsg.timecrypt.crypto.prf.PRFFactory;
import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertArrayEquals;


public class TestTreeKeyRegression {

    @Test
    public void testTreeKeyRegressionShare_sharedTreeShouldDeriveSameKeys() {
        int depth = 20;
        int from = 2000;
        int to = 3000;
        IPRF aesni = PRFFactory.getDefaultPRF();
        TreeKeyRegression reg = (TreeKeyRegression) TreeKeyRegressionFactory.getNewDefaultTESTKeyRegression(aesni, depth);
        ArrayList<SeedNode> nodes = reg.revealSeeds(from, to);
        TreeKeyRegression reg2 = new TreeKeyRegression(false, aesni, nodes, depth, 2);
        for (int i = from; i <= to; i++) {
            assertArrayEquals(reg.getSeed(i), reg2.getSeed(i));
        }
    }

    @Test(expected = InvalidKeyDerivation.class)
    public void testTreeKeyRegressionShare_invalidAccess() {
        int depth = 10;
        int from = 100;
        int to = 200;
        IPRF aesni = PRFFactory.getDefaultPRF();
        TreeKeyRegression reg = (TreeKeyRegression) TreeKeyRegressionFactory.getNewDefaultTESTKeyRegression(aesni, depth);
        ArrayList<SeedNode> nodes = reg.revealSeeds(from, to);
        TreeKeyRegression reg2 = new TreeKeyRegression(false, aesni, nodes, depth, 2);
        reg2.getSeed(99);
    }

    @Test
    public void testTreeKeyRegressionOld_oldAndNewShouldBeEqual() {
        int depth = 20;
        int from = 2000;
        int to = 3000;
        IPRF aesni = PRFFactory.getDefaultPRF();
        TreeKeyRegression reg = (TreeKeyRegression) TreeKeyRegressionFactory.getNewDefaultTESTKeyRegression(aesni, depth);
        OldTreeKeyDerivation reg2 = new OldTreeKeyDerivation(aesni, new byte[16], depth, 2, 0);
        for (int i = from; i <= to; i++) {
            assertArrayEquals(reg.getSeed(i), reg2.getSeed(i));
        }
    }
}
