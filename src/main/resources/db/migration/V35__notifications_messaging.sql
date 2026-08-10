-- Notifications & Messaging: a super-admin-editable sender name, editable system
-- message templates (the previously hardcoded WhatsApp / in-app bodies), and an
-- outgoing history of super-admin broadcasts.

-- Generic key/value settings store. Currently holds the notification sender name.
CREATE TABLE app_settings (
    key        text PRIMARY KEY,
    value      text NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now()
);

INSERT INTO app_settings (key, value) VALUES ('sender_name', 'Center System');

-- Editable templates for the automatic system messages. `channel` is
-- 'whatsapp' | 'notification'. `title` is null for WhatsApp-only messages.
-- `variables` is a comma list of {placeholders} shown as an editor hint; the
-- code interpolates them at send time (and falls back to a baked-in default if
-- a row is ever missing).
CREATE TABLE message_templates (
    code       text PRIMARY KEY,
    name       text NOT NULL,
    channel    text NOT NULL,
    title      text,
    body       text NOT NULL,
    variables  text,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by text
);

INSERT INTO message_templates (code, name, channel, title, body, variables) VALUES
 ('student_verification', 'رمز تحقق تسجيل الطالب', 'whatsapp', NULL,
  E'يحاول شخص ما إنشاء حساب باسمك في تطبيق الطالب.\nرمز التحقق: {code}\nصالح لمدة {minutes} دقائق. إذا لم تكن أنت، تجاهل هذه الرسالة.',
  'code,minutes'),
 ('student_password_reset', 'إعادة تعيين كلمة مرور الطالب', 'whatsapp', NULL,
  E'طلب إعادة تعيين كلمة المرور لحسابك في تطبيق الطالب.\nرمز التحقق: {code}\nصالح لمدة {minutes} دقائق. إذا لم تكن أنت، تجاهل هذه الرسالة.',
  'code,minutes'),
 ('parent_password_reset', 'إعادة تعيين كلمة مرور ولي الأمر', 'whatsapp', NULL,
  E'طلب إعادة تعيين كلمة المرور لحسابك بصفتك ولي أمر.\nرمز التحقق: {code}\nصالح لمدة {minutes} دقائق. إذا لم تكن أنت، تجاهل هذه الرسالة.',
  'code,minutes'),
 ('parent_link_request', 'طلب ربط ولي أمر (إشعار)', 'notification', 'طلب ربط ولي أمر',
  'قام ({name}) بطلب ربط حسابه بحسابك بصفته ولي أمر. افتح الإعدادات ثم أولياء الأمور للموافقة على الطلب أو رفضه.',
  'name'),
 ('parent_link_approved_wa', 'تأكيد ربط ولي الأمر (واتساب)', 'whatsapp', NULL,
  E'تم التحقق من أنك ولي أمر الطالب ({name}) وتفعيل حسابك بنجاح.\nيمكنك الآن تسجيل الدخول إلى التطبيق.',
  'name'),
 ('parent_link_approved', 'قبول طلب الربط (إشعار)', 'notification', 'تم قبول الطلب',
  'تمت الموافقة على ربط حسابك بالطالب ({name}) بنجاح.',
  'name'),
 ('parent_link_rejected_wa', 'رفض التحقق (واتساب)', 'whatsapp', NULL,
  'تعذّر التحقق من صلتك بالطالب. يرجى التأكد من إدخال كود الطالب الصحيح.',
  ''),
 ('parent_link_rejected', 'رفض طلب الربط (إشعار)', 'notification', 'تم رفض الطلب',
  'تعذّر ربط حسابك بالطالب ({name}).',
  'name');

-- A log of every super-admin broadcast, for the History panel. `channel` records
-- whether it also went out over WhatsApp.
CREATE TABLE outgoing_messages (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    channel       text NOT NULL,
    sender        text NOT NULL,
    title         text,
    body          text NOT NULL,
    audience      text,
    recipients    integer NOT NULL DEFAULT 0,
    whatsapp_sent integer NOT NULL DEFAULT 0,
    created_at    timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX outgoing_messages_created_idx ON outgoing_messages (created_at DESC);
