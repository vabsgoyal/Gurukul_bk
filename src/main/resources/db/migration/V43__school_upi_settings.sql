ALTER TABLE school ADD COLUMN upi_vpa VARCHAR(255);
ALTER TABLE school ADD COLUMN upi_payee_name VARCHAR(255);

CREATE TABLE fee_upi_qr_log (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    assessment_id UUID NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    reference_id VARCHAR(40) NOT NULL,
    upi_vpa VARCHAR(255) NOT NULL,
    payee_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_fee_upi_qr_log_reference UNIQUE (reference_id),
    CONSTRAINT fk_fee_upi_qr_log_school FOREIGN KEY (school_id) REFERENCES school(id),
    CONSTRAINT fk_fee_upi_qr_log_assessment FOREIGN KEY (assessment_id) REFERENCES student_fee_assessment(id)
);
