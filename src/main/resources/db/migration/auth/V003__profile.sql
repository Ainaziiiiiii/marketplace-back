-- Профиль пользователя: имя (показывается как «Айзада К.») и выбранный город.
-- Вход клиентов и курьеров — только по SMS-коду; телефоны хранятся в формате +996XXXXXXXXX.
alter table users add column display_name varchar(120);
alter table users add column city_id uuid;
create index idx_users_created on users (created_at);
