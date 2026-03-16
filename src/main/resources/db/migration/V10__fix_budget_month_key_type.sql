-- Fix budgets.month_key type mismatch for Hibernate validation (expects VARCHAR(6)).
ALTER TABLE budgets
    ALTER COLUMN month_key TYPE VARCHAR(6)
    USING TRIM(month_key);

