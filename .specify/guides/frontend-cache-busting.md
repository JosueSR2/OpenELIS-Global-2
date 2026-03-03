# Frontend Cache Busting & Auto-Update Guide

## Problema Resuelto

**Síntoma:** Al reiniciar el frontend, los usuarios seguían viendo el diseño anterior porque el navegador cacheaba indefinidamente los archivos `index.html`, CSS y JavaScript.

**Causa:** 
1. No había configuración de headers HTTP `Cache-Control`
2. Los meta tags de caché no estaban presentes en `index.html`
3. No había mecanismo para detectar cambios de versión

## Solución Implementada

### 1. Configuración de Nginx (frontend/nginx/nginx.conf)

**Cambio:**
- `index.html` → `Cache-Control: no-cache, no-store, must-revalidate` (nunca cachear)
- Assets (CSS, JS, imágenes) → `Cache-Control: public, max-age=31536000, immutable` (cachear 1 año con hash)
- Otras rutas → `Cache-Control: no-cache` (validar antes de usar)

**Beneficio:** Los cambios en `index.html` se cargan inmediatamente; los assets con hash se cachean indefinidamente para rendimiento.

### 2. Meta Tags HTTP en public/index.html

**Cambio:**
```html
<meta http-equiv="Cache-Control" content="no-cache, no-store, must-revalidate" />
<meta http-equiv="Pragma" content="no-cache" />
<meta http-equiv="Expires" content="0" />
<meta name="build-version" content="%BUILD_TIMESTAMP%" />
```

**Beneficio:** Navegadores antiguos y proxies respetan estos headers como respaldo.

### 3. Mecanismo de Detección de Cambios (frontend/src/index.js)

**Cambio:**
```javascript
// Verificar cada 5 minutos si hay nueva versión
// Si detecta cambio, recarga la página automáticamente
setInterval(checkForUpdates, 5 * 60 * 1000);
```

**Beneficio:** Los usuarios obtienen nuevos diseños automáticamente sin necesidad de recargar manualmente.

### 4. Timestamp de Build en Dockerfile.prod

**Cambio:**
```dockerfile
RUN sed -i "s|%BUILD_TIMESTAMP%|$(date +%s)|g" /usr/share/nginx/html/index.html
```

**Beneficio:** Cada build de Docker obtiene un timestamp único que se compara en el navegador.

## Flujo de Actualización

```
1. Usuario accede a https://localhost/
   ↓
2. Nginx devuelve index.html con:
   - Header: Cache-Control: no-cache, no-store, must-revalidate
   - Meta tag: build-version = "1709475310" (timestamp del build)
   ↓
3. JavaScript en index.js ejecuta checkForUpdates()
   ↓
4. Si detecta cambio de versión:
   - console.log("Nueva versión disponible. Recargando...")
   - window.location.reload(true) // Hard refresh
   ↓
5. Navegador descarga nuevo index.html y assets
   ↓
6. Usuario ve el diseño actualizado
```

## Cómo Probar

### En Desarrollo

```bash
# 1. Hacer cambios en el código React
# 2. El `npm start` recompila automáticamente
# 3. Abrir DevTools (F12) → Network
# 4. Recargar página
# 5. Verificar que index.html tiene:
#    - Status: 304 (Not Modified) O 200 (con new timestamp)
#    - Header: Cache-Control: no-cache...
```

### En Producción (Docker)

```bash
# 1. Hacer cambios en frontend/
# 2. Reconstruir imagen:
docker compose -f dev.docker-compose.yml up --build frontend

# 3. Abrir navegador en https://localhost/
# 4. Ver consola del navegador:
#    - Si hay nueva versión: "Nueva versión disponible. Recargando..."
#    - Página se recarga automáticamente
# 5. Hard refresh manualmente (Ctrl+Shift+R en Firefox, Cmd+Shift+R en Chrome)
```

## Configuración de Caché por Tipo de Archivo

| Archivo | Cache-Control | Tiempo | Razón |
|---------|---------------|--------|-------|
| index.html | no-cache | 0 | Detectar cambios inmediatamente |
| main.*.js | public, immutable | 1 año | Hash único por build |
| main.*.css | public, immutable | 1 año | Hash único por build |
| Imágenes | public, immutable | 1 año | Cambios = nuevo hash |
| Fuentes | public, immutable | 1 año | Rara vez cambian |
| Otras rutas (SPA) | no-cache | 0 | Rutas dinámicas hacia index.html |

## Notas Importantes

1. **Service Worker:** Si está habilitado, puede interferir. El código en `index.js` ya maneja esto.

2. **HTTPS:** En producción, asegurar que se usa HTTPS. Los headers de caché funcionan mejor con HTTPS.

3. **CDN/Proxy:** Si hay un CDN o proxy inverso, verificar que respete los headers `Cache-Control`. Proxies viejos pueden ignorarlos.

4. **Browsers Antiguos:** Los `<meta http-equiv>` tags ayudan con navegadores que no respetan headers HTTP.

5. **Hard Refresh:** Los usuarios pueden forzar una actualización completa con:
   - **Firefox:** Ctrl+Shift+R
   - **Chrome:** Cmd+Shift+R (Mac) / Ctrl+Shift+R (Windows/Linux)
   - **Safari:** Cmd+Shift+R (desactivar caché, luego recargar)

## Verificar Headers en Nginx

```bash
# Conectarse al contenedor de frontend
docker compose -f dev.docker-compose.yml exec frontend sh

# Verificar configuración de nginx
cat /etc/nginx/conf.d/default.conf

# Recargar nginx sin parar
nginx -s reload
```

## Debugging

Si los cambios no se reflejan:

1. **Limpiar caché del navegador:**
   - DevTools → Storage → Cache Storage → Borrar todo
   - DevTools → Application → Service Workers → Unregister

2. **Forzar hard refresh:**
   - Ctrl+Shift+R (elimina caché, descarga todo nuevamente)

3. **Verificar headers en Network:**
   - DevTools → Network → index.html → Headers
   - Buscar `Cache-Control: no-cache, no-store`

4. **Revisar consola:**
   - DevTools → Console
   - Buscar: "Nueva versión disponible. Recargando..."

## Performance Impact

- **No hay impacto negativo:**
  - Assets con hash se cachean indefinidamente (mejor performance)
  - Único cambio: `index.html` no se cachea (pequeño descarga)
  - Verificación de cambios: 1 request cada 5 minutos (negligible)

- **Beneficios:**
  - Usuarios siempre ven la versión más reciente
  - Cambios se detectan automáticamente
  - Sin necesidad de instrucciones manuales de "limpia caché"
