-- People come from SparkyTC: this is a copy of each account, refreshed on sign-in and from the admin user list.
CREATE TABLE users (
    id UUID PRIMARY KEY,                       -- the SparkyTC user ID
    username TEXT NOT NULL,
    display_name TEXT NOT NULL,
    role TEXT NOT NULL,                        -- ADMIN or TESTER, as in SparkyTC
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    synced_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX users_username ON users (lower(username));

CREATE TABLE locations (
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    is_archived BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX locations_name ON locations (lower(name));

-- A kind of device (VR headset, laptop...). POOL devices are lent to testers, ASSIGNED ones are given to a person.
CREATE TABLE device_types (
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    tag_prefix TEXT NOT NULL,                  -- asset tags are <prefix>-0001, <prefix>-0002...
    usage TEXT NOT NULL,                       -- POOL or ASSIGNED
    fields JSONB NOT NULL DEFAULT '[]',        -- extra fields of this type: [{key, label, kind, options, required}]
    coverage_field TEXT,                       -- the field holding the OS/firmware version, for the coverage matrix
    next_number INTEGER NOT NULL DEFAULT 1,
    is_archived BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX device_types_name ON device_types (lower(name));
CREATE UNIQUE INDEX device_types_prefix ON device_types (upper(tag_prefix));

CREATE TABLE devices (
    id UUID PRIMARY KEY,
    asset_tag TEXT NOT NULL,
    type_id BIGINT NOT NULL REFERENCES device_types (id),
    name TEXT NOT NULL,
    manufacturer TEXT,
    model TEXT NOT NULL,
    serial_number TEXT,
    status TEXT NOT NULL,                      -- AVAILABLE, ON_LOAN, ASSIGNED, IN_REPAIR, LOST, RETIRED
    location_id BIGINT REFERENCES locations (id),
    holder_id UUID REFERENCES users (id),      -- who has it (on loan or assigned)
    held_since TIMESTAMPTZ,
    due_at TIMESTAMPTZ,                        -- loans only
    purchase_date DATE,
    warranty_end DATE,
    notes TEXT,
    fields JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX devices_asset_tag ON devices (upper(asset_tag));
CREATE UNIQUE INDEX devices_serial ON devices (upper(serial_number)) WHERE serial_number IS NOT NULL;
CREATE INDEX devices_holder ON devices (holder_id);
CREATE INDEX devices_type ON devices (type_id);

-- Everything that happened to a device, never edited.
CREATE TABLE device_events (
    id BIGSERIAL PRIMARY KEY,
    device_id UUID NOT NULL REFERENCES devices (id),
    kind TEXT NOT NULL,                        -- CREATED, UPDATED, CHECKED_OUT, CHECKED_IN, ASSIGNED, UNASSIGNED, STATUS_CHANGED, IMPORTED
    actor_id UUID REFERENCES users (id),
    holder_id UUID REFERENCES users (id),
    from_status TEXT,
    to_status TEXT,
    note TEXT,
    details JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX device_events_device ON device_events (device_id, created_at DESC);

INSERT INTO locations (name) VALUES ('Main office');

INSERT INTO device_types (name, tag_prefix, usage, coverage_field, fields) VALUES
('VR headset', 'VR', 'POOL', 'system_version', '[
  {"key": "system_version", "label": "System version", "kind": "TEXT", "required": false},
  {"key": "controllers", "label": "Controller serials", "kind": "TEXT", "required": false},
  {"key": "mdm", "label": "MDM", "kind": "SELECT", "options": ["None", "VIVE Business+", "Other"], "required": false},
  {"key": "battery_health", "label": "Battery health", "kind": "SELECT", "options": ["Good", "Fair", "Poor"], "required": false},
  {"key": "accessories", "label": "Accessories", "kind": "TEXT", "required": false}
]'),
('Laptop', 'LAP', 'ASSIGNED', NULL, '[
  {"key": "hostname", "label": "Hostname", "kind": "TEXT", "required": false},
  {"key": "os", "label": "Operating system", "kind": "TEXT", "required": false},
  {"key": "cpu", "label": "CPU", "kind": "TEXT", "required": false},
  {"key": "ram_gb", "label": "RAM (GB)", "kind": "NUMBER", "required": false},
  {"key": "charger", "label": "Charger included", "kind": "BOOLEAN", "required": false}
]'),
('Monitor', 'MON', 'ASSIGNED', NULL, '[
  {"key": "size_in", "label": "Size (in)", "kind": "NUMBER", "required": false},
  {"key": "ports", "label": "Ports", "kind": "TEXT", "required": false}
]'),
('Peripheral', 'PER', 'ASSIGNED', NULL, '[
  {"key": "kind", "label": "Kind", "kind": "SELECT", "options": ["Keyboard", "Mouse", "Headset", "Dock", "Other"], "required": false}
]');
