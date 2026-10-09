-- Карточка магазина для витрины и курьера: описание («о магазине»), логотип, как пройти к точке забора
-- и координаты точки забора (карта курьера). Заполняются в кабинете магазина.
alter table shops add column description varchar(300);
alter table shops add column logo_url varchar(500);
alter table shops add column pickup_instructions varchar(300);
alter table shops add column lat double precision;
alter table shops add column lon double precision;
