-- Conversations: 1:1 (STAFF_STAFF / STAFF_STUDENT) and BOT (the user's private Helpdesk BOT chat).
-- Participants are polymorphic (Employee or Student), matching the credential table's pattern -
-- see conversation_participant, not a "party A / party B" column pair on this table.
CREATE TABLE conversation (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    type VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_conversation_school FOREIGN KEY (school_id) REFERENCES school(id)
);

CREATE TABLE conversation_participant (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    conversation_id UUID NOT NULL,
    owner_type VARCHAR(20) NOT NULL,
    owner_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_conversation_participant UNIQUE (conversation_id, owner_type, owner_id),
    CONSTRAINT fk_conversation_participant_conversation FOREIGN KEY (conversation_id) REFERENCES conversation(id),
    CONSTRAINT fk_conversation_participant_school FOREIGN KEY (school_id) REFERENCES school(id)
);

-- Fast "my conversations" lookup: find all conversations for a given (owner_type, owner_id).
CREATE INDEX idx_conversation_participant_owner ON conversation_participant(school_id, owner_type, owner_id);

-- sender_owner_type/sender_owner_id are NULL exactly when sender_kind = 'BOT' - Helpdesk BOT replies
-- have no polymorphic owner, only a human's messages do.
CREATE TABLE message (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    conversation_id UUID NOT NULL,
    sender_kind VARCHAR(10) NOT NULL,
    sender_owner_type VARCHAR(20),
    sender_owner_id UUID,
    content TEXT NOT NULL,
    sent_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_message_conversation FOREIGN KEY (conversation_id) REFERENCES conversation(id),
    CONSTRAINT fk_message_school FOREIGN KEY (school_id) REFERENCES school(id),
    CONSTRAINT chk_message_sender CHECK (
        (sender_kind = 'BOT' AND sender_owner_type IS NULL AND sender_owner_id IS NULL)
        OR (sender_kind = 'HUMAN' AND sender_owner_type IS NOT NULL AND sender_owner_id IS NOT NULL)
    )
);

-- Fast paginated history lookup: messages for a conversation, newest first.
CREATE INDEX idx_message_conversation_sent_at ON message(conversation_id, sent_at);
