# Plan: actualizacion de dependencias frontend vulnerables

Fecha: 2026-08-22

Issue: #229

## Objetivo

Eliminar las vulnerabilidades reportadas por `npm audit` en el frontend antes
de avanzar hacia un deploy comercializable.

## Alcance

- Actualizar Angular y tooling relacionado dentro del mismo major.
- Actualizar dependencias transitivas corregibles mediante versiones parche.
- Regenerar `package-lock.json` de forma reproducible.
- No usar `npm audit fix --force`.
- No saltar majors.

## Cambios esperados

- Angular pasa a `21.2.21`.
- `@angular/build`, `@angular/cli` y `@angular/compiler-cli` pasan a `21.2.21`.
- Tailwind/PostCSS/Vitest/Prettier pasan a versiones parche compatibles.
- `npm audit` queda en cero vulnerabilidades.

## Validacion

- `npm ci`
- `npm audit`
- `npm run build`
- `npm test -- --watch=false`

## Notas operativas

Si `node_modules` queda bloqueado en Windows por `ng serve`, detener el proceso
antes de reinstalar dependencias.
