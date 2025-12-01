-- Dynamic Pricing & Promotions (single table for both)
-- Safe: no FKs; minimal constraints; reasonable indexes.

create table if not exists pricing_rules (
  id            bigint primary key auto_increment,

  rule_type     varchar(16)  not null,            -- PRICING | PROMO
  name          varchar(120) not null,
  description   text null,

  scope         varchar(16)  not null default 'GLOBAL',  -- GLOBAL | LOT | LEVEL | SLOT | TYPE
  lot           varchar(128) null,
  level         varchar(128) null,
  slot_number   varchar(64)  null,
  vehicle_type  varchar(32)  null,

  days_of_week  varchar(32)  null,                -- e.g. "MON,TUE,SAT"
  start_time    time         null,
  end_time      time         null,
  start_date    date         null,
  end_date      date         null,

  effect_type   varchar(16)  not null,            -- MULTIPLY | PCT_OFF | FLAT_ADD | FLAT_OFF
  effect_value  decimal(10,4) not null,

  priority      int           not null default 100,
  stackable     boolean       not null default true,
  active        boolean       not null default true,

  created_at    datetime      not null default current_timestamp,
  updated_at    datetime      null on update current_timestamp
);

create index idx_pricing_rules_type_active on pricing_rules(rule_type, active);
create index  idx_pricing_rules_scope on pricing_rules(scope);
create index  idx_pricing_rules_lot on pricing_rules(lot);
create index  idx_pricing_rules_level on pricing_rules(level);
create index  idx_pricing_rules_slot on pricing_rules(slot_number);
create index  idx_pricing_rules_vehicle on pricing_rules(vehicle_type);
create index  idx_pricing_rules_priority on pricing_rules(priority);


UPDATE pricing_rules SET scope     = 'SLOT_TYPE' WHERE scope = 'TYPE';

UPDATE pricing_rules SET slot_type = NULL WHERE slot_type = 'ALL';


