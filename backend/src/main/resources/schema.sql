-- Oracle sequence + table for referral ids.
CREATE SEQUENCE IF NOT EXISTS referral_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS referral (
  id NUMBER(19) PRIMARY KEY,
  patient_reference VARCHAR2(30) NOT NULL,
  specialist_office VARCHAR2(100) NOT NULL,
  follow_up_date DATE NOT NULL,
  status VARCHAR2(20) NOT NULL,
  created_at TIMESTAMP
);
-- new stuff (extension spec)
CREATE SEQUENCE IF NOT EXISTS history_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS referral_status_history (
  id NUMBER(19) PRIMARY KEY,
  referral_id NUMBER(19) NOT NULL REFERENCES referral(id),
  from_status VARCHAR2(20) NOT NULL,
  to_status VARCHAR2(20) NOT NULL,
  changed_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS history_ref_idx ON referral_status_history(referral_id);
CREATE SEQUENCE IF NOT EXISTS contact_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS referral_contact_attempt (
  id NUMBER(19) PRIMARY KEY,
  referral_id NUMBER(19) NOT NULL REFERENCES referral(id),
  channel VARCHAR2(10) NOT NULL,
  outcome VARCHAR2(30) NOT NULL,
  recorded_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS contact_ref_idx ON referral_contact_attempt(referral_id);
CREATE SEQUENCE IF NOT EXISTS provider_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS provider (
  id NUMBER(19) PRIMARY KEY,
  name VARCHAR2(100) NOT NULL,
  normalized_name VARCHAR2(300) NOT NULL,
  CONSTRAINT provider_norm_uq UNIQUE (normalized_name)
);
-- old databases dont have this column so add it (H2 only syntax, oracle needs it by hand)
ALTER TABLE referral ADD COLUMN IF NOT EXISTS provider_id NUMBER(19) REFERENCES provider(id);
