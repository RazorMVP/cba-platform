-- Backlog 5: cardholder controls. PUT /card-api/v1/cards/{id}/controls accepted these flags and dropped
-- them; they are now stored per card and enforced by CardAuthorizationService.authorize (RC 57).
-- Default true, so every existing card keeps working exactly as before.
ALTER TABLE cards
    ADD COLUMN contactless_enabled   BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN cnp_enabled           BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN international_enabled BOOLEAN NOT NULL DEFAULT true;
