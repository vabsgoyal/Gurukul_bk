CREATE TABLE payment_attempt (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    assessment_id UUID NOT NULL,
    transaction_ref VARCHAR(64) NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    status VARCHAR(20) NOT NULL,
    upi_transaction_id VARCHAR(255),
    approval_ref_no VARCHAR(255),
    response_code VARCHAR(255),
    raw_response VARCHAR(2000),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_payment_attempt_transaction_ref UNIQUE (transaction_ref),
    CONSTRAINT fk_payment_attempt_assessment FOREIGN KEY (assessment_id) REFERENCES student_fee_assessment(id)
);

CREATE INDEX idx_payment_attempt_assessment ON payment_attempt(assessment_id, school_id);
