-- Create table for Summary Reservations (session → DB)
create table if not exists summery_reservation (
    id bigserial primary key,
    external_id bigint not null,
    user varchar(200),
    hours integer,
    status varchar(20) not null,
    date date
);

create index if not exists idx_summery_reservation_external on summery_reservation(external_id);

