CREATE TABLE sponsor (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    contact_phone VARCHAR(50),
    contact_email VARCHAR(255),
    pan VARCHAR(20),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_sponsor_school FOREIGN KEY (school_id) REFERENCES school(id)
);

CREATE TABLE sponsorship (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    sponsor_id UUID NOT NULL,
    purpose VARCHAR(50) NOT NULL,
    pledged_amount DECIMAL(12, 2) NOT NULL,
    fund_account_id UUID,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_sponsorship_sponsor FOREIGN KEY (sponsor_id) REFERENCES sponsor(id),
    CONSTRAINT fk_sponsorship_fund_account FOREIGN KEY (fund_account_id) REFERENCES fund_account(id),
    CONSTRAINT fk_sponsorship_school FOREIGN KEY (school_id) REFERENCES school(id)
);

CREATE TABLE sponsorship_payment (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    sponsorship_id UUID NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    transaction_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_sponsorship_payment_sponsorship FOREIGN KEY (sponsorship_id) REFERENCES sponsorship(id),
    CONSTRAINT fk_sponsorship_payment_transaction FOREIGN KEY (transaction_id) REFERENCES financial_transaction(id),
    CONSTRAINT fk_sponsorship_payment_school FOREIGN KEY (school_id) REFERENCES school(id)
);
