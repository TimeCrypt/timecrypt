package ch.ethz.dsg.timecrypt.crypto.keymanagement;

public class CachedKeys {
    byte[] k1 = null;
    byte[] k2 = null;

    public CachedKeys(byte[] k1, byte[] k2) {
        this.k1 = k1;
        this.k2 = k2;
    }

    public CachedKeys() {
    }

    public byte[] getK1() {
        return k1;
    }

    public byte[] getK2() {
        return k2;
    }

    public void setK1(byte[] k1) {
        this.k1 = k1;
    }

    public void setK2(byte[] k2) {
        this.k2 = k2;
    }

    public boolean containsKeys() {
        return this.k1 != null && this.k2 != null;
    }
}
