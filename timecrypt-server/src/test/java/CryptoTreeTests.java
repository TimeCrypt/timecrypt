/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

import ch.ethz.dsg.timecrypt.crypto.TwoBigintMACNodeContent;
import org.junit.Test;

import java.math.BigInteger;

import static org.junit.Assert.assertEquals;

public class CryptoTreeTests {

    @Test
    public void checkMACContent() {
        TwoBigintMACNodeContent cont = new TwoBigintMACNodeContent(BigInteger.valueOf(3455345345353453445L), BigInteger.valueOf(3455345345353453445L));
        cont.mergeOther(cont);
        cont.mergeOther(cont);

        byte[] before = cont.encode();
        TwoBigintMACNodeContent afterC = (TwoBigintMACNodeContent) TwoBigintMACNodeContent.decode(before);

        assertEquals(cont.getStringRepresentation(), afterC.getStringRepresentation());
    }


}
