/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

// DROP KEYSPACE IF EXISTS "timecrypt";
//CREATE KEYSPACE IF NOT EXISTS "timecrypt" WITH replication = {'class': 'SimpleStrategy', 'replication_factor' : '1'};

USE "timecrypt";

CREATE TABLE treestore (
   username text,
   uid bigint,
   root_node bigint,
   root_content blob,
   root_version int,
   k int,
   PRIMARY KEY ((username, uid))
);

CREATE TABLE treeblockstore (
   username text,
   uid bigint,
   blockid bigint,
   version int,
   content blob,
   PRIMARY KEY ((username, uid), blockid) 
);

CREATE TABLE chunkstore (
   username text,
   uid bigint,
   chunk_key int,
   chunk blob,
   PRIMARY KEY ((username, uid), chunk_key)
);
