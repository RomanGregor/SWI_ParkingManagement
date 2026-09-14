-- Initial schema for the car park reservation system.
-- Times are stored as timestamptz; the application works in UTC.

create table app_user (
    id                              uuid         primary key,
    email                           varchar(255) not null unique,
    full_name                       varchar(255) not null,
    role                            varchar(20)  not null,
    accessibility_permit_valid_until date
);

create table parking_spot (
    id        uuid        primary key,
    code      varchar(32) not null unique,
    zone_code varchar(32) not null,
    type      varchar(20) not null,
    status    varchar(20) not null
);

create table reservation (
    id            uuid        primary key,
    spot_id       uuid        not null references parking_spot (id),
    user_id       uuid        not null references app_user (id),
    vehicle_plate varchar(16) not null,
    vehicle_type  varchar(20) not null,
    starts_at     timestamptz not null,
    ends_at       timestamptz not null,
    status        varchar(20) not null,
    created_at    timestamptz not null,
    updated_at    timestamptz not null,
    version       bigint      not null default 0,
    constraint reservation_window_ordered check (starts_at < ends_at)
);

-- The overlap check filters by spot, status and window; this index serves exactly that query.
create index idx_reservation_spot_window on reservation (spot_id, status, starts_at, ends_at);
create index idx_reservation_user on reservation (user_id);
