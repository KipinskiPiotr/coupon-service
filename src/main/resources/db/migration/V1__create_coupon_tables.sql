create table coupon
(
    id           bigint generated always as identity primary key,
    code         varchar(32) not null,
    max_uses     integer     not null,
    current_uses integer     not null default 0,
    country      varchar(2)  not null,
    created_at   timestamptz not null default now(),

    constraint uq_coupon_code unique (code),
    constraint ck_coupon_code_upper_case check (code = upper(code)),
    constraint ck_coupon_max_uses_positive check (max_uses > 0),
    constraint ck_coupon_current_uses_within_limit check (current_uses between 0 and max_uses),
    constraint ck_coupon_country_format check (country ~ '^[A-Z]{2}$')
);

create table coupon_usage
(
    id        bigint generated always as identity primary key,
    coupon_id bigint      not null,
    user_id   varchar(36) not null,
    country   varchar(2)  not null,
    used_at   timestamptz not null default now(),

    constraint fk_coupon_usage_coupon foreign key (coupon_id) references coupon (id),
    constraint uq_coupon_usage_coupon_user unique (coupon_id, user_id),
    constraint ck_coupon_usage_country_format check (country ~ '^[A-Z]{2}$')
);
