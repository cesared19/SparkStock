-- The SparkyTC project a device is for (e.g. VR), and the device's ID in the system it was imported from
-- (e.g. the Confluence database's ID column), which makes importing the same file again update instead of duplicate.
ALTER TABLE devices ADD COLUMN project_code TEXT;
ALTER TABLE devices ADD COLUMN external_id TEXT;
CREATE UNIQUE INDEX devices_external_id ON devices (external_id) WHERE external_id IS NOT NULL;
CREATE INDEX devices_project ON devices (upper(project_code));
