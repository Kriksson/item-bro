ALTER TABLE order_messages
    ADD COLUMN message_type VARCHAR(10) NOT NULL DEFAULT 'USER',
    ADD COLUMN event_type VARCHAR(40);

ALTER TABLE order_messages
    ALTER COLUMN client_message_id DROP NOT NULL;

ALTER TABLE order_messages
    ADD CONSTRAINT chk_order_messages_type
        CHECK (
            (
                message_type = 'USER'
                    AND client_message_id IS NOT NULL
                    AND event_type IS NULL
                )
                OR
            (
                message_type = 'SYSTEM'
                    AND client_message_id IS NULL
                    AND event_type IS NOT NULL
                )
            );