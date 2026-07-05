CREATE TABLE infra_expense_category (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_infra_expense_category UNIQUE (school_id, code),
    CONSTRAINT fk_infra_expense_category_school FOREIGN KEY (school_id) REFERENCES school(id)
);

INSERT INTO infra_expense_category (id, school_id, code, name, created_at, updated_at)
VALUES
    ('33333333-3333-3333-3333-333333333331', '11111111-1111-1111-1111-111111111111', 'LIBRARY', 'Library', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('33333333-3333-3333-3333-333333333332', '11111111-1111-1111-1111-111111111111', 'LAB', 'Laboratory', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111', 'SPORTS', 'Sports Equipment', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('33333333-3333-3333-3333-333333333334', '11111111-1111-1111-1111-111111111111', 'FURNITURE', 'Furniture', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('33333333-3333-3333-3333-333333333335', '11111111-1111-1111-1111-111111111111', 'CLASSROOM', 'Classroom Maintenance', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('33333333-3333-3333-3333-333333333336', '11111111-1111-1111-1111-111111111111', 'IT', 'IT Equipment', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

CREATE TABLE infra_expense_request (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    category_id UUID NOT NULL,
    description VARCHAR(1000) NOT NULL,
    estimated_amount DECIMAL(12, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_infra_expense_request_category FOREIGN KEY (category_id) REFERENCES infra_expense_category(id),
    CONSTRAINT fk_infra_expense_request_school FOREIGN KEY (school_id) REFERENCES school(id)
);

CREATE TABLE infra_purchase_record (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    request_id UUID NOT NULL,
    vendor_id UUID NOT NULL,
    invoice_number VARCHAR(100) NOT NULL,
    actual_amount DECIMAL(12, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_infra_purchase_record UNIQUE (request_id),
    CONSTRAINT fk_infra_purchase_record_request FOREIGN KEY (request_id) REFERENCES infra_expense_request(id),
    CONSTRAINT fk_infra_purchase_record_vendor FOREIGN KEY (vendor_id) REFERENCES vendor(id),
    CONSTRAINT fk_infra_purchase_record_school FOREIGN KEY (school_id) REFERENCES school(id)
);

CREATE TABLE infra_vendor_payment (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    purchase_id UUID NOT NULL,
    transaction_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_infra_vendor_payment UNIQUE (purchase_id),
    CONSTRAINT fk_infra_vendor_payment_purchase FOREIGN KEY (purchase_id) REFERENCES infra_purchase_record(id),
    CONSTRAINT fk_infra_vendor_payment_transaction FOREIGN KEY (transaction_id) REFERENCES financial_transaction(id),
    CONSTRAINT fk_infra_vendor_payment_school FOREIGN KEY (school_id) REFERENCES school(id)
);
