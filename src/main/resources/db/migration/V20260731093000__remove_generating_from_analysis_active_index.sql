DROP INDEX uk_analysis_active;

CREATE UNIQUE INDEX uk_analysis_active ON analysis (user_id)
    WHERE status IN ('UPLOADING', 'ANALYZING');
