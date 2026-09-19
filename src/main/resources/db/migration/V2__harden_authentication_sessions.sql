ALTER TABLE authentication_session
    ADD COLUMN last_activity_at TIMESTAMPTZ;

UPDATE authentication_session
SET last_activity_at = created_at;

ALTER TABLE authentication_session
    ALTER COLUMN last_activity_at SET NOT NULL;

ALTER TABLE authentication_session
    ADD CONSTRAINT ck_authentication_session_last_activity
        CHECK (last_activity_at >= created_at AND last_activity_at < expires_at);

ALTER TABLE authentication_login_throttle
DROP CONSTRAINT ck_authentication_login_throttle_scope;

ALTER TABLE authentication_login_throttle
ALTER COLUMN scope TYPE VARCHAR(32);

ALTER TABLE authentication_login_throttle
    ADD CONSTRAINT ck_authentication_login_throttle_scope
        CHECK (scope IN ('ACCOUNT', 'SOURCE', 'REGISTRATION_SOURCE'));