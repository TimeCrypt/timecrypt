package ch.ethz.dsg.timecrypt;

import ch.ethz.dsg.timecrypt.client.streamHandling.metaData.StreamMetaData;
import ch.ethz.dsg.timecrypt.protocol.EncryptionScheme;

import java.util.Arrays;
import java.util.List;

public class DefaultConfigs {

    public static List<StreamMetaData.MetadataType> getDefaultMetaDataConfig() {
        return  Arrays.asList(
                StreamMetaData.MetadataType.SUM,
                StreamMetaData.MetadataType.COUNT,
                StreamMetaData.MetadataType.SQUARE);
    }

    public static StreamMetaData.MetadataEncryptionScheme getDefaultEncryptionScheme() {
        return StreamMetaData.MetadataEncryptionScheme.LONG;
    }

    public static StreamMetaData.MetadataEncryptionScheme getDefaultAuthEncryptionScheme() {
        return StreamMetaData.MetadataEncryptionScheme.LONG_MAC;
    }
}
