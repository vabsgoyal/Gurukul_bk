CREATE TABLE fund_account (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    account_type VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_fund_account_school_code UNIQUE (school_id, code),
    CONSTRAINT fk_fund_account_school FOREIGN KEY (school_id) REFERENCES school(id)
);

CREATE TABLE receipt_sequence (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    sequence_type VARCHAR(50) NOT NULL,
    academic_year VARCHAR(20) NOT NULL,
    last_value BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_receipt_sequence UNIQUE (school_id, sequence_type, academic_year),
    CONSTRAINT fk_receipt_sequence_school FOREIGN KEY (school_id) REFERENCES school(id)
);

CREATE TABLE financial_transaction (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    direction VARCHAR(20) NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    source_id UUID NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    payment_method VARCHAR(20),
    payment_reference VARCHAR(100),
    transaction_date DATE NOT NULL,
    receipt_number VARCHAR(100),
    status VARCHAR(20) NOT NULL,
    fund_account_id UUID,
    notes VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_financial_transaction_school FOREIGN KEY (school_id) REFERENCES school(id),
    CONSTRAINT fk_financial_transaction_fund_account FOREIGN KEY (fund_account_id) REFERENCES fund_account(id)
);

CREATE TABLE vendor (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    contact_phone VARCHAR(50),
    contact_email VARCHAR(255),
    bank_account VARCHAR(100),
    upi_id VARCHAR(100),
    address VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_vendor_school FOREIGN KEY (school_id) REFERENCES school(id)
);

CREATE TABLE employee (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    designation VARCHAR(100) NOT NULL,
    join_date DATE NOT NULL,
    bank_account VARCHAR(100),
    contact_phone VARCHAR(50),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_employee_school FOREIGN KEY (school_id) REFERENCES school(id)
);

CREATE TABLE school_event (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    event_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    inflow_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    outflow_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_school_event_school FOREIGN KEY (school_id) REFERENCES school(id)
);

INSERT INTO fund_account (id, school_id, code, name, account_type, created_at, updated_at)
VALUES (
    '22222222-2222-2222-2222-222222222222',
    '11111111-1111-1111-1111-111111111111',
    'GENERAL',
    'General Fund',
    'OPERATING',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);
