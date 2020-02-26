# TimeCrypt Crypto Primitives

The implementation of the TimeCrypt cryptographic primitives consists of several aspects:
- The implementation of the pseudorandom function (PRF) that is used for the key regression trees. The speed of the PRF is an relevant aspect the overall Speed of TimeCrypt therefore there are additional Implementations using OpenSSL or the native AES-NI CPU instruction. See the `ch.ethz.dsg.timecrypt.crypto.prf` package for details. TimeCrypt PRFs have to comply with the `IPRF` interface.
- The key regression itself is present in different versions in the `ch.ethz.dsg.timecrypt.crypto.keyRegression` package. Implementations of the key regression have to comply the `IKeyRegression` interface.
- The encryption and handling of the encrypted data in the package `ch.ethz.dsg.timecrypt.crypto.encryption`. This includes the encryption of the raw data chunks as well as the encryption of the additive meta data that is stored in the digests.

