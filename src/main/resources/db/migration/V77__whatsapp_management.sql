-- WhatsApp management: usage reporting, template variable mapping, template
-- sharing, and which template carries which message type.
--
-- Everything here exists so the BACKEND can answer, on its own, the three
-- questions the UI keeps asking: what did this workspace send and from which
-- number, what may this workspace send, and how is a message turned into an
-- approved template. None of that is derivable from the tables that came before.

-- ---------------------------------------------------------------------------
-- 1. The message log learns which number carried the message, and what it was.
--
-- Without instance_id the dashboard cannot say "this number sent 812 messages":
-- the log only ever recorded the recipient. template_name/template_category are
-- denormalised on purpose - a template can be renamed or deleted in WhatsApp
-- Manager, and a cost report for last month must not change when it is.
-- ---------------------------------------------------------------------------
alter table wa_message_log
    add column instance_id       uuid,
    add column template_name     varchar(512),
    add column template_category varchar(30);

create index wa_message_log_admin_instance_idx on wa_message_log (admin_id, instance_id);
create index wa_message_log_admin_phone_idx on wa_message_log (admin_id, phone);

-- ---------------------------------------------------------------------------
-- 2. Templates: what the account may use them for, and who may use them.
--
-- has_url_button is read from Meta, never set by hand - a template either has a
-- dynamic URL button or it does not, and sending a value for a button that is
-- static is rejected by Meta.
-- ---------------------------------------------------------------------------
alter table wa_cloud_template
    add column label          varchar(120),
    add column header_var     varchar(60),
    add column has_url_button boolean not null default false,
    add column shared_all     boolean not null default true;

-- One row per {{n}} in the template body, saying which system variable fills it.
-- var_position is the placeholder number, 1-based, exactly as Meta counts them.
create table wa_cloud_template_var (
    template_id  uuid        not null references wa_cloud_template (id) on delete cascade,
    -- Not "position": it is a SQL keyword, and the generated DML is unquoted.
    var_position int         not null,
    var_key      varchar(60) not null,
    primary key (template_id, var_position)
);

-- Only consulted when shared_all is false: the accounts allowed to use it.
-- admin_id carries no foreign key, matching whatsapp_config: these rows are
-- written by the super admin, who has no tenant, and a deleted teacher leaves a
-- harmless orphan rather than blocking the delete.
create table wa_cloud_template_grant (
    template_id uuid not null references wa_cloud_template (id) on delete cascade,
    admin_id    uuid not null,
    primary key (template_id, admin_id)
);

create index wa_cloud_template_grant_admin_idx on wa_cloud_template_grant (admin_id);

-- ---------------------------------------------------------------------------
-- 3. Which template carries which message type, per owner scope.
--
-- Same shape and same sentinel as whatsapp_responsibility: the all-zero UUID is
-- the platform scope, and a teacher with no row of their own falls back to it.
-- That fallback is what lets one approved template serve every teacher.
--
-- url_button_value overrides the sending number's own phone on a template whose
-- button is a wa.me link - the office line is not always the line that sends.
-- ---------------------------------------------------------------------------
create table wa_type_template (
    owner_admin_id   uuid        not null,
    code             varchar(40) not null,
    template_id      uuid        not null references wa_cloud_template (id) on delete cascade,
    url_button_value varchar(30),
    primary key (owner_admin_id, code)
);

create index wa_type_template_template_idx on wa_type_template (template_id);
