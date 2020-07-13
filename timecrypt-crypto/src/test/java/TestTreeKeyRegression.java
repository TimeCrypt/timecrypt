/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

import ch.ethz.dsg.timecrypt.crypto.keyRegression.*;
import ch.ethz.dsg.timecrypt.crypto.prf.IPRF;
import ch.ethz.dsg.timecrypt.crypto.prf.PRFFactory;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;


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

    @Test
    public void testLabelTreeKeyRegression_labelAndNewShouldBeEqual() {
        int depth = 20;
        int from = 2000;
        int to = 3000;
        IPRF aesni = PRFFactory.getDefaultPRF();
        TreeKeyRegression reg = (TreeKeyRegression) TreeKeyRegressionFactory.getNewDefaultTESTKeyRegression(aesni, depth);
        LabelTreeKeyRegression reg2 = new LabelTreeKeyRegression(aesni, depth, new byte[16]);
        for (int i = from; i <= to; i++) {
            assertArrayEquals(reg.getSeed(i), reg2.getSeed(i));
        }
    }

    @Test
    public void testLabelTreeKeyRegressionShare_sharedNodesShouldBeEqual() {
        int depth = 20;
        int from = 2000;
        int to = 3000;
        IPRF aesni = PRFFactory.getDefaultPRF();
        TreeKeyRegression reg = (TreeKeyRegression) TreeKeyRegressionFactory.getNewDefaultTESTKeyRegression(aesni, depth);
        LabelTreeKeyRegression reg2 = new LabelTreeKeyRegression(aesni, depth, new byte[16]);
        ArrayList<SeedNode> nodes = reg.revealSeeds(from, to);
        List<SeedNode> nodes2 = reg2.constrainNodes(from, to + 1);

        for (SeedNode tn : nodes2) {
            boolean ok = false;
            for (SeedNode sn : nodes) {
                if (Arrays.equals(sn.getSeed(), tn.getSeed())) {
                    ok = true;
                    break;
                }
            }
            assertTrue(ok);
        }
    }

    @Test
    public void testLabelTreeKeyRegressionShare_sharedTreeShouldDeriveSameKeys() {
        int depth = 20;
        int from = 2000;
        int to = 3000;
        IPRF aesni = PRFFactory.getDefaultPRF();
        LabelTreeKeyRegression reg = new LabelTreeKeyRegression(aesni, depth, new byte[16]);
        List<SeedNode> nodes = reg.constrainNodes(from, to + 1);
        LabelTreeKeyRegression reg2 = new LabelTreeKeyRegression(aesni, depth, nodes);
        for (int i = from; i <= to; i++) {
            assertArrayEquals(reg.getSeed(i), reg2.getSeed(i));
        }
    }

    @Test(expected = InvalidKeyDerivation.class)
    public void testLabelTreeKeyRegressionShare_invalidAccess() {
        int depth = 10;
        int from = 100;
        int to = 200;
        IPRF aesni = PRFFactory.getDefaultPRF();
        LabelTreeKeyRegression reg = new LabelTreeKeyRegression(aesni, depth, new byte[16]);
        List<SeedNode> nodes = reg.constrainNodes(from, to + 1);
        LabelTreeKeyRegression reg2 = new LabelTreeKeyRegression(aesni, depth, nodes);
        reg2.getSeed(99);
    }
}
