# Majes Alerta · Observatorio 2.0

Demo universitario de reportes de **robos**, análisis territorial y evaluación de DBSCAN, para el área ilustrativa de El Pedregal, distrito de Majes, Arequipa. Los reportes son ficticios. No se envían denuncias a autoridades.

## Abrir en Windows

Requiere **Python 3.10 o posterior**. No requiere `pip`, Node ni cuentas para funcionar con su mapa local.

1. Descomprime todo el ZIP.
2. Abre `landing_page` en Visual Studio Code.
3. Abre una terminal y ejecuta:

```powershell
py -3 server.py
```

4. Abre <http://localhost:8000> y deja la terminal abierta.

No abras `web/index.html` directamente. Si el puerto está ocupado, detén la ejecución anterior con Ctrl+C o usa `py -3 server.py --port 8001` y abre <http://localhost:8001>.

macOS/Linux: `python3 server.py`. El BAT anterior sigue incluido, pero no es necesario usarlo.

## Reemplazar la versión anterior en tu repositorio

1. Detén el servidor anterior.
2. Haz una copia de respaldo de tu carpeta `landing_page`, especialmente `data/demo.sqlite3` si deseas conservar los casos.
3. Copia los archivos de esta nueva carpeta `landing_page` dentro de la carpeta del mismo nombre en tu repositorio, reemplazando los archivos de código.
4. No crees una carpeta `landing_page` dentro de otra. `server.py` debe quedar en `tu-repositorio/landing_page/server.py`.
5. Conserva `data/` y `maps.local.json` si ya los tenías. El ZIP no incluye ninguno de los dos.
6. `app_mobil` permanece independiente. Este paquete no contiene ni reemplaza tu proyecto Android.

La base anterior es compatible. Si contiene fechas antiguas puede haber filtros vacíos. Para mostrar datos recientes, respalda la base y arranca una base nueva, o usa `py -3 server.py --db data/presentacion.sqlite3`. Nunca borres una base que quieras conservar.

## Qué hay en cada pantalla

| Ruta | Pantalla | Funciones |
| --- | --- | --- |
| `/` | Portada | Presentación y acceso al observatorio. |
| `/panel` | Vista pública | Filtros sincronizados, calor, celdas, agrupaciones DBSCAN, recurrencia, comparación de periodos, matriz día/hora, modalidades, PDF y CSV. |
| `/gestion` | Revisión privada | Prioridad explicable, coincidencias, validación, duplicado, descarte y cierre. |
| `/gestion-datos` | Gestión y calidad privada | Estados, prioridades, pendientes, mediana hasta primera revisión y calidad de campos/evidencias. |
| `/laboratorio` | Laboratorio público sintético | Radio/mínimo configurables, semilla, escenarios conocidos, cobertura, precisión, F1 y tiempos por tamaño. |
| `/metodologia` | Criterios | Cómo leer las métricas y activar Google Maps. |
| `/reportar` | Formulario web de prueba | Registro de un caso ficticio para probar la revisión. No es la app Android. |
| `/seguimiento` | Seguimiento | Código privado aleatorio y estados del reporte. |

Contraseña pública del demo: **`MajesDemo2026!`**. Cambiable con `DEMO_ADMIN_PASSWORD`. La protección de sesión es solo para demostración local, no para producción.

## Mapas

- **Sin clave:** calles reales de OpenStreetMap incluidas en `web/majes-streets.geojson`, dibujadas con Leaflet. Contiene 873 vías/tramos con geometría, no imágenes descargadas de un servidor de teselas. Esta base local funciona sin internet después de extraer el ZIP; cubre solo el área del demo, no todo el distrito.
- **Con clave de Google:** activa Maps JavaScript API según `docs/GOOGLE_MAPS.md`. El mapa usa sus calles y controles, con nuestras capas de calor/celdas/DBSCAN. Necesita internet. La integración está implementada, pero no se probó contra una cuenta con clave activa.
- **Vista esquemática:** alternativa manual de coordenadas sin calles. No se confunde con cartografía real.
- Si Google falla al cargar, se cambia a la base local; no se incrusta una clave compartida.
- El calor es una superposición propia de conteos por celda. No usa la antigua `google.maps.visualization.HeatmapLayer`, no es KDE calibrado y no representa probabilidad. Su referencia de saturación es 20 reportes/celda; la superposición de halos y el zoom también afectan el color.
- Las huellas DBSCAN son conjuntos de celdas publicables de sus miembros, no polígonos exactos ni sectores oficiales. Pueden superponerse. Los números de agrupación no son identificadores estables entre consultas.

