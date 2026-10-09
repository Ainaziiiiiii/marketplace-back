-- Колокольчик «2 новых»: время прочтения уведомления пользователем.
alter table notifications add column read_at timestamptz;
create index idx_notifications_unread on notifications (user_id) where read_at is null;
