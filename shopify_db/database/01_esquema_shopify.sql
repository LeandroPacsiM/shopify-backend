-- ==========================================================
-- BASE DE DATOS: SHOPIFY_DB
-- Motor: PostgreSQL
-- ==========================================================

-- ==========================================================
-- USUARIOS, ROLES Y AUDITORÍA
-- ==========================================================

CREATE TABLE usuarios (
  id BIGSERIAL PRIMARY KEY,
  email VARCHAR(255) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  nombre VARCHAR(100),
  apellido VARCHAR(100),
  activo BOOLEAN NOT NULL DEFAULT TRUE,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE roles (
  id BIGSERIAL PRIMARY KEY,
  nombre VARCHAR(50) NOT NULL UNIQUE,
  descripcion VARCHAR(255)
);

CREATE TABLE permisos (
  id BIGSERIAL PRIMARY KEY,
  nombre VARCHAR(100) NOT NULL UNIQUE,
  descripcion VARCHAR(255)
);

CREATE TABLE rol_permisos (
  id BIGSERIAL PRIMARY KEY,
  rol_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
  permiso_id BIGINT NOT NULL REFERENCES permisos(id) ON DELETE CASCADE,
  CONSTRAINT uq_rol_permiso UNIQUE (rol_id, permiso_id)
);

CREATE TABLE usuario_roles (
  id BIGSERIAL PRIMARY KEY,
  usuario_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
  rol_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
  CONSTRAINT uq_usuario_rol UNIQUE (usuario_id, rol_id)
);

CREATE TABLE clientes (
  id BIGSERIAL PRIMARY KEY,
  usuario_id BIGINT UNIQUE REFERENCES usuarios(id) ON DELETE SET NULL,
  nombre VARCHAR(100),
  apellido VARCHAR(100),
  telefono VARCHAR(30),
  acepta_marketing BOOLEAN NOT NULL DEFAULT FALSE,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE direcciones (
  id BIGSERIAL PRIMARY KEY,
  cliente_id BIGINT NOT NULL REFERENCES clientes(id) ON DELETE CASCADE,
  linea1 VARCHAR(255) NOT NULL,
  linea2 VARCHAR(255),
  ciudad VARCHAR(100),
  departamento VARCHAR(100),
  codigo_postal VARCHAR(20),
  pais VARCHAR(100),
  es_principal BOOLEAN NOT NULL DEFAULT FALSE,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE sesiones (
  id BIGSERIAL PRIMARY KEY,
  usuario_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
  token VARCHAR(255) NOT NULL UNIQUE,
  ip VARCHAR(45),
  user_agent VARCHAR(255),
  expira_en TIMESTAMPTZ NOT NULL,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE registros_auditoria (
  id BIGSERIAL PRIMARY KEY,
  usuario_id BIGINT REFERENCES usuarios(id) ON DELETE SET NULL,
  entidad VARCHAR(100) NOT NULL,
  entidad_id BIGINT,
  accion VARCHAR(50) NOT NULL,
  datos_anteriores JSONB,
  datos_nuevos JSONB,
  ip VARCHAR(45),
  creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ==========================================================
-- CATÁLOGO
-- ==========================================================

CREATE TABLE proveedores (
  id BIGSERIAL PRIMARY KEY,
  nombre VARCHAR(255) NOT NULL,
  slug VARCHAR(255) NOT NULL UNIQUE,
  descripcion TEXT,
  activo BOOLEAN NOT NULL DEFAULT TRUE,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE productos (
  id BIGSERIAL PRIMARY KEY,
  proveedor_id BIGINT REFERENCES proveedores(id) ON DELETE SET NULL,
  titulo VARCHAR(255) NOT NULL,
  slug VARCHAR(255) NOT NULL UNIQUE,
  descripcion TEXT,
  estado VARCHAR(20) NOT NULL DEFAULT 'borrador',
  publicado_en TIMESTAMPTZ,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT chk_producto_estado CHECK (
    estado IN ('borrador', 'activo', 'archivado')
  )
);

CREATE TABLE producto_opciones (
  id BIGSERIAL PRIMARY KEY,
  producto_id BIGINT NOT NULL REFERENCES productos(id) ON DELETE CASCADE,
  nombre VARCHAR(100) NOT NULL,
  posicion INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE producto_opcion_valores (
  id BIGSERIAL PRIMARY KEY,
  producto_opcion_id BIGINT NOT NULL REFERENCES producto_opciones(id) ON DELETE CASCADE,
  valor VARCHAR(100) NOT NULL,
  posicion INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE producto_variantes (
  id BIGSERIAL PRIMARY KEY,
  producto_id BIGINT NOT NULL REFERENCES productos(id) ON DELETE CASCADE,
  sku VARCHAR(100) UNIQUE,
  codigo_barras VARCHAR(100),
  precio NUMERIC(12,2) NOT NULL CHECK (precio >= 0),
  precio_comparativo NUMERIC(12,2) CHECK (precio_comparativo >= 0),
  peso_gramos INTEGER CHECK (peso_gramos >= 0),
  activo BOOLEAN NOT NULL DEFAULT TRUE,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE variante_opcion_valores (
  id BIGSERIAL PRIMARY KEY,
  variante_id BIGINT NOT NULL REFERENCES producto_variantes(id) ON DELETE CASCADE,
  opcion_valor_id BIGINT NOT NULL REFERENCES producto_opcion_valores(id) ON DELETE CASCADE,
  CONSTRAINT uq_variante_opcion_valor UNIQUE (variante_id, opcion_valor_id)
);

CREATE TABLE producto_imagenes (
  id BIGSERIAL PRIMARY KEY,
  producto_id BIGINT NOT NULL REFERENCES productos(id) ON DELETE CASCADE,
  variante_id BIGINT REFERENCES producto_variantes(id) ON DELETE SET NULL,
  url VARCHAR(500) NOT NULL,
  texto_alt VARCHAR(255),
  posicion INTEGER NOT NULL DEFAULT 0,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE colecciones (
  id BIGSERIAL PRIMARY KEY,
  titulo VARCHAR(255) NOT NULL,
  slug VARCHAR(255) NOT NULL UNIQUE,
  descripcion TEXT,
  activo BOOLEAN NOT NULL DEFAULT TRUE,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE coleccion_productos (
  id BIGSERIAL PRIMARY KEY,
  coleccion_id BIGINT NOT NULL REFERENCES colecciones(id) ON DELETE CASCADE,
  producto_id BIGINT NOT NULL REFERENCES productos(id) ON DELETE CASCADE,
  posicion INTEGER NOT NULL DEFAULT 0,
  CONSTRAINT uq_coleccion_producto UNIQUE (coleccion_id, producto_id)
);

CREATE TABLE etiquetas (
  id BIGSERIAL PRIMARY KEY,
  nombre VARCHAR(100) NOT NULL UNIQUE,
  slug VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE producto_etiquetas (
  id BIGSERIAL PRIMARY KEY,
  producto_id BIGINT NOT NULL REFERENCES productos(id) ON DELETE CASCADE,
  etiqueta_id BIGINT NOT NULL REFERENCES etiquetas(id) ON DELETE CASCADE,
  CONSTRAINT uq_producto_etiqueta UNIQUE (producto_id, etiqueta_id)
);

CREATE TABLE metacampos (
  id BIGSERIAL PRIMARY KEY,
  entidad VARCHAR(50) NOT NULL,
  entidad_id BIGINT NOT NULL,
  clave VARCHAR(100) NOT NULL,
  valor TEXT,
  tipo VARCHAR(30) NOT NULL DEFAULT 'texto',
  creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_metacampo UNIQUE (entidad, entidad_id, clave)
);

-- ==========================================================
-- INVENTARIO
-- ==========================================================

CREATE TABLE ubicaciones (
  id BIGSERIAL PRIMARY KEY,
  nombre VARCHAR(255) NOT NULL,
  direccion VARCHAR(255),
  ciudad VARCHAR(100),
  pais VARCHAR(100),
  activo BOOLEAN NOT NULL DEFAULT TRUE,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE articulos_inventario (
  id BIGSERIAL PRIMARY KEY,
  variante_id BIGINT NOT NULL REFERENCES producto_variantes(id) ON DELETE CASCADE,
  sku VARCHAR(100),
  requiere_envio BOOLEAN NOT NULL DEFAULT TRUE,
  controla_stock BOOLEAN NOT NULL DEFAULT TRUE,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE niveles_inventario (
  id BIGSERIAL PRIMARY KEY,
  articulo_inventario_id BIGINT NOT NULL REFERENCES articulos_inventario(id) ON DELETE CASCADE,
  ubicacion_id BIGINT NOT NULL REFERENCES ubicaciones(id) ON DELETE CASCADE,
  disponible INTEGER NOT NULL DEFAULT 0 CHECK (disponible >= 0),
  reservado INTEGER NOT NULL DEFAULT 0 CHECK (reservado >= 0),
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_nivel_inventario UNIQUE (articulo_inventario_id, ubicacion_id)
);

CREATE TABLE movimientos_inventario (
  id BIGSERIAL PRIMARY KEY,
  nivel_inventario_id BIGINT NOT NULL REFERENCES niveles_inventario(id) ON DELETE CASCADE,
  tipo_movimiento VARCHAR(30) NOT NULL,
  cantidad INTEGER NOT NULL CHECK (cantidad <> 0),
  motivo VARCHAR(255),
  referencia VARCHAR(100),
  usuario_id BIGINT REFERENCES usuarios(id) ON DELETE SET NULL,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT chk_tipo_movimiento CHECK (
    tipo_movimiento IN ('entrada', 'salida', 'ajuste', 'reserva', 'liberacion')
  )
);

-- ==========================================================
-- CARRITO Y CHECKOUT
-- ==========================================================

CREATE TABLE carritos (
  id BIGSERIAL PRIMARY KEY,
  cliente_id BIGINT REFERENCES clientes(id) ON DELETE SET NULL,
  token VARCHAR(255) NOT NULL UNIQUE,
  moneda CHAR(3) NOT NULL DEFAULT 'PEN',
  estado VARCHAR(20) NOT NULL DEFAULT 'activo',
  creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  expira_en TIMESTAMPTZ,
  CONSTRAINT chk_carrito_estado CHECK (
    estado IN ('activo', 'convertido', 'abandonado', 'expirado')
  )
);

CREATE TABLE carrito_items (
  id BIGSERIAL PRIMARY KEY,
  carrito_id BIGINT NOT NULL REFERENCES carritos(id) ON DELETE CASCADE,
  variante_id BIGINT NOT NULL REFERENCES producto_variantes(id) ON DELETE CASCADE,
  cantidad INTEGER NOT NULL CHECK (cantidad > 0),
  precio_unitario NUMERIC(12,2) NOT NULL CHECK (precio_unitario >= 0),
  creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_carrito_variante UNIQUE (carrito_id, variante_id)
);

CREATE TABLE metodos_envio (
  id BIGSERIAL PRIMARY KEY,
  nombre VARCHAR(100) NOT NULL,
  descripcion VARCHAR(255),
  precio NUMERIC(12,2) NOT NULL CHECK (precio >= 0),
  activo BOOLEAN NOT NULL DEFAULT TRUE,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE tasas_impuesto (
  id BIGSERIAL PRIMARY KEY,
  nombre VARCHAR(100) NOT NULL,
  pais VARCHAR(100),
  departamento VARCHAR(100),
  porcentaje NUMERIC(5,2) NOT NULL CHECK (porcentaje >= 0),
  activo BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE checkout_sesiones (
  id BIGSERIAL PRIMARY KEY,
  carrito_id BIGINT NOT NULL REFERENCES carritos(id) ON DELETE CASCADE,
  cliente_id BIGINT REFERENCES clientes(id) ON DELETE SET NULL,
  direccion_envio_id BIGINT REFERENCES direcciones(id) ON DELETE SET NULL,
  direccion_facturacion_id BIGINT REFERENCES direcciones(id) ON DELETE SET NULL,
  metodo_envio_id BIGINT REFERENCES metodos_envio(id) ON DELETE SET NULL,
  moneda CHAR(3) NOT NULL DEFAULT 'PEN',
  subtotal NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (subtotal >= 0),
  impuesto_total NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (impuesto_total >= 0),
  envio_total NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (envio_total >= 0),
  descuento_total NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (descuento_total >= 0),
  total NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (total >= 0),
  estado VARCHAR(30) NOT NULL DEFAULT 'pendiente',
  expira_en TIMESTAMPTZ,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT chk_checkout_estado CHECK (
    estado IN ('pendiente', 'completado', 'expirado', 'cancelado')
  )
);

-- ==========================================================
-- PEDIDOS
-- ==========================================================

CREATE TABLE pedidos (
  id BIGSERIAL PRIMARY KEY,
  cliente_id BIGINT REFERENCES clientes(id) ON DELETE SET NULL,
  checkout_sesion_id BIGINT UNIQUE REFERENCES checkout_sesiones(id) ON DELETE SET NULL,
  numero_pedido VARCHAR(30) NOT NULL UNIQUE,
  estado VARCHAR(30) NOT NULL DEFAULT 'pendiente',
  moneda CHAR(3) NOT NULL DEFAULT 'PEN',
  subtotal NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (subtotal >= 0),
  impuesto_total NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (impuesto_total >= 0),
  envio_total NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (envio_total >= 0),
  descuento_total NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (descuento_total >= 0),
  total NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (total >= 0),
  realizado_en TIMESTAMPTZ,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT chk_pedido_estado CHECK (
    estado IN (
      'pendiente', 'pagado', 'en_preparacion', 'enviado',
      'entregado', 'cancelado', 'reembolsado'
    )
  )
);

CREATE TABLE pedido_items (
  id BIGSERIAL PRIMARY KEY,
  pedido_id BIGINT NOT NULL REFERENCES pedidos(id) ON DELETE CASCADE,
  variante_id BIGINT REFERENCES producto_variantes(id) ON DELETE SET NULL,
  producto_titulo VARCHAR(255) NOT NULL,
  variante_titulo VARCHAR(255),
  sku VARCHAR(100),
  cantidad INTEGER NOT NULL CHECK (cantidad > 0),
  precio_unitario NUMERIC(12,2) NOT NULL CHECK (precio_unitario >= 0),
  precio_total NUMERIC(12,2) NOT NULL CHECK (precio_total >= 0)
);

CREATE TABLE pedido_direcciones (
  id BIGSERIAL PRIMARY KEY,
  pedido_id BIGINT NOT NULL REFERENCES pedidos(id) ON DELETE CASCADE,
  tipo VARCHAR(20) NOT NULL,
  nombre_destinatario VARCHAR(150),
  linea1 VARCHAR(255),
  linea2 VARCHAR(255),
  ciudad VARCHAR(100),
  departamento VARCHAR(100),
  codigo_postal VARCHAR(20),
  pais VARCHAR(100),
  telefono VARCHAR(30),
  CONSTRAINT chk_tipo_direccion CHECK (
    tipo IN ('envio', 'facturacion')
  )
);

CREATE TABLE historial_estados_pedido (
  id BIGSERIAL PRIMARY KEY,
  pedido_id BIGINT NOT NULL REFERENCES pedidos(id) ON DELETE CASCADE,
  estado_anterior VARCHAR(30),
  estado_nuevo VARCHAR(30) NOT NULL,
  comentario VARCHAR(255),
  usuario_id BIGINT REFERENCES usuarios(id) ON DELETE SET NULL,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ==========================================================
-- PAGOS
-- ==========================================================

CREATE TABLE pasarelas_pago (
  id BIGSERIAL PRIMARY KEY,
  nombre VARCHAR(100) NOT NULL,
  codigo VARCHAR(50) NOT NULL UNIQUE,
  descripcion VARCHAR(255),
  activo BOOLEAN NOT NULL DEFAULT TRUE,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE pagos (
  id BIGSERIAL PRIMARY KEY,
  pedido_id BIGINT NOT NULL REFERENCES pedidos(id) ON DELETE CASCADE,
  pasarela_pago_id BIGINT NOT NULL REFERENCES pasarelas_pago(id) ON DELETE RESTRICT,
  estado VARCHAR(30) NOT NULL DEFAULT 'pendiente',
  monto NUMERIC(12,2) NOT NULL CHECK (monto >= 0),
  moneda CHAR(3) NOT NULL DEFAULT 'PEN',
  transaccion_externa_id VARCHAR(255),
  metodo VARCHAR(50),
  pagado_en TIMESTAMPTZ,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT chk_pago_estado CHECK (
    estado IN ('pendiente', 'procesando', 'aprobado', 'rechazado', 'cancelado', 'reembolsado')
  )
);

CREATE TABLE pago_eventos (
  id BIGSERIAL PRIMARY KEY,
  pago_id BIGINT NOT NULL REFERENCES pagos(id) ON DELETE CASCADE,
  tipo_evento VARCHAR(50) NOT NULL,
  estado_anterior VARCHAR(30),
  estado_nuevo VARCHAR(30),
  payload JSONB,
  referencia_externa VARCHAR(255),
  creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE reembolsos (
  id BIGSERIAL PRIMARY KEY,
  pedido_id BIGINT NOT NULL REFERENCES pedidos(id) ON DELETE CASCADE,
  pago_id BIGINT NOT NULL REFERENCES pagos(id) ON DELETE CASCADE,
  monto NUMERIC(12,2) NOT NULL CHECK (monto > 0),
  moneda CHAR(3) NOT NULL DEFAULT 'PEN',
  motivo VARCHAR(255),
  estado VARCHAR(30) NOT NULL DEFAULT 'pendiente',
  transaccion_externa_id VARCHAR(255),
  creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT chk_reembolso_estado CHECK (
    estado IN ('pendiente', 'procesando', 'aprobado', 'rechazado')
  )
);

-- ==========================================================
-- ENVÍOS Y TRACKING
-- ==========================================================

CREATE TABLE transportistas (
  id BIGSERIAL PRIMARY KEY,
  nombre VARCHAR(100) NOT NULL UNIQUE,
  codigo VARCHAR(50) NOT NULL UNIQUE,
  url_tracking VARCHAR(500),
  activo BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE envios (
  id BIGSERIAL PRIMARY KEY,
  pedido_id BIGINT NOT NULL REFERENCES pedidos(id) ON DELETE CASCADE,
  transportista_id BIGINT REFERENCES transportistas(id) ON DELETE SET NULL,
  ubicacion_origen_id BIGINT REFERENCES ubicaciones(id) ON DELETE SET NULL,
  numero_tracking VARCHAR(100),
  url_tracking VARCHAR(500),
  transportista VARCHAR(100),
  estado VARCHAR(30) NOT NULL DEFAULT 'pendiente',
  enviado_en TIMESTAMPTZ,
  entregado_en TIMESTAMPTZ,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT chk_envio_estado CHECK (
    estado IN (
      'pendiente', 'preparando', 'enviado', 'en_transito',
      'en_reparto', 'entregado', 'devuelto', 'cancelado'
    )
  )
);

CREATE TABLE envio_items (
  id BIGSERIAL PRIMARY KEY,
  envio_id BIGINT NOT NULL REFERENCES envios(id) ON DELETE CASCADE,
  pedido_item_id BIGINT NOT NULL REFERENCES pedido_items(id) ON DELETE CASCADE,
  cantidad INTEGER NOT NULL CHECK (cantidad > 0)
);

CREATE TABLE envio_eventos (
  id BIGSERIAL PRIMARY KEY,
  envio_id BIGINT NOT NULL REFERENCES envios(id) ON DELETE CASCADE,
  estado VARCHAR(50) NOT NULL,
  descripcion VARCHAR(255),
  ubicacion VARCHAR(255),
  ocurrido_en TIMESTAMPTZ NOT NULL,
  referencia_externa VARCHAR(255),
  creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ==========================================================
-- DESCUENTOS
-- ==========================================================

CREATE TABLE descuentos (
  id BIGSERIAL PRIMARY KEY,
  codigo VARCHAR(50) NOT NULL UNIQUE,
  tipo VARCHAR(30) NOT NULL,
  valor NUMERIC(12,2) NOT NULL CHECK (valor >= 0),
  descripcion VARCHAR(255),
  uso_maximo INTEGER CHECK (uso_maximo > 0),
  usos_actuales INTEGER NOT NULL DEFAULT 0 CHECK (usos_actuales >= 0),
  inicia_en TIMESTAMPTZ,
  termina_en TIMESTAMPTZ,
  activo BOOLEAN NOT NULL DEFAULT TRUE,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT chk_tipo_descuento CHECK (
    tipo IN ('porcentaje', 'monto_fijo', 'envio_gratis')
  )
);

CREATE TABLE pedido_descuentos (
  id BIGSERIAL PRIMARY KEY,
  pedido_id BIGINT NOT NULL REFERENCES pedidos(id) ON DELETE CASCADE,
  descuento_id BIGINT NOT NULL REFERENCES descuentos(id) ON DELETE RESTRICT,
  monto_aplicado NUMERIC(12,2) NOT NULL CHECK (monto_aplicado >= 0)
);

CREATE TABLE descuento_productos (
  id BIGSERIAL PRIMARY KEY,
  descuento_id BIGINT NOT NULL REFERENCES descuentos(id) ON DELETE CASCADE,
  producto_id BIGINT NOT NULL REFERENCES productos(id) ON DELETE CASCADE,
  CONSTRAINT uq_descuento_producto UNIQUE (descuento_id, producto_id)
);

CREATE TABLE descuento_colecciones (
  id BIGSERIAL PRIMARY KEY,
  descuento_id BIGINT NOT NULL REFERENCES descuentos(id) ON DELETE CASCADE,
  coleccion_id BIGINT NOT NULL REFERENCES colecciones(id) ON DELETE CASCADE,
  CONSTRAINT uq_descuento_coleccion UNIQUE (descuento_id, coleccion_id)
);

-- ==========================================================
-- EXTRAS
-- ==========================================================

CREATE TABLE resenas (
  id BIGSERIAL PRIMARY KEY,
  producto_id BIGINT NOT NULL REFERENCES productos(id) ON DELETE CASCADE,
  cliente_id BIGINT REFERENCES clientes(id) ON DELETE SET NULL,
  calificacion INTEGER NOT NULL CHECK (calificacion BETWEEN 1 AND 5),
  titulo VARCHAR(255),
  comentario TEXT,
  aprobada BOOLEAN NOT NULL DEFAULT FALSE,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE listas_deseos (
  id BIGSERIAL PRIMARY KEY,
  cliente_id BIGINT NOT NULL REFERENCES clientes(id) ON DELETE CASCADE,
  nombre VARCHAR(100) NOT NULL DEFAULT 'Favoritos',
  creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE lista_deseo_items (
  id BIGSERIAL PRIMARY KEY,
  lista_deseo_id BIGINT NOT NULL REFERENCES listas_deseos(id) ON DELETE CASCADE,
  producto_id BIGINT NOT NULL REFERENCES productos(id) ON DELETE CASCADE,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_lista_deseo_producto UNIQUE (lista_deseo_id, producto_id)
);

-- ==========================================================
-- ÍNDICES
-- ==========================================================

CREATE INDEX idx_usuarios_email ON usuarios(email);
CREATE INDEX idx_clientes_usuario ON clientes(usuario_id);
CREATE INDEX idx_direcciones_cliente ON direcciones(cliente_id);
CREATE INDEX idx_sesiones_usuario ON sesiones(usuario_id);
CREATE INDEX idx_sesiones_token ON sesiones(token);
CREATE INDEX idx_auditoria_entidad ON registros_auditoria(entidad, entidad_id);
CREATE INDEX idx_auditoria_usuario ON registros_auditoria(usuario_id);

CREATE INDEX idx_productos_proveedor ON productos(proveedor_id);
CREATE INDEX idx_productos_estado ON productos(estado);
CREATE INDEX idx_producto_opciones_producto ON producto_opciones(producto_id);
CREATE INDEX idx_producto_opcion_valores_opcion ON producto_opcion_valores(producto_opcion_id);
CREATE INDEX idx_variantes_producto ON producto_variantes(producto_id);
CREATE INDEX idx_variantes_sku ON producto_variantes(sku);
CREATE INDEX idx_variante_opcion_valores_variante ON variante_opcion_valores(variante_id);
CREATE INDEX idx_producto_imagenes_producto ON producto_imagenes(producto_id);
CREATE INDEX idx_coleccion_productos_producto ON coleccion_productos(producto_id);
CREATE INDEX idx_producto_etiquetas_producto ON producto_etiquetas(producto_id);
CREATE INDEX idx_metacampos_entidad ON metacampos(entidad, entidad_id);

CREATE INDEX idx_articulos_inventario_variante ON articulos_inventario(variante_id);
CREATE INDEX idx_niveles_inventario_articulo ON niveles_inventario(articulo_inventario_id);
CREATE INDEX idx_niveles_inventario_ubicacion ON niveles_inventario(ubicacion_id);
CREATE INDEX idx_movimientos_nivel ON movimientos_inventario(nivel_inventario_id);

CREATE INDEX idx_carritos_cliente ON carritos(cliente_id);
CREATE INDEX idx_carrito_items_carrito ON carrito_items(carrito_id);
CREATE INDEX idx_carrito_items_variante ON carrito_items(variante_id);
CREATE INDEX idx_checkout_carrito ON checkout_sesiones(carrito_id);

CREATE INDEX idx_pedidos_cliente ON pedidos(cliente_id);
CREATE INDEX idx_pedidos_estado ON pedidos(estado);
CREATE INDEX idx_pedido_items_pedido ON pedido_items(pedido_id);
CREATE INDEX idx_pedido_items_variante ON pedido_items(variante_id);
CREATE INDEX idx_pedido_direcciones_pedido ON pedido_direcciones(pedido_id);
CREATE INDEX idx_historial_pedido ON historial_estados_pedido(pedido_id);

CREATE INDEX idx_pagos_pedido ON pagos(pedido_id);
CREATE INDEX idx_pagos_pasarela ON pagos(pasarela_pago_id);
CREATE INDEX idx_pago_eventos_pago ON pago_eventos(pago_id);
CREATE INDEX idx_reembolsos_pedido ON reembolsos(pedido_id);
CREATE INDEX idx_reembolsos_pago ON reembolsos(pago_id);

CREATE INDEX idx_envios_pedido ON envios(pedido_id);
CREATE INDEX idx_envios_transportista ON envios(transportista_id);
CREATE INDEX idx_envios_tracking ON envios(numero_tracking);
CREATE INDEX idx_envio_items_envio ON envio_items(envio_id);
CREATE INDEX idx_envio_items_pedido_item ON envio_items(pedido_item_id);
CREATE INDEX idx_envio_eventos_envio ON envio_eventos(envio_id);

CREATE INDEX idx_pedido_descuentos_pedido ON pedido_descuentos(pedido_id);
CREATE INDEX idx_pedido_descuentos_descuento ON pedido_descuentos(descuento_id);
CREATE INDEX idx_descuento_productos_producto ON descuento_productos(producto_id);
CREATE INDEX idx_descuento_colecciones_coleccion ON descuento_colecciones(coleccion_id);

CREATE INDEX idx_resenas_producto ON resenas(producto_id);
CREATE INDEX idx_resenas_cliente ON resenas(cliente_id);
CREATE INDEX idx_listas_deseos_cliente ON listas_deseos(cliente_id);
CREATE INDEX idx_lista_deseo_items_lista ON lista_deseo_items(lista_deseo_id);
CREATE INDEX idx_lista_deseo_items_producto ON lista_deseo_items(producto_id);

-- ==========================================================
-- COMENTARIOS
-- ==========================================================

DO $$
BEGIN
  EXECUTE format(
    'COMMENT ON DATABASE %I IS %L',
    current_database(),
    'Base de datos de un e-commerce tipo Shopify'
  );
END
$$;

COMMENT ON TABLE usuarios IS 'Usuarios con acceso al sistema: administradores, operadores o clientes';
COMMENT ON TABLE clientes IS 'Información comercial del comprador';
COMMENT ON TABLE productos IS 'Catálogo de productos de la tienda';
COMMENT ON TABLE producto_variantes IS 'Versiones comprables de un producto: talla, color, SKU, precio';
COMMENT ON TABLE niveles_inventario IS 'Stock disponible y reservado por artículo y ubicación';
COMMENT ON TABLE pedidos IS 'Pedidos realizados por clientes';
COMMENT ON TABLE pedido_items IS 'Líneas de venta con datos históricos del producto y la variante';
COMMENT ON TABLE pagos IS 'Pagos o intentos de pago asociados a un pedido';
COMMENT ON TABLE pasarelas_pago IS 'Proveedores de pago: Stripe, Culqi, Yape, PayPal, etc.';
COMMENT ON TABLE envios IS 'Envíos asociados a un pedido';
COMMENT ON TABLE envio_eventos IS 'Eventos de seguimiento del envío proporcionados por el transportista';
COMMENT ON TABLE historial_estados_pedido IS 'Historial de cambios de estado del pedido';
