-- Who a notification is from. System messages read as "التطبيق"; once teachers
-- send notifications, a student linked to several teachers can tell them apart.
-- Additive and nullable: old rows keep NULL (the client falls back to the app).

alter table notifications add column if not exists sender text;
