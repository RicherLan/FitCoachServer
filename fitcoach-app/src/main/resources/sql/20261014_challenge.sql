-- MySQL 8; additive migration, never deletes existing training/payment tables.
CREATE TABLE IF NOT EXISTS challenge_session (
 id VARCHAR(36) PRIMARY KEY,
 user_id BIGINT NOT NULL,
 project VARCHAR(32) NOT NULL,
 rule_version VARCHAR(64) NOT NULL,
 mode VARCHAR(16) NOT NULL,
 level_no INT NOT NULL,
 difficulty INT NOT NULL,
 public_score BOOLEAN NOT NULL DEFAULT FALSE,
 started_ms BIGINT NOT NULL,
 week_start DATE NOT NULL,
 training_date DATE NOT NULL,
 state VARCHAR(16) NOT NULL,
 reps INT NOT NULL DEFAULT 0,
 completed BOOLEAN NOT NULL DEFAULT FALSE,
 elapsed_ms BIGINT NOT NULL DEFAULT 0,
 payload MEDIUMTEXT NULL,
 INDEX idx_challenge_user (user_id, started_ms),
 INDEX idx_challenge_board (project, rule_version, state, public_score, mode, week_start, reps)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS challenge_audit (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 session_id VARCHAR(36) NOT NULL,
 actor VARCHAR(100) NOT NULL,
 reason VARCHAR(500) NOT NULL,
 previous_state VARCHAR(16) NOT NULL,
 next_state VARCHAR(16) NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
