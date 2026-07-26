-- Real, working Principal login for the demo school, inserted directly rather than relying on an
-- ApplicationRunner (PrincipalPhoneBackfillSeeder still handles every other/future school - this
-- migration only exists because the demo school needed a guaranteed row, deploy-restart-independent).
-- Login: phone 9999999999 via OTP (code 1234), or password "Principal@9999" via
-- POST /api/v1/auth/login with username 9999999999.
INSERT INTO employee (
    id, school_id, name, designation, join_date, contact_phone, status, employee_type,
    created_at, updated_at
)
VALUES (
    '44444444-4444-4444-4444-444444444444',
    '11111111-1111-1111-1111-111111111111',
    'Principal',
    'Principal',
    CURRENT_DATE,
    '9999999999',
    'ACTIVE',
    'NON_TEACHING',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

INSERT INTO credential (
    id, school_id, owner_type, owner_id, username, password_hash, role, created_at, updated_at
)
VALUES (
    '55555555-5555-5555-5555-555555555555',
    '11111111-1111-1111-1111-111111111111',
    'EMPLOYEE',
    '44444444-4444-4444-4444-444444444444',
    '9999999999',
    '$2a$10$JUk9x9LMYVT951gsh4VZDOZrGxuGRX/DKqDwsX4fxMZsfArokccNe',
    'ADMIN',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);
