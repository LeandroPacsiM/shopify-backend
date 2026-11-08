-- ==========================================================
-- MIGRACIÓN V10: tabla tiendas (tenants) + credenciales por tienda
-- Paso 1 del plan multi-tenant. Solo crea el catálogo; el
-- tienda_id en cada recurso llega en V11+ (con backfill).
-- No editar después de aplicarlo.
-- ==========================================================

CREATE TABLE tiendas (
  id BIGSERIAL PRIMARY KEY,
  nombre VARCHAR(255) NOT NULL,
  slug VARCHAR(100) NOT NULL UNIQUE,
  descripcion VARCHAR(255),
  activa BOOLEAN NOT NULL DEFAULT TRUE,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Credenciales de pasarela POR TIENDA (RF-PAY-01): el adapter usa
-- las de la tienda del pedido. Secretos CIFRADOS (llave en entorno),
-- jamás en claro. Una fila por (tienda, pasarela).

CREATE TABLE tienda_pasarelas (
  id BIGSERIAL PRIMARY KEY,
  tienda_id BIGINT NOT NULL REFERENCES tiendas(id) ON DELETE CASCADE,
  pasarela_pago_id BIGINT NOT NULL REFERENCES pasarelas_pago(id) ON DELETE RESTRICT,
  clave_publica VARCHAR(255) NOT NULL,
  secreto_cifrado TEXT NOT NULL,
  modo VARCHAR(10) NOT NULL DEFAULT 'test',
  activo BOOLEAN NOT NULL DEFAULT TRUE,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_tienda_pasarela UNIQUE (tienda_id, pasarela_pago_id),
  CONSTRAINT chk_tienda_pasarela_modo CHECK (modo IN ('test', 'vivo'))
);

CREATE INDEX idx_tienda_pasarelas_tienda ON tienda_pasarelas(tienda_id);

DROP TRIGGER IF EXISTS trg_tiendas_actualizado_en ON tiendas;
CREATE TRIGGER trg_tiendas_actualizado_en
  BEFORE UPDATE ON tiendas
  FOR EACH ROW EXECUTE FUNCTION trg_refrescar_actualizado_en();

DROP TRIGGER IF EXISTS trg_tienda_pasarelas_actualizado_en ON tienda_pasarelas;
CREATE TRIGGER trg_tienda_pasarelas_actualizado_en
  BEFORE UPDATE ON tienda_pasarelas
  FOR EACH ROW EXECUTE FUNCTION trg_refrescar_actualizado_en();
