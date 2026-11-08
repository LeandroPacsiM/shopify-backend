-- ==========================================================
-- MIGRACIÓN V5: cliente obligatorio + no borrar con historial
-- Replica Shopify (verificado en doc oficial):
-- - Con "require sign in before checkout", todo pedido queda ligado
--   a un customer profile; Order.customer solo es null en guest.
-- - Shopify BLOQUEA borrar un cliente asociado a 1+ pedidos
--   ("A customer can be deleted only if they haven't made an order").
-- - GDPR no es DELETE: es "Erase personal data" (redacta PII pero
--   conserva perfil e historial). El backend debe anonimizar,
--   jamás borrar clientes con pedidos.
-- No editar después de aplicarlo (crear V6 para lo siguiente).
-- ==========================================================
-- ANTES DE APLICAR (solo si ya hay datos): deben dar 0 filas.
-- Limpiar o migrar esos casos primero, si no el ALTER falla:
--
-- SELECT COUNT(*) FROM pedidos WHERE cliente_id IS NULL;
-- SELECT COUNT(*) FROM checkout_sesiones WHERE cliente_id IS NULL;
-- SELECT COUNT(*) FROM clientes WHERE usuario_id IS NULL;
-- ==========================================================

-- ---------- 1. clientes.usuario_id: NOT NULL + RESTRICT ----------
-- Adiós clientes fantasma. Borrar un usuario con cliente se bloquea;
-- la baja es desactivar (usuarios.activo=FALSE) o anonimizar.

ALTER TABLE clientes
  ALTER COLUMN usuario_id SET NOT NULL;

DO $$
DECLARE fk_name TEXT;
BEGIN
  SELECT c.conname INTO fk_name
    FROM pg_constraint c
    JOIN pg_attribute a ON a.attrelid = c.conrelid
     AND a.attnum = c.conkey[1]
   WHERE c.conrelid = 'clientes'::regclass
     AND c.contype = 'f'
     AND a.attname = 'usuario_id';
  IF fk_name IS NOT NULL THEN
    EXECUTE format('ALTER TABLE clientes DROP CONSTRAINT %I', fk_name);
  END IF;
END $$;

ALTER TABLE clientes
  ADD CONSTRAINT fk_clientes_usuario
  FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE RESTRICT;

-- ---------- 2. pedidos.cliente_id: NOT NULL + RESTRICT ----------
-- Sin guest (decisión A): todo pedido tiene dueño. Borrar un cliente
-- con pedidos se bloquea en BD; GDPR = anonimizar PII en aplicación.

ALTER TABLE pedidos
  ALTER COLUMN cliente_id SET NOT NULL;

DO $$
DECLARE fk_name TEXT;
BEGIN
  SELECT c.conname INTO fk_name
    FROM pg_constraint c
    JOIN pg_attribute a ON a.attrelid = c.conrelid
     AND a.attnum = c.conkey[1]
   WHERE c.conrelid = 'pedidos'::regclass
     AND c.contype = 'f'
     AND a.attname = 'cliente_id';
  IF fk_name IS NOT NULL THEN
    EXECUTE format('ALTER TABLE pedidos DROP CONSTRAINT %I', fk_name);
  END IF;
END $$;

ALTER TABLE pedidos
  ADD CONSTRAINT fk_pedidos_cliente
  FOREIGN KEY (cliente_id) REFERENCES clientes(id) ON DELETE RESTRICT;

-- ---------- 3. checkout_sesiones.cliente_id: NOT NULL + RESTRICT ----------
-- Con login obligatorio antes del checkout, la sesión siempre tiene
-- cliente. El carrito (carritos.cliente_id) SÍ sigue nullable: el
-- visitante arma su carrito y se autentica al pasar al checkout
-- (igual que Shopify: carrito anónimo, cuenta exigida al pagar).

ALTER TABLE checkout_sesiones
  ALTER COLUMN cliente_id SET NOT NULL;

DO $$
DECLARE fk_name TEXT;
BEGIN
  SELECT c.conname INTO fk_name
    FROM pg_constraint c
    JOIN pg_attribute a ON a.attrelid = c.conrelid
     AND a.attnum = c.conkey[1]
   WHERE c.conrelid = 'checkout_sesiones'::regclass
     AND c.contype = 'f'
     AND a.attname = 'cliente_id';
  IF fk_name IS NOT NULL THEN
    EXECUTE format('ALTER TABLE checkout_sesiones DROP CONSTRAINT %I', fk_name);
  END IF;
END $$;

ALTER TABLE checkout_sesiones
  ADD CONSTRAINT fk_checkout_cliente
  FOREIGN KEY (cliente_id) REFERENCES clientes(id) ON DELETE RESTRICT;
