CREATE TABLE event_participation_fee (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    event_id UUID NOT NULL,
    participant_type VARCHAR(20) NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_event_participation_fee UNIQUE (event_id, participant_type),
    CONSTRAINT fk_event_participation_fee_event FOREIGN KEY (event_id) REFERENCES school_event(id),
    CONSTRAINT fk_event_participation_fee_school FOREIGN KEY (school_id) REFERENCES school(id)
);

CREATE TABLE event_collection_payment (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    event_id UUID NOT NULL,
    payer_name VARCHAR(255) NOT NULL,
    payer_reference VARCHAR(255),
    amount DECIMAL(12, 2) NOT NULL,
    transaction_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_event_collection_payment_event FOREIGN KEY (event_id) REFERENCES school_event(id),
    CONSTRAINT fk_event_collection_payment_transaction FOREIGN KEY (transaction_id) REFERENCES financial_transaction(id),
    CONSTRAINT fk_event_collection_payment_school FOREIGN KEY (school_id) REFERENCES school(id)
);
