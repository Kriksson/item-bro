ALTER TABLE orders
    ADD COLUMN advice_id BIGINT;

ALTER TABLE orders
    ADD CONSTRAINT fk_orders_advice
    FOREIGN KEY (advice_id)
    REFERENCES advices(id);