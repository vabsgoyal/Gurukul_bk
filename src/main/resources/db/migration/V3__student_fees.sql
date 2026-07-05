CREATE TABLE fee_category (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_fee_category_school_code UNIQUE (school_id, code),
    CONSTRAINT fk_fee_category_school FOREIGN KEY (school_id) REFERENCES school(id)
);

CREATE TABLE fee_structure (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    class_section_id UUID NOT NULL,
    academic_year VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_fee_structure UNIQUE (school_id, class_section_id, academic_year),
    CONSTRAINT fk_fee_structure_school FOREIGN KEY (school_id) REFERENCES school(id),
    CONSTRAINT fk_fee_structure_class_section FOREIGN KEY (class_section_id) REFERENCES class_section(id)
);

CREATE TABLE fee_structure_line (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    fee_structure_id UUID NOT NULL,
    fee_category_id UUID NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_fee_structure_line UNIQUE (fee_structure_id, fee_category_id),
    CONSTRAINT fk_fee_structure_line_structure FOREIGN KEY (fee_structure_id) REFERENCES fee_structure(id),
    CONSTRAINT fk_fee_structure_line_category FOREIGN KEY (fee_category_id) REFERENCES fee_category(id),
    CONSTRAINT fk_fee_structure_line_school FOREIGN KEY (school_id) REFERENCES school(id)
);

CREATE TABLE student_fee_assessment (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    student_id UUID NOT NULL,
    academic_year VARCHAR(20) NOT NULL,
    total_due DECIMAL(12, 2) NOT NULL,
    total_paid DECIMAL(12, 2) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    due_date DATE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_student_fee_assessment UNIQUE (school_id, student_id, academic_year),
    CONSTRAINT fk_student_fee_assessment_student FOREIGN KEY (student_id) REFERENCES student(id),
    CONSTRAINT fk_student_fee_assessment_school FOREIGN KEY (school_id) REFERENCES school(id)
);

CREATE TABLE fee_payment (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    assessment_id UUID NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    transaction_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_fee_payment_assessment FOREIGN KEY (assessment_id) REFERENCES student_fee_assessment(id),
    CONSTRAINT fk_fee_payment_transaction FOREIGN KEY (transaction_id) REFERENCES financial_transaction(id),
    CONSTRAINT fk_fee_payment_school FOREIGN KEY (school_id) REFERENCES school(id)
);
