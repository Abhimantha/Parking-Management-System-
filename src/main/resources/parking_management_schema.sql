-- ==========================================
-- Parking Management System Database Schema
-- Corrected and Unified SQL Script
-- ==========================================

-- Drop existing tables (for clean setup)
DROP TABLE IF EXISTS audit_logs;
DROP TABLE IF EXISTS promotions;
DROP TABLE IF EXISTS pricing_rules;
DROP TABLE IF EXISTS payments;
DROP TABLE IF EXISTS reservations;
DROP TABLE IF EXISTS slots;
DROP TABLE IF EXISTS settings;
DROP TABLE IF EXISTS reports;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS system_settings;

-- ==========================================
-- Users Table
-- ==========================================
CREATE TABLE users (
                       id BIGINT PRIMARY KEY AUTO_INCREMENT,
                       username VARCHAR(255) NOT NULL,
                       email VARCHAR(255) NOT NULL UNIQUE,
                       password VARCHAR(255) NOT NULL,
                       role VARCHAR(20) NOT NULL,
                       suspended TINYINT(1) NOT NULL DEFAULT 0,
                       car_model VARCHAR(50) NULL,
                       car_plate VARCHAR(20) NULL,
                       nic_number VARCHAR(20) NULL
) ENGINE=InnoDB;

