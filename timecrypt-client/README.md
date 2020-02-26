# TimeCrypt client

### Testbed

```
Usage: timecrypt-testbed [-hvV] [--[no-]create] [--[no-]delete]
                         [-c=<configFolderPath>] [-f=<frequency>]
                         [--keystore=<keystorePath>] [--max=<max>]
                         [--min=<min>] [-n=<numberOfChunks>]
                         [-p=<keyStorePassword>] [--port=<port>]
                         [--precision=<streamPrecision>]
                         [--profile=<profileName>] [--server=<server>]
                         [--stream-name=<streamPrefix>] [-t=<threads>]
                         [--user=<user>]
                         [--resolution-levels=<streamResulutionLevels>[,
                         <streamResulutionLevels>...]]...
                         [--stream-encryption=<metadataEncryptionSchemas>[,
                         <metadataEncryptionSchemas>...]]...
                         [--stream-metadata=<metadataTypes>[,
                         <metadataTypes>...]]...
Testbed for TimeCrypt. Can send measurements to a stream or multiple streams
after optionally creating them.
  -c, --config-folder=<configFolderPath>
                            The folder to expect all the config in. Default:
                              $HOME/.TimeCryptClient
  -f, --insert-frequency=<frequency>
                            The frequency to use for inserting chunks in
                              milliseconds. Default: 'testbed'
  -h, --help                Show this help message and exit.
      --keystore=<keystorePath>
                            The keystore to use for this execution. Default:
                              timecrypt.jks in the config folder.
      --max=<max>           The maximum value for inserting.
      --min=<min>           The minimum value for inserting.
  -n, --number=<numberOfChunks>
                            The number of chunks to insert before terminating
                              the program.
      --[no-]create         Create keystore, profile and stream(s) if they are
                              not existing? Default: yes
      --[no-]delete         Remove every resource that was created during
                              execution? Default: yes
  -p, --password=<keyStorePassword>
                            The keystore password to use for this execution.
                              WATCH OUT: Providing the password on the
                              commandline might be insecure. Consider providing
                              it by specifying the environment variable
                              TIMECRYPT_KEYSTORE_PASSWORD
      --port=<port>         The port to use for profile creation. WATCH OUT:
                              This variable gets ignored if a existing profile
                              is used! Default: '3000'
      --precision=<streamPrecision>
                            The size of the chunks for stream cration. This
                              also determines the minimal size of statistical
                              queries. Possible values: ONE_HUNDRED_MILLIS,
                              TWO_HUNDRED_FIFTY_MILLIS, FIVE_HUNDRED_MILLIS,
                              ONE_SECOND, TEN_SECONDS, ONE_MINUTE. WATCH OUT:
                              This variable gets ignored if a existing profile
                              is used! Default: Ten seconds.
      --profile=<profileName>
                            The profile to use for this execution. Existing
                              profiles will be searched in the config folder.
                              If there is no profile with this name a new
                              profile with this name will be created (if
                              creation is enabled) Default: testbed.
      --resolution-levels=<streamResulutionLevels>[,<streamResulutionLevels>...]
                            The precisions to use for additional resolution
                              levels for of stream sharing. Possible values:
                              ONE_HUNDRED_MILLIS, TWO_HUNDRED_FIFTY_MILLIS,
                              FIVE_HUNDRED_MILLIS, ONE_SECOND, TEN_SECONDS,
                              ONE_MINUTE. WATCH OUT: This variable gets ignored
                              if a existing profile is used! Default: All
                              precision levels that are greater than you chosen
                              precision level.
      --server=<server>     The server to use for profile creation. WATCH OUT:
                              This variable gets ignored if a existing profile
                              is used! Default: '127.0.0.1'
      --stream-encryption=<metadataEncryptionSchemas>[,
        <metadataEncryptionSchemas>...]
                            The encryption to use for the stream statistical
                              data Possible values: LONG, LONG_MAC,
                              BIG_INT_128, BIG_INT_128_MAC. WATCH OUT: This
                              variable gets ignored if a existing stream is
                              used! WATCH OUT: You can specify multiple
                              encryption types. In this case multiple streams
                              would be created. Default: LONG
      --stream-metadata=<metadataTypes>[,<metadataTypes>...]
                            The metadata to provide for the stream statistical
                              data Possible values: SUM, COUNT. WATCH OUT: This
                              variable gets ignored if a existing stream is
                              used! Default: All possible
      --stream-name=<streamPrefix>
                            The stream to use for inserting chunks. If there is
                              no stream with this name and create is enabled a
                              stream with this name will be created. This value
                              is used as a prefix if multiple will be created
                              due to the configuration options that are
                              selected! Default: 'testbed'
  -t, --threads=<threads>   The number of threads for inserting chunks per
                              stream.
      --user=<user>         The user to use for profile creation. WATCH OUT:
                              This variable gets ignored if a existing profile
                              is used! Default: 'testbed'
  -v, --[no-]verbose        Show verbose output.
  -V, --version             Print version information and exit.
```
