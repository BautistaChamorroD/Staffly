CREATE TABLE company (
    id UUID PRIMARY KEY,
    nombre VARCHAR(255) NOT NULL,
    razon_social VARCHAR(255) NOT NULL,
    pais VARCHAR(100) NOT NULL,
    moneda VARCHAR(10) NOT NULL,
    zona_horaria VARCHAR(100) NOT NULL,
    estado VARCHAR(20) NOT NULL,
    plan VARCHAR(100),
    fecha_alta TIMESTAMP NOT NULL
);
