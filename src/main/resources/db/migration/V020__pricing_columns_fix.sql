-- ========= Add the columns your JPA entity needs (no IF NOT EXISTS; use information_schema) =========

-- Helper: add column if missing
-- Usage: set @col='active_from'; set @ddl='ALTER TABLE pricing_rules ADD COLUMN active_from DATETIME NULL';
--        (then run the 4 PREPARE/EXEC/DEALLOCATE lines)
SET @tbl := 'pricing_rules';

-- active_from



SET @ddl := 'ALTER TABLE pricing_rules ADD COLUMN active_from DATETIME NULL';
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME=@tbl AND COLUMN_NAME=@col);
SET @sql := IF(@exists=0, @ddl, 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- active_until
SET @col := 'active_until';
SET @ddl := 'ALTER TABLE pricing_rules ADD COLUMN active_until DATETIME NULL';
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME=@tbl AND COLUMN_NAME=@col);
SET @sql := IF(@exists=0, @ddl, 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- start_hour
SET @col := 'start_hour';
SET @ddl := 'ALTER TABLE pricing_rules ADD COLUMN start_hour INT NULL';
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME=@tbl AND COLUMN_NAME=@col);
SET @sql := IF(@exists=0, @ddl, 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- end_hour
SET @col := 'end_hour';
SET @ddl := 'ALTER TABLE pricing_rules ADD COLUMN end_hour INT NULL';
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME=@tbl AND COLUMN_NAME=@col);
SET @sql := IF(@exists=0, @ddl, 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- time_band (NOT NULL with default)
SET @col := 'time_band';
SET @ddl := "ALTER TABLE pricing_rules ADD COLUMN time_band VARCHAR(20) NOT NULL DEFAULT 'DAILY_WINDOW'";
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME=@tbl AND COLUMN_NAME=@col);
SET @sql := IF(@exists=0, @ddl, 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- slot_type
SET @col := 'slot_type';
SET @ddl := 'ALTER TABLE pricing_rules ADD COLUMN slot_type VARCHAR(20) NULL';
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME=@tbl AND COLUMN_NAME=@col);
SET @sql := IF(@exists=0, @ddl, 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- slot_id
SET @col := 'slot_id';
SET @ddl := 'ALTER TABLE pricing_rules ADD COLUMN slot_id BIGINT NULL';
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME=@tbl AND COLUMN_NAME=@col);
SET @sql := IF(@exists=0, @ddl, 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- type (entity DiscountType) NOT NULL default 'PERCENT'
SET @col := 'type';
SET @ddl := "ALTER TABLE pricing_rules ADD COLUMN `type` VARCHAR(10) NOT NULL DEFAULT 'PERCENT'";
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME=@tbl AND COLUMN_NAME=@col);
SET @sql := IF(@exists=0, @ddl, 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- value (entity amount) NOT NULL default 0
SET @col := 'value';
SET @ddl := 'ALTER TABLE pricing_rules ADD COLUMN `value` DECIMAL(18,2) NOT NULL DEFAULT 0';
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME=@tbl AND COLUMN_NAME=@col);
SET @sql := IF(@exists=0, @ddl, 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- priority (if you already had it, this will no-op)
SET @col := 'priority';
SET @ddl := 'ALTER TABLE pricing_rules ADD COLUMN priority INT NOT NULL DEFAULT 100';
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME=@tbl AND COLUMN_NAME=@col);
SET @sql := IF(@exists=0, @ddl, 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ========= Light backfill so Hibernate + engine have sane values (non-destructive) =========

-- If time_band added, prefer EXACT_RANGE when a date window exists
UPDATE pricing_rules
   SET time_band = 'EXACT_RANGE'
 WHERE (start_date IS NOT NULL OR end_date IS NOT NULL)
   AND (time_band IS NULL OR time_band = 'DAILY_WINDOW');

-- Populate active_from/active_until from date range if missing
UPDATE pricing_rules
   SET active_from = TIMESTAMP(start_date, '00:00:00')
 WHERE active_from IS NULL AND start_date IS NOT NULL;

UPDATE pricing_rules
   SET active_until = TIMESTAMP(end_date, '23:59:59')
 WHERE active_until IS NULL AND end_date IS NOT NULL;

-- Map old effect_* into entity-friendly type/value if not already set
-- MULTIPLY (e.g., 1.10 -> +10%)
UPDATE pricing_rules
   SET `type`='PERCENT', `value`=ROUND((effect_value - 1.0) * 100, 2)
 WHERE effect_type='MULTIPLY' AND (`value` IS NULL OR `value`=0);

-- PCT_OFF (e.g., 10 -> -10%)
UPDATE pricing_rules
   SET `type`='PERCENT', `value`=ROUND(-effect_value, 2)
 WHERE effect_type='PCT_OFF' AND (`value` IS NULL OR `value`=0);

-- FLAT_ADD
UPDATE pricing_rules
   SET `type`='FLAT', `value`=ROUND(effect_value, 2)
 WHERE effect_type='FLAT_ADD' AND (`value` IS NULL OR `value`=0);

-- FLAT_OFF
UPDATE pricing_rules
   SET `type`='FLAT', `value`=ROUND(-effect_value, 2)
 WHERE effect_type='FLAT_OFF' AND (`value` IS NULL OR `value`=0);


ALTER TABLE reservations
  ADD COLUMN promo_code VARCHAR(40) NULL;


CREATE TABLE IF NOT EXISTS audit_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  ts TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  action VARCHAR(64) NOT NULL,
  username VARCHAR(255),
  roles VARCHAR(255),
  ip VARCHAR(45),
  method VARCHAR(16),
  path VARCHAR(512),
  status INT,
  user_agent VARCHAR(512),
  message VARCHAR(1024)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_audit_ts     ON audit_logs (ts);
CREATE INDEX idx_audit_user   ON audit_logs (username);
CREATE INDEX idx_audit_action ON audit_logs (action);
CREATE INDEX idx_audit_path   ON audit_logs (path);

