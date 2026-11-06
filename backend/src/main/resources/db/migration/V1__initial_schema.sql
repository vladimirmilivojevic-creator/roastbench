-- RoastBench initial schema: green coffee inventory, roast profiles, roasts with curves.

create table location (
    id   bigserial primary key,
    name varchar(100) not null unique
);

create table lot (
    id           bigserial primary key,
    code         varchar(40)  not null unique,
    name         varchar(150) not null,
    country      varchar(80)  not null,
    region       varchar(120),
    producer     varchar(150),
    variety      varchar(120),
    process      varchar(80),
    altitude_m   integer check (altitude_m between 0 and 6000),
    crop_year    integer check (crop_year between 1990 and 2100),
    supplier     varchar(150),
    price_per_kg numeric(10, 2) check (price_per_kg >= 0),
    min_stock_kg numeric(10, 3) not null default 0 check (min_stock_kg >= 0),
    notes        text,
    archived     boolean      not null default false,
    created_at   timestamptz  not null default now()
);

create table profile (
    id                 bigserial primary key,
    name               varchar(120) not null unique,
    description        text,
    reference_roast_id bigint,
    created_at         timestamptz  not null default now()
);

create sequence roast_batch_seq start with 1;

create table roast (
    id                bigserial primary key,
    batch_no          bigint           not null unique default nextval('roast_batch_seq'),
    name              varchar(150)     not null,
    roasted_at        timestamptz      not null,
    machine           varchar(120),
    profile_id        bigint references profile (id) on delete set null,
    green_weight_g    numeric(10, 1),
    roasted_weight_g  numeric(10, 1),
    source_format     varchar(20)      not null,
    source_filename   varchar(255),
    notes             text,
    -- summary metrics, stored so lists can sort and filter without reading curves
    total_time_s      double precision not null,
    drop_estimated    boolean          not null,
    charge_bt         double precision not null,
    drop_bt           double precision not null,
    tp_time_s         double precision not null,
    tp_bt             double precision not null,
    dry_end_s         double precision,
    first_crack_s     double precision,
    dev_time_s        double precision,
    dtr_pct           double precision,
    loss_pct          double precision,
    created_at        timestamptz      not null default now()
);

alter table profile
    add constraint fk_profile_reference_roast foreign key (reference_roast_id) references roast (id) on delete set null;

create index idx_roast_roasted_at on roast (roasted_at desc);
create index idx_roast_profile on roast (profile_id);

-- time_s is seconds since charge; bt/et are degrees Celsius
create table roast_curve (
    roast_id bigint primary key references roast (id) on delete cascade,
    time_s   double precision[] not null,
    bt       double precision[] not null,
    et       double precision[]
);

create table roast_event (
    id      bigserial primary key,
    roast_id bigint           not null references roast (id) on delete cascade,
    type    varchar(30)      not null,
    time_s  double precision not null,
    unique (roast_id, type)
);

create table roast_lot (
    roast_id       bigint         not null references roast (id) on delete cascade,
    lot_id         bigint         not null references lot (id),
    green_weight_g numeric(10, 1) not null check (green_weight_g > 0),
    primary key (roast_id, lot_id)
);

-- Append-only stock ledger. Quantity is signed; the sign must match the movement type.
create table stock_movement (
    id          bigserial primary key,
    lot_id      bigint         not null references lot (id),
    location_id bigint         not null references location (id),
    type        varchar(30)    not null check (type in
        ('RECEIPT', 'ROAST_CONSUMPTION', 'ADJUSTMENT', 'TRANSFER_IN', 'TRANSFER_OUT', 'ROAST_REVERSAL')),
    quantity_kg numeric(10, 3) not null check (quantity_kg <> 0),
    roast_id    bigint references roast (id) on delete set null,
    reason      varchar(300),
    occurred_at timestamptz    not null default now(),
    created_at  timestamptz    not null default now(),
    constraint ck_movement_sign check (
        (type in ('RECEIPT', 'TRANSFER_IN', 'ROAST_REVERSAL') and quantity_kg > 0)
        or (type in ('ROAST_CONSUMPTION', 'TRANSFER_OUT') and quantity_kg < 0)
        or type = 'ADJUSTMENT')
);

create index idx_movement_lot_time on stock_movement (lot_id, occurred_at, id);
create index idx_movement_roast on stock_movement (roast_id);

create view lot_stock as
select lot_id, sum(quantity_kg) as stock_kg
from stock_movement
group by lot_id;
