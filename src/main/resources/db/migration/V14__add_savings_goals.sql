CREATE TABLE IF NOT EXISTS savings_goals (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    month_key VARCHAR(6) NOT NULL,
    target_amount NUMERIC(15, 2) NOT NULL,
    CONSTRAINT fk_savings_goals_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_savings_goals_user_month UNIQUE (user_id, month_key)
);

CREATE INDEX IF NOT EXISTS idx_savings_goals_user_month ON savings_goals(user_id, month_key);