-- ==========================================
-- Slots Table
-- ==========================================
CREATE TABLE slots (
                       id BIGINT PRIMARY KEY AUTO_INCREMENT,
                       slot_number VARCHAR(50) NOT NULL UNIQUE,
                       level VARCHAR(50) NOT NULL,
                       type VARCHAR(20) NOT NULL,
                       status VARCHAR(20) NOT NULL,
                       lot VARCHAR(100) NULL,
                       map_row INT NULL,
                       map_col INT NULL,
                       col_index INT NOT NULL DEFAULT 0,
                       row_index INT NOT NULL DEFAULT 0,
                       lot_id BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB;

-- ==========================================
-- Reservations Table
-- ==========================================
CREATE TABLE reservations (
                              id BIGINT PRIMARY KEY AUTO_INCREMENT,
                              user_id BIGINT NOT NULL,
                              slot_id BIGINT NOT NULL,
                              start_time DATETIME NOT NULL,
                              end_time DATETIME NOT NULL,
                              status VARCHAR(20) NOT NULL,
                              promo_code VARCHAR(40) NULL,
                              CONSTRAINT fk_res_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
                              CONSTRAINT fk_res_slot FOREIGN KEY (slot_id) REFERENCES slots(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

-- ==========================================
-- Payments Table
-- ==========================================
CREATE TABLE payments (
                          id BIGINT PRIMARY KEY AUTO_INCREMENT,
                          reservation_id BIGINT NOT NULL,
                          amount DECIMAL(12,2) NOT NULL,
                          method VARCHAR(20) NOT NULL,
                          status VARCHAR(20) NOT NULL,
                          created_at DATETIME NOT NULL,
                          phase VARCHAR(50) NOT NULL DEFAULT 'INITIAL',
                          CONSTRAINT fk_pay_reservation FOREIGN KEY (reservation_id) REFERENCES reservations(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE UNIQUE INDEX uk_payments_res_phase ON payments (reservation_id, phase);
CREATE INDEX idx_payments_reservation ON payments (reservation_id);

-- ==========================================
-- Reports Table
-- ==========================================
CREATE TABLE reports (
                         id BIGINT PRIMARY KEY AUTO_INCREMENT,
                         title VARCHAR(255) NOT NULL,
                         period_start DATE NULL,
                         period_end DATE NULL,
                         generated_at DATETIME NOT NULL,
                         notes VARCHAR(2000) NULL
) ENGINE=InnoDB;

-- ==========================================
-- Settings Table
-- ==========================================
CREATE TABLE settings (
                          id BIGINT PRIMARY KEY AUTO_INCREMENT,
                          config_key VARCHAR(255) NOT NULL UNIQUE,
                          config_value VARCHAR(2000) NOT NULL,
                          description VARCHAR(500) NULL
) ENGINE=InnoDB;

-- ==========================================
-- System Settings Table
-- ==========================================
CREATE TABLE system_settings (
                                 id BIGINT PRIMARY KEY,
                                 enable_reservations       TINYINT(1) NOT NULL DEFAULT 1,
                                 enable_payments           TINYINT(1) NOT NULL DEFAULT 1,
                                 allow_cancellation        TINYINT(1) NOT NULL DEFAULT 1,
                                 require_cancellation_reason TINYINT(1) NOT NULL DEFAULT 1,
                                 refund_window_hours       INT NOT NULL DEFAULT 24,
                                 show_reports_to_drivers   TINYINT(1) NOT NULL DEFAULT 1
);

-- ==========================================
-- Promotions Table
-- ==========================================
CREATE TABLE promotions (
                            id           BIGINT PRIMARY KEY AUTO_INCREMENT,
                            name         VARCHAR(120) NOT NULL,
                            code         VARCHAR(64)  NULL,
                            scope        VARCHAR(20)  NOT NULL DEFAULT 'GLOBAL',
                            lot          VARCHAR(128) NULL,
                            level        VARCHAR(128) NULL,
                            slot_type    VARCHAR(20)  NULL,
                            slot_id      BIGINT       NULL,
                            time_band    VARCHAR(20)  NOT NULL DEFAULT 'EXACT_RANGE',
                            active_from  DATETIME     NULL,
                            active_until DATETIME     NULL,
                            start_hour   INT          NULL,
                            end_hour     INT          NULL,
                            type         VARCHAR(10)  NOT NULL,
                            value        DECIMAL(18,2) NOT NULL DEFAULT 0,
                            stackable    TINYINT(1)   NOT NULL DEFAULT 1,
                            active       TINYINT(1)   NOT NULL DEFAULT 1
);

CREATE INDEX idx_promotions_active   ON promotions(active);
CREATE INDEX idx_promotions_scope    ON promotions(scope);
CREATE INDEX idx_promotions_lot      ON promotions(lot);
CREATE INDEX idx_promotions_level    ON promotions(level);
CREATE INDEX idx_promotions_slotid   ON promotions(slot_id);
CREATE INDEX idx_promotions_code     ON promotions(code);

-- ==========================================
-- Pricing Rules Table (Dynamic Pricing & Promotions unified)
-- ==========================================
CREATE TABLE pricing_rules (
                               id            BIGINT PRIMARY KEY AUTO_INCREMENT,
                               rule_type     VARCHAR(16)  NOT NULL,
                               name          VARCHAR(120) NOT NULL,
                               description   TEXT NULL,
                               scope         VARCHAR(16)  NOT NULL DEFAULT 'GLOBAL',
                               lot           VARCHAR(128) NULL,
                               level         VARCHAR(128) NULL,
                               slot_number   VARCHAR(64)  NULL,
                               slot_type     VARCHAR(20)  NULL,
                               vehicle_type  VARCHAR(32)  NULL,
                               days_of_week  VARCHAR(32)  NULL,
                               start_time    TIME         NULL,
                               end_time      TIME         NULL,
                               start_date    DATE         NULL,
                               end_date      DATE         NULL,
                               effect_type   VARCHAR(16)  NOT NULL,
                               effect_value  DECIMAL(10,4) NOT NULL,
                               priority      INT          NOT NULL DEFAULT 100,
                               stackable     TINYINT(1)   NOT NULL DEFAULT 1,
                               active        TINYINT(1)   NOT NULL DEFAULT 1,
                               created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               updated_at    DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP
);

CREATE INDEX idx_pricing_rules_type_active ON pricing_rules(rule_type, active);
CREATE INDEX idx_pricing_rules_scope       ON pricing_rules(scope);
CREATE INDEX idx_pricing_rules_lot         ON pricing_rules(lot);
CREATE INDEX idx_pricing_rules_level       ON pricing_rules(level);
CREATE INDEX idx_pricing_rules_slot        ON pricing_rules(slot_number);
CREATE INDEX idx_pricing_rules_vehicle     ON pricing_rules(vehicle_type);
CREATE INDEX idx_pricing_rules_priority    ON pricing_rules(priority);

-- Normalize old data if migrating
UPDATE pricing_rules SET scope     = 'SLOT_TYPE' WHERE scope = 'TYPE';
UPDATE pricing_rules SET slot_type = NULL WHERE slot_type = 'ALL';

-- ==========================================
-- Audit Logs Table
-- ==========================================
CREATE TABLE audit_logs (
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

ALTER TABLE users ADD COLUMN phone VARCHAR(20) AFTER password;
ALTER TABLE lots
    MODIFY lot_number VARCHAR(50) NULL;
ALTER TABLE lots
    MODIFY level VARCHAR(50) NOT NULL DEFAULT 'L1';
ALTER TABLE lots
    MODIFY COLUMN type VARCHAR(50) NOT NULL DEFAULT 'GENERAL';
ALTER TABLE lots
    MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
CREATE TABLE levels (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        lot_id BIGINT NOT NULL,
                        name VARCHAR(100) NOT NULL,
                        CONSTRAINT fk_levels_lot FOREIGN KEY (lot_id) REFERENCES lots(id) ON DELETE CASCADE
) ENGINE=InnoDB;
ALTER TABLE slots
    ADD COLUMN price_rate DECIMAL(10,2) NOT NULL DEFAULT 0;

ALTER TABLE reservations
    ADD COLUMN cancel_reason VARCHAR(255) NULL AFTER status,
    ADD COLUMN canceled_at DATETIME NULL AFTER cancel_reason;


ALTER TABLE payments
    ADD COLUMN cancel_reason VARCHAR(255) NULL AFTER status,
    ADD COLUMN cancelled_at DATETIME NULL AFTER cancel_reason;

ALTER TABLE payments
    ADD COLUMN paid_at DATETIME NULL AFTER method;
ALTER TABLE pricing_rules
    CHANGE COLUMN start_date active_from DATETIME NULL,
    CHANGE COLUMN end_date active_until DATETIME NULL;

ALTER TABLE payments
    ADD COLUMN refund_due_at DATETIME NULL AFTER paid_at;
ALTER TABLE payments
    ADD COLUMN updated_at DATETIME NULL
        AFTER created_at;
