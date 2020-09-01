CREATE TABLE IF NOT EXISTS homequeue
(
    id   binary(16)  NOT NULL PRIMARY KEY,
    name varchar(16) NOT NULL
);

CREATE TABLE IF NOT EXISTS homes
(
    id         varchar(16) NOT NULL,
    playeruuid binary(16)  NOT NULL,
    server     varchar(32) NOT NULL,
    PRIMARY KEY (id, playeruuid)
);

CREATE TABLE IF NOT EXISTS syncedcommands
(
    id        int PRIMARY KEY AUTO_INCREMENT,
    command   varchar(32) NOT NULL,
    arguments varchar(64) NOT NULL,
    timestamp datetime(6) NOT NULL
);

CREATE TABLE IF NOT EXISTS warpqueue
(
    id   binary(16)  NOT NULL PRIMARY KEY,
    name varchar(16) NOT NULL
);

CREATE TABLE IF NOT EXISTS warps
(
    id          varchar(32) NOT NULL,
    description varchar(32) NOT NULL DEFAULT '',
    category    varchar(32) NOT NULL DEFAULT '',
    cost        double      NOT NULL DEFAULT 0,
    server      varchar(32) NOT NULL
);