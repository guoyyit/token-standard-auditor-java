CREATE TABLE IF NOT EXISTS checks (
    check_id        TEXT PRIMARY KEY,
    contract_key    TEXT NOT NULL,
    standard        TEXT NOT NULL,
    standard_version TEXT NOT NULL,
    status          TEXT NOT NULL,
    request_json    TEXT NOT NULL,
    report_json     TEXT,
    error_code      TEXT,
    error_message   TEXT,
    created_at      TEXT NOT NULL,
    completed_at    TEXT
);

CREATE INDEX IF NOT EXISTS idx_checks_contract_key ON checks (contract_key, status);
