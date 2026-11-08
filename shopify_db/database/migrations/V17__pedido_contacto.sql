-- ==========================================================
-- MIGRACIÓN V17: contacto y notas en checkout y pedidos
-- El cliente deja email/teléfono de contacto y notas al crear el
-- checkout; al confirmar se copian al pedido (snapshot, como los
-- totales y las direcciones). Todo nullable: pedidos viejos y compra
-- sin notas siguen válidos. Sin PII nueva en otras tablas.
-- No editar después de aplicarlo.
-- ==========================================================

ALTER TABLE checkout_sesiones
  ADD COLUMN email_contacto VARCHAR(255) NULL,
  ADD COLUMN telefono_contacto VARCHAR(50) NULL,
  ADD COLUMN notas_cliente VARCHAR(1000) NULL;

ALTER TABLE pedidos
  ADD COLUMN email_contacto VARCHAR(255) NULL,
  ADD COLUMN telefono_contacto VARCHAR(50) NULL,
  ADD COLUMN notas_cliente VARCHAR(1000) NULL;