Fuente y licencia de calles: `docs/CARTOGRAFIA.md`. No se incluye búsqueda de direcciones ni rutas seguras.

## Indicadores y fórmulas

- Solo `validado` y `cerrado` entran al panel. Duplicados/descartados/pendientes no se cuentan.
- Fecha y hora corresponden al hecho, con zona horaria Perú (UTC-5).
- Ventanas de 7, 30 o 90 días calendario hasta hoy inclusive, frente a la ventana inmediatamente anterior de igual duración. Hoy puede estar incompleto; los conteos iniciales de 180 reportes sintéticos cubren unos 84 días, por lo que una comparación de 90 días no tiene historial anterior completo.
- Cambio: actual - anterior. Porcentaje: (actual - anterior) / anterior × 100; si anterior=0 se informa sin base.
- Celdas: 0,006 grados, aprox. 640 × 670 m. Solo se publican las de al menos 3 registros. No garantiza anonimato, particularmente frente a consultas repetidas. Solo debe usarse con datos ficticios.
- Recurrencia: bloques consecutivos de 7 días desde el inicio del filtro. Al menos 2 bloques con 3 o más reportes. El último bloque puede ser parcial. Valores espaciales previos de 0 a 2 se muestran como “Reservado”.
- Mediana de revisión: primera transición diferente de recibido menos creación; se excluyen intervalos negativos y se muestra el número de registros usados. No equivale a atención policial ni tiempo de resolución.
- Calidad: campos presentes, coordenadas dentro del área, foto opcional, duplicados confirmados / total de la cohorte por fecha del hecho. No mide verdad del reporte.
- Prioridad: misma regla del demo anterior, máximo 100 (base 10, arma 40, lesión 15, recencia hasta 20, recurrencia cercana hasta 15). No es AHP aplicado ni un modelo entrenado.

## Laboratorio

Puntos: 100, 300, 600 o 1000; radio 50-1000 metros; mínimo 3-30 puntos; semilla 0-9999. Usa tres discos etiquetados y aproximadamente 10% de ruido uniforme. El ruido puede caer cerca de un grupo o formar grupos accidentales; es parte del ensayo.

DBSCAN usa Haversine y una implementación O(n²) para muestras pequeñas. Una agrupación detectada coincide con una referencia si Jaccard ≥ 0,5. Se maximiza el número de emparejamientos uno a uno. Precisión=coincidencias/detectadas; cobertura=coincidencias/referencias; F1=media armónica. No se evalúa desempeño policial ni predicción. El tiempo mide solo agrupación, no generación de datos, red ni renderizado.

El escenario no cambia la base de reportes ni se mezcla con las estadísticas. SUS, AHP con expertos y validación con datos de campo quedan pendientes.

## Exportaciones

- PDF local: pulsa **Generar informe PDF** y luego **Guardar PDF**. Generado con jsPDF: filtros, tablas, comparación temporal, modalidades, matriz temporal, recurrencia y metodología. No incorpora una captura de Google Maps ni fotos privadas. El número de páginas depende de la longitud de las tablas. Se incluye un ejemplo de cuatro páginas en `docs/EJEMPLO_INFORME.pdf`.
- CSV público: celdas agregadas del filtro.

## Arquitectura

- `server.py`: rutas HTTP, validación, sesiones, configuración.
- `store.py`: SQLite, historial y evidencia privada.
- `analytics.py`: distancias, DBSCAN, filtros y prioridades.
- `insights.py`: agregados públicos y gestión privada.
- `evaluation.py`: escenarios y métricas de referencia.
- `web/app.js`: navegación, portada y flujo de reportes.
- `web/analysis-ui.js`: paneles analíticos y laboratorio.
- `web/map.js`: adaptadores Leaflet/local y Google; `grid-map.js`: alternativa esquemática.
- `web/report-pdf.js`: PDF generado en el navegador.

Sin servidor compartido con Android en esta entrega. El próximo paso sería una API común y migración a PostgreSQL/PostGIS; no es necesario reescribir los gráficos para hacerlo.

## Validación y límites

Ejecuta `python3 -m unittest discover -s tests -v`: 26 pruebas cubren registro/revisión, acceso, datos agregados, filtros, matriz temporal, recurrencia, métricas y escenarios DBSCAN. Las comprobaciones visuales y límites específicos están en `docs/VALIDACION.md`.

180 registros ficticios se crean una vez por base. La base y fotos de las pruebas no se incluyen. La web trabaja con el servidor local Python/SQLite; no debe publicarse tal cual para recibir datos personales reales. No hay monitoreo GPS continuo, notificaciones, análisis automático de fotografías, predicción, despacho de patrullas ni integración municipal.
