-- Why a discounted student pays less than the center's price. Required from now
-- on whenever the price is below the center rate; null for full-price students.
ALTER TABLE students ADD COLUMN discount_reason text;
