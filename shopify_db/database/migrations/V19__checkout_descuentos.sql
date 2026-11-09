-- V19: varios descuentos por checkout (RF-DISC-01). Un código una vez por
-- checkout; códigos distintos combinan (máx. 5 de producto/pedido + 1 de
-- envío). Espejo de `pedido_descuentos`: el checkout manda CASCADE y el
-- descuento conserva RESTRICT para no borrar historial de usos.
CREATE TABLE checkout_descuentos (
  id BIGSERIAL PRIMARY KEY,
  checkout_sesion_id BIGINT NOT NULL REFERENCES checkout_sesiones(id) ON DELETE CASCADE,
  descuento_id BIGINT NOT NULL REFERENCES descuentos(id) ON DELETE RESTRICT,
  monto_aplicado NUMERIC(12,2) NOT NULL CHECK (monto_aplicado >= 0),
  CONSTRAINT uq_checkout_descuento UNIQUE (checkout_sesion_id, descuento_id)
);

CREATE INDEX idx_checkout_descuentos_checkout ON checkout_descuentos(checkout_sesion_id);
CREATE INDEX idx_checkout_descuentos_descuento ON checkout_descuentos(descuento_id);
