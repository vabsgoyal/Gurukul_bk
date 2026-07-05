CREATE TABLE salary_structure (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    employee_id UUID NOT NULL,
    basic DECIMAL(12, 2) NOT NULL,
    allowances DECIMAL(12, 2) NOT NULL DEFAULT 0,
    deductions DECIMAL(12, 2) NOT NULL DEFAULT 0,
    effective_from DATE NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_salary_structure_employee FOREIGN KEY (employee_id) REFERENCES employee(id),
    CONSTRAINT fk_salary_structure_school FOREIGN KEY (school_id) REFERENCES school(id)
);

CREATE TABLE payroll_run (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    payroll_month INT NOT NULL,
    payroll_year INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_payroll_run UNIQUE (school_id, payroll_month, payroll_year),
    CONSTRAINT fk_payroll_run_school FOREIGN KEY (school_id) REFERENCES school(id)
);

CREATE TABLE payroll_line (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    run_id UUID NOT NULL,
    employee_id UUID NOT NULL,
    gross DECIMAL(12, 2) NOT NULL,
    deductions DECIMAL(12, 2) NOT NULL,
    net DECIMAL(12, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_payroll_line UNIQUE (run_id, employee_id),
    CONSTRAINT fk_payroll_line_run FOREIGN KEY (run_id) REFERENCES payroll_run(id),
    CONSTRAINT fk_payroll_line_employee FOREIGN KEY (employee_id) REFERENCES employee(id),
    CONSTRAINT fk_payroll_line_school FOREIGN KEY (school_id) REFERENCES school(id)
);

CREATE TABLE salary_payment (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    payroll_line_id UUID NOT NULL,
    transaction_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_salary_payment UNIQUE (payroll_line_id),
    CONSTRAINT fk_salary_payment_line FOREIGN KEY (payroll_line_id) REFERENCES payroll_line(id),
    CONSTRAINT fk_salary_payment_transaction FOREIGN KEY (transaction_id) REFERENCES financial_transaction(id),
    CONSTRAINT fk_salary_payment_school FOREIGN KEY (school_id) REFERENCES school(id)
);

CREATE TABLE payslip (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    payroll_line_id UUID NOT NULL,
    document_ref VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_payslip UNIQUE (payroll_line_id),
    CONSTRAINT fk_payslip_line FOREIGN KEY (payroll_line_id) REFERENCES payroll_line(id),
    CONSTRAINT fk_payslip_school FOREIGN KEY (school_id) REFERENCES school(id)
);
