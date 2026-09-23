-- Oracle sequence + table for referral ids.
CREATE SEQUENCE referral_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE referral (
  id NUMBER(19) PRIMARY KEY,
  patient_reference VARCHAR2(30) NOT NULL,
  specialist_office VARCHAR2(100) NOT NULL,
  follow_up_date DATE NOT NULL,
  status VARCHAR2(20) NOT NULL,
  created_at TIMESTAMP
);
