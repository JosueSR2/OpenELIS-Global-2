const { createProxyMiddleware } = require('http-proxy-middleware');

module.exports = function(app) {
  // Agregar headers de cache-busting a todas las respuestas
  app.use((req, res, next) => {
    // Para index.html y archivos HTML: sin caché
    if (req.url.includes('.html') || req.url === '/') {
      res.set('Cache-Control', 'no-cache, no-store, must-revalidate');
      res.set('Pragma', 'no-cache');
      res.set('Expires', '0');
    }
    // Para archivos estáticos (JS, CSS, imágenes): caché largo plazo
    else if (/\.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$/.test(req.url)) {
      res.set('Cache-Control', 'public, max-age=31536000, immutable');
    }
    next();
  });

  // Proxy API requests to backend
  // Route 1: /api/OpenELIS-Global/* → http://localhost:8080/OpenELIS-Global/*
  // NOTE: during development the backend runs in a local Docker container
  // which is exposed on localhost:8080. Previously we pointed at
  // "oe.openelis.org" which does not resolve on the host and produced
  // ENOTFOUND/504 errors. Using localhost ensures the proxy can reach the
  // container once it's started.
  app.use(
    '/api/OpenELIS-Global',
    createProxyMiddleware({
      target: 'http://localhost:8080',
      changeOrigin: true,
      pathRewrite: {
        '^/api/OpenELIS-Global': '/OpenELIS-Global',
      },
    })
  );

  // Route 2: Legacy /rest/* → http://localhost:8080/OpenELIS-Global/rest/* (fallback)
  app.use(
    '/rest',
    createProxyMiddleware({
      target: 'http://localhost:8080',
      changeOrigin: true,
      pathRewrite: {
        '^/rest': '/OpenELIS-Global/rest',
      },
    })
  );
};
