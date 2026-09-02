-- Plugin SDK: mate_plugin table

CREATE TABLE IF NOT EXISTS mate_plugin (
    id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    version VARCHAR(32) NOT NULL,
    plugin_type VARCHAR(32) NOT NULL,
    display_name VARCHAR(128),
    description TEXT,
    author VARCHAR(128),
    entrypoint VARCHAR(256) NOT NULL,
    jar_path VARCHAR(512),
    config_json TEXT NOT NULL,
    enabled BOOLEAN NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'LOADED',
    error_message TEXT,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL,
    deleted INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_plugin_name ON mate_plugin (name);
