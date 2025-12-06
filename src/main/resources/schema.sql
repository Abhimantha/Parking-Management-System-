-- Run this manually in MySQL to create tables (if you don't use Hibernate ddl-auto)

DROP TABLE IF EXISTS payments;
DROP TABLE IF EXISTS reservations;
DROP TABLE IF EXISTS slots;
DROP TABLE IF EXISTS settings;
DROP TABLE IF EXISTS reports;
DROP TABLE IF EXISTS users;

CREATE TABLE users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(255) NOT NULL,
  email VARCHAR(255) NOT NULL UNIQUE,
  password VARCHAR(255) NOT NULL,
  role VARCHAR(20) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE slots (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  slot_number VARCHAR(50) NOT NULL UNIQUE,
  level VARCHAR(50) NOT NULL,
  type VARCHAR(20) NOT NULL,
  status VARCHAR(20) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE reservations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  slot_id BIGINT NOT NULL,
  start_time DATETIME NOT NULL,
  end_time DATETIME NOT NULL,
  status VARCHAR(20) NOT NULL,
  CONSTRAINT fk_res_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
  CONSTRAINT fk_res_slot FOREIGN KEY (slot_id) REFERENCES slots(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE payments (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  reservation_id BIGINT NOT NULL UNIQUE,
  amount DECIMAL(12,2) NOT NULL,
  method VARCHAR(20) NOT NULL,
  status VARCHAR(20) NOT NULL,
  created_at DATETIME NOT NULL,
  CONSTRAINT fk_pay_reservation FOREIGN KEY (reservation_id) REFERENCES reservations(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE reports (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(255) NOT NULL,
  period_start DATE NULL,
  period_end DATE NULL,
  generated_at DATETIME NOT NULL,
  notes VARCHAR(2000) NULL
) ENGINE=InnoDB;

CREATE TABLE settings (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  config_key VARCHAR(255) NOT NULL UNIQUE,
  config_value VARCHAR(2000) NOT NULL,
  description VARCHAR(500) NULL
) ENGINE=InnoDB;

ALTER TABLE slots ADD COLUMN lot VARCHAR(100) NULL;
ALTER TABLE slots ADD COLUMN map_row INT NULL;
ALTER TABLE slots ADD COLUMN map_col INT NULL;

UPDATE slots SET lot='City Center', level='L1', map_row=1, map_col=1 WHERE slot_number='A1';
UPDATE slots SET lot='City Center', level='L1', map_row=1, map_col=2 WHERE slot_number='A2';
UPDATE slots SET lot='City Center', level='L1', map_row=1, map_col=3 WHERE slot_number='A3';
UPDATE slots SET lot='City Center', level='L1', map_row=2, map_col=1 WHERE slot_number='B1';

UPDATE slots SET lot='City Center', level='L1', map_row=2, map_col=2 WHERE slot_number='B2';
UPDATE slots SET lot='City Center', level='L1', map_row=2, map_col=3 WHERE slot_number='B3';
UPDATE slots SET lot='City Center', level='L2', map_row=1, map_col=1 WHERE slot_number='A1';
UPDATE slots SET lot='City Center', level='L2', map_row=1, map_col=2 WHERE slot_number='A2';
UPDATE slots SET lot='City Center', level='L2', map_row=1, map_col=3 WHERE slot_number='A3';
UPDATE slots SET lot='City Center', level='L2', map_row=2, map_col=1 WHERE slot_number='B1';
UPDATE slots SET lot='City Center', level='L2', map_row=2, map_col=2 WHERE slot_number='B2';
UPDATE slots SET lot='City Center', level='L2', map_row=2, map_col=3 WHERE slot_number='B3';

ALTER TABLE slots MODIFY col_index INT NOT NULL DEFAULT 0;
ALTER TABLE slots MODIFY row_index INT NOT NULL DEFAULT 0;
ALTER TABLE slots MODIFY lot_id INT NOT NULL DEFAULT 0;
ALTER TABLE users MODIFY COLUMN password VARCHAR(255) NOT NULL;


-- 2) Drop and recreate the FK with ON DELETE CASCADE
ALTER TABLE payments DROP FOREIGN KEY FKp8yh4sjt3u0g6aru1oxfh3o14;

ALTER TABLE payments
  ADD CONSTRAINT FKp8yh4sjt3u0g6aru1oxfh3o14
  FOREIGN KEY (reservation_id)
  REFERENCES reservations(id)
  ON DELETE CASCADE;


-- See current indexes to confirm the name
SHOW CREATE TABLE payments

-- Drop the unique index that blocks multiple rows per reservation
ALTER TABLE payments DROP INDEX `UKe7qdxh4fch1yfisduker8j6w2`;

-- Prevent duplicates per phase while allowing multiple phases
CREATE UNIQUE INDEX ux_payments_reservation_phase
  ON payments (reservation_id, phase);


-- 1) Remove the old unique index that forces one payment per reservation
ALTER TABLE payments DROP INDEX UKe7qdxh4fch1yfisduker8j6w2;


-- 3) Enforce at most one payment per (reservation, phase)
CREATE UNIQUE INDEX uk_payments_res_phase ON payments (reservation_id, phase);

-- (Optional but recommended) a normal lookup index
CREATE INDEX idx_payments_reservation ON payments (reservation_id);

UPDATE users
SET password = '{noop}admin123'
WHERE email = 'admin@local';

ALTER TABLE users ADD COLUMN suspended TINYINT(1) NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN car_model VARCHAR(50) NULL;
ALTER TABLE users ADD COLUMN car_plate VARCHAR(20) NULL;
ALTER TABLE users ADD COLUMN nic_number VARCHAR(20) NULL;


CREATE TABLE IF NOT EXISTS system_settings (
  id BIGINT PRIMARY KEY,
  enable_reservations       TINYINT(1) NOT NULL DEFAULT 1,
  enable_payments           TINYINT(1) NOT NULL DEFAULT 1,
  allow_cancellation        TINYINT(1) NOT NULL DEFAULT 1,
  require_cancellation_reason TINYINT(1) NOT NULL DEFAULT 1,
  refund_window_hours       INT NOT NULL DEFAULT 24,
  show_reports_to_drivers   TINYINT(1) NOT NULL DEFAULT 1
);



-- Now delete parents
DELETE FROM payments;
DELETE FROM reservations;

-- Verify
SELECT count(*) AS reservations_after FROM reservations;
SELECT count(*) AS payments_after     FROM payments;

