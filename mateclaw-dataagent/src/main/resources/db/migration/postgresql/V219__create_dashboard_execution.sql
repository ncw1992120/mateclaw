CREATE TABLE dataagent_dashboard_execution (
    id BIGINT NOT NULL,
    execution_id VARCHAR(128) NOT NULL,
    dashboard_id BIGINT NOT NULL,
    workspace_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    parameters_json TEXT,
    output_json TEXT,
    output_ref_json TEXT,
    logs TEXT,
    error_message TEXT,
    return_code INTEGER,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_dataagent_dashboard_execution PRIMARY KEY (id),
    CONSTRAINT uk_dataagent_dashboard_execution_id UNIQUE (execution_id)
);
CREATE INDEX idx_dataagent_dashboard_execution_dashboard ON dataagent_dashboard_execution (dashboard_id, create_time);
CREATE INDEX idx_dataagent_dashboard_execution_workspace ON dataagent_dashboard_execution (workspace_id, create_time);
