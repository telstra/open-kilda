-- initial mysql schema

CREATE TABLE flow_event
(
    `id`                 integer primary key auto_increment,
    `flow_id`            text        not null,
    `task_id`            text        not null,
    `task_id_unique_key` char(100)   not null,
    `action`             text        not null,
    `unstructured`       text        not null,
    `time_create`        datetime(3) not null default now(3),
    `time_modify`        datetime(3),
    `event_time`         datetime(3),
    index `ix_event_time` (`event_time`),
    index `ix_flow_id` (`flow_id`(64)),
    unique `ix_task_id` (`task_id_unique_key`(100))
) engine=innodb;

CREATE TABLE flow_event_action
(
    `id`            integer primary key auto_increment,
    `flow_event_id` integer     not null,
    `action`        text        not null,
    `details`       text,
    `time_create`   datetime(3) not null default now(3),
    `time_modify`   datetime(3),
    `event_time`    datetime(3),
    index `ix_flow_event_id` (`flow_event_id`),
    foreign key `fk_flow_event_action_2_flow_event` (`flow_event_id`)
        REFERENCES flow_event (`id`)
        ON UPDATE CASCADE ON DELETE RESTRICT
) engine=innodb;

CREATE TABLE flow_event_dump
(
    `id`            integer primary key auto_increment,
    `flow_event_id` integer     not null,
    `kind`          varchar(64) not null,
    `unstructured`  text        not null,
    `time_create`   datetime(3) not null default now(3),
    `time_modify`   datetime(3),
    index `ix_flow_event_id` (`flow_event_id`),
    foreign key `fk_flow_event_dump_2_flow_event` (`flow_event_id`)
        REFERENCES flow_event (`id`)
        ON UPDATE CASCADE ON DELETE RESTRICT
) engine = innodb;

CREATE TABLE port_event
(
    `id`           char(36) primary key, -- uuid
    `switch_id`    char(23)    not null,
    `port_number`  integer     not null,
    `event`        varchar(64) not null,
    `unstructured` text        not null,
    `time_create`  datetime(3) not null default now(3),
    `time_modify`  datetime(3),
    `event_time`   datetime(3),
    index `ix_event_time` (`event_time`)
) engine = innodb;
