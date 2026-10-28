# Activar Google Maps

La base local de calles ya permite usar el demo sin internet ni cuenta. Para usar cartografía Google necesitas una clave propia y conexión.

1. Revisa la opción oficial de clave de demostración: <https://developers.google.com/maps/documentation/javascript/demo-key>. Está destinada a prototipos, tiene funciones y cuotas limitadas y requiere cuenta personal de Google elegible. No aceptamos condiciones ni creamos cuentas por ti.
2. Si usas una clave estándar, configura Maps JavaScript API según <https://developers.google.com/maps/documentation/javascript/get-api-key>. La configuración estándar requiere facturación; revisa cuotas y cargos antes de activarla.
3. Restringe la clave a Maps JavaScript API y a tus orígenes web autorizados, incluyendo los que uses para desarrollo, por ejemplo `http://localhost:8000/*` y `http://127.0.0.1:8000/*`. Cambia el puerto si corresponde. No uses una clave sin restricciones.
4. En `landing_page`, copia `maps.example.json` y nómbralo `maps.local.json`.
5. Escribe la clave como valor de `google_maps_api_key`, entre comillas. No pegues la clave en un chat, captura o README.
6. Reinicia el servidor Python y recarga la web. El selector del mapa permitirá Google Maps y lo abrirá por defecto.

Alternativa: define `GOOGLE_MAPS_API_KEY` en tu entorno local antes de iniciar Python. No hay que instalar paquetes adicionales.

`maps.local.json` está en `.gitignore`. La clave web se entrega al navegador; su protección depende de las restricciones de Google, no de ocultarla en el código. Esta es la forma normal de usar una clave para mapas web.

La capa de calor usa nuestra implementación Canvas sobre `OverlayView`. Las celdas y las huellas DBSCAN usan rectángulos de la API. No dependemos de la antigua HeatmapLayer retirada por Google. La documentación de Google es la referencia para los permisos y límites concretos de cada clave. Si la clave de demostración no admite la función requerida, conserva el mapa local o utiliza una clave estándar correctamente configurada.

Si Google no carga o rechaza la clave, aparece un aviso y se intenta mostrar la base local. No se ha realizado una prueba con una clave activa en esta entrega. Prueba tu configuración antes de exponer.

Fuentes consultadas el 28/10/2026:
- <https://developers.google.com/maps/documentation/javascript/demo-key>
- <https://developers.google.com/maps/documentation/javascript/get-api-key>
- <https://developers.google.com/maps/documentation/javascript/customoverlays>
- <https://developers.google.com/maps/deprecations>
