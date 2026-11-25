-- The model and version combinations a SparkyTC project has to be tested on, for the coverage matrix.
-- A target without version is covered by any version of the model.
CREATE TABLE coverage_targets (
    id BIGSERIAL PRIMARY KEY,
    project_code TEXT NOT NULL,
    model TEXT NOT NULL,
    version TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX coverage_targets_unique ON coverage_targets (upper(project_code), lower(model), coalesce(lower(version), ''));
