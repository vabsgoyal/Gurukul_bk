CREATE TABLE event_budget (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    event_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_event_budget UNIQUE (event_id),
    CONSTRAINT fk_event_budget_event FOREIGN KEY (event_id) REFERENCES school_event(id),
    CONSTRAINT fk_event_budget_school FOREIGN KEY (school_id) REFERENCES school(id)
);

CREATE TABLE event_budget_line (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    budget_id UUID NOT NULL,
    description VARCHAR(500) NOT NULL,
    planned_amount DECIMAL(12, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_event_budget_line_budget FOREIGN KEY (budget_id) REFERENCES event_budget(id),
    CONSTRAINT fk_event_budget_line_school FOREIGN KEY (school_id) REFERENCES school(id)
);

CREATE TABLE event_expense_request (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    budget_line_id UUID NOT NULL,
    description VARCHAR(1000) NOT NULL,
    estimated_amount DECIMAL(12, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_event_expense_request_line FOREIGN KEY (budget_line_id) REFERENCES event_budget_line(id),
    CONSTRAINT fk_event_expense_request_school FOREIGN KEY (school_id) REFERENCES school(id)
);

CREATE TABLE event_vendor_payment (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    request_id UUID NOT NULL,
    vendor_id UUID NOT NULL,
    transaction_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_event_vendor_payment UNIQUE (request_id),
    CONSTRAINT fk_event_vendor_payment_request FOREIGN KEY (request_id) REFERENCES event_expense_request(id),
    CONSTRAINT fk_event_vendor_payment_vendor FOREIGN KEY (vendor_id) REFERENCES vendor(id),
    CONSTRAINT fk_event_vendor_payment_transaction FOREIGN KEY (transaction_id) REFERENCES financial_transaction(id),
    CONSTRAINT fk_event_vendor_payment_school FOREIGN KEY (school_id) REFERENCES school(id)
);
