-- Человекочитаемый номер заказа «24-08317» (год + порядковый номер) — его видят покупатель,
-- магазин, курьер и админка. Посылки одного заказа нумеруются 1..N (parcel_no).
create sequence order_number_seq start with 1;

alter table orders add column number varchar(16);
update orders set number = to_char(created_at, 'YY') || '-' || lpad(nextval('order_number_seq')::text, 5, '0');
alter table orders alter column number set not null;
alter table orders alter column number
    set default (to_char(now(), 'YY') || '-' || lpad(nextval('order_number_seq')::text, 5, '0'));
alter table orders add constraint uq_orders_number unique (number);

alter table suborders add column parcel_no smallint not null default 1;
