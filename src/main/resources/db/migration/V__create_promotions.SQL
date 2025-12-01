-- Create table used by PromotionRepository/Promotion entity
-- Safe to run multiple times (IF NOT EXISTS). No FKs, minimal constraints.

CREATE TABLE IF NOT EXISTS promotions (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  name         VARCHAR(120) NOT NULL,
  code         VARCHAR(64)  NULL,

  -- Scope filters (match your engine's enums/logic)
  scope        VARCHAR(20)  NOT NULL DEFAULT 'GLOBAL',  -- GLOBAL | LOT | LEVEL | SLOT_TYPE | SLOT
  lot          VARCHAR(128) NULL,
  level        VARCHAR(128) NULL,
  slot_type    VARCHAR(20)  NULL,   -- COMPACT | LARGE | EV (DO NOT USE 'ALL')
  slot_id      BIGINT       NULL,

  -- Time band (aligns with TimeBandType)
  time_band    VARCHAR(20)  NOT NULL DEFAULT 'EXACT_RANGE', -- EXACT_RANGE | DAILY_WINDOW | WEEKDAY | WEEKEND
  active_from  DATETIME     NULL,
  active_until DATETIME     NULL,
  start_hour   INT          NULL,  -- 0-23 for DAILY_WINDOW
  end_hour     INT          NULL,

  -- Effect (aligns with DiscountType)
  type         VARCHAR(10)  NOT NULL,     -- PERCENT | FLAT
  value        DECIMAL(18,2) NOT NULL DEFAULT 0,

  stackable    BOOLEAN      NOT NULL DEFAULT TRUE,
  active       BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE INDEX  idx_promotions_active   ON promotions(active);
CREATE INDEX  idx_promotions_scope    ON promotions(scope);
CREATE INDEX  idx_promotions_lot      ON promotions(lot);
CREATE INDEX  idx_promotions_level    ON promotions(level);
CREATE INDEX  idx_promotions_slotid   ON promotions(slot_id);
CREATE INDEX  idx_promotions_code     ON promotions(code);
