# Majes Alerta · Observatorio 2.0

Demo universitario de reportes de **robos y hurtos**, análisis territorial con IA y evaluación de DBSCAN, para el área ilustrativa de El Pedregal, distrito de Majes, Arequipa. Los reportes son ficticios. No se envían denuncias a autoridades.

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
| `/gestion` | Revisión privada | Prioridad explicable, figura legal sugerida y confirmable, coincidencias, validación, duplicado, descarte y cierre. |
| `/gestion-datos` | Gestión y calidad privada | Estados, prioridades, pendientes, mediana hasta primera revisión y calidad de campos/evidencias. |
| `/analisis` | Análisis con IA privado | La IA interpreta los datos y propone dónde actuar, qué hacer, quién y cuándo, con un indicador para medirlo. |
| `/acciones` | Seguimiento privado | Acciones preventivas registradas (desde la IA o a mano), estado, notas y evaluación antes/durante frente al resto del área. |
| `/laboratorio` | Laboratorio público sintético | Radio/mínimo configurables, semilla, escenarios conocidos, cobertura, precisión, F1 y tiempos por tamaño. |
| `/metodologia` | Criterios | Cómo leer las métricas y activar Google Maps. |
| `/reportar` | Formulario web de prueba | Registro de un caso ficticio. El canal normal del ciudadano es la app Android; la web es sobre todo para administración. |
| `/seguimiento` | Seguimiento | Código privado aleatorio y estados del reporte. |

Contraseña pública del demo: **`MajesDemo2026!`**. Cambiable con `DEMO_ADMIN_PASSWORD`. La protección de sesión es solo para demostración local, no para producción.

## Conectar la app Android

La app de `app_mobil` envía cada reporte nuevo a `POST /api/reports` y consulta su estado con `POST /api/track`. Los reportes llegan a `/gestion` como `recibido`.

1. Inicia el servidor. Para el **emulador** basta `python3 server.py` (la app usa `http://10.0.2.2:8000`, que apunta a tu PC). Para un **celular físico** en la misma Wi-Fi: `python3 server.py --host 0.0.0.0` y en la app, *Ajustes → Dirección del servidor*, escribe `http://IP_DE_TU_PC:8000`.
2. En la app, *Ajustes → Guardar y probar* confirma la conexión.
3. Crea un reporte con el punto ficticio (la ubicación debe estar dentro del área de El Pedregal). Si no hay conexión queda **pendiente** y se reintenta al abrir la app o con *Enviar pendientes*.
4. Valida el caso en `/gestion` y en la app toca *Actualizar estado*.

Solo para demo: HTTP sin cifrar y sin cuentas. Un reintento tras un corte de red puede duplicar un reporte; la revisión lo marca como posible coincidencia.

## Hurto o robo (Código Penal)

Quien reporta no elige el artículo. Responde preguntas simples (¿hubo violencia o amenaza?, ¿participaron dos o más personas?, ¿fue en una vivienda habitada?, ¿el bien fue devuelto tras usarlo?, valor aproximado) y `legal.py` sugiere la figura:

| Figura | Artículo | Regla del demo |
| --- | --- | --- |
| Falta por hurto | 444 | Hurto simple de un bien de hasta 10% de la UIT (S/ 535 con UIT de S/ 5 350). |
| Hurto simple | 185 | Sin violencia ni amenaza y sin agravantes. |
| Hurto agravado | 186 | Sin violencia, pero de noche, con destreza o escalamiento, en vivienda habitada, por dos o más personas, sobre un vehículo u organización criminal. |
| Hurto de uso | 187 | Sin violencia; el bien se usó y se devolvió o abandonó. |
| Robo | 188 | Con violencia o amenaza, sin agravantes. |
| Robo agravado | 189 | Con violencia o amenaza y a mano armada, con lesiones, de noche, por dos o más personas, en vivienda habitada, usando o sobre un vehículo u organización criminal. |

- «De noche» se aproxima por la hora del hecho (18:00 a 05:59). La UIT está en `analytics.py` y debe actualizarse cada año.
- En `/gestion` el revisor ve la figura sugerida con sus motivos y la confirma o corrige. La confirmada prevalece.
- Los registros anteriores sin dato de violencia se mantienen como robo (el demo antes solo registraba robos), con confianza baja.
- El panel y la gestión filtran por **tipo de delito** (robos / hurtos). La violencia o amenaza suma 2 al índice de daño.
- Es orientativo: no reemplaza la calificación policial ni fiscal.

## Análisis con IA

`/analisis` (privado) responde «¿dónde actuar primero?». `ai_analysis.py` arma un resumen **agregado** por zona (reportes, índice de daño, robos/hurtos, figuras legales, franja y modalidad predominantes, recurrencia, agrupaciones DBSCAN, horas pico, tendencia) y se lo entrega a Claude, que devuelve en JSON con esquema fijo: lectura general, hasta 5 zonas con diagnóstico, acciones (qué, quién, cuándo) e indicador para medir el efecto, patrones y limitaciones.

- **No se envían** descripciones, identificadores, fotos ni coordenadas exactas. La página permite ver exactamente lo que recibió la IA.
- Para activarla (opcional): `pip install anthropic` y `ANTHROPIC_API_KEY` (o `ant auth login`). Modelo por defecto `claude-opus-5-5`; se cambia con `MAJES_AI_MODEL`.
- Sin el paquete o sin credenciales, la misma página muestra un **análisis por reglas** con el mismo formato y lo indica. El demo sigue funcionando sin internet.
- Cada análisis se guarda 30 minutos por filtro y datos; «Regenerar» fuerza uno nuevo. Cada llamada a la IA consume créditos de la API.
- Las recomendaciones requieren criterio humano: no predicen delitos ni señalan personas.

## Seguimiento de acciones

Cierra el ciclo **dato → decisión → resultado**. En `/analisis` cada acción recomendada tiene un botón «Registrar»; también se puede registrar a mano en `/acciones` (zona, acción, responsable, fechas y franja). Por defecto dura 4 semanas.

Cómo se evalúa (`actions.py`):
- Se cuentan los reportes **validados** de la zona (y de la franja, si se eligió) durante la acción y en el mismo número de días justo antes de empezar.
- Ese cambio se compara con el **resto del área** en las mismas fechas. Si todo bajó por igual, no se atribuye la baja a la acción.
- Veredictos: muy pronto (menos de 7 días), pocos datos (menos de 5 reportes), aumentaron, bajaron más que el resto, bajaron como el resto, sin cambio.
- Es una lectura descriptiva para decidir si mantener, ajustar o cambiar la acción: **no prueba causalidad**.

La IA recibe las acciones registradas y su resultado, para no repetir lo que ya se hace y proponer ajustes donde no funciona.

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
- Prioridad: máximo 100 (base 10, arma 30, lesión 15, valor de lo robado hasta 15, recencia hasta 15, recurrencia cercana hasta 15). No es AHP aplicado ni un modelo entrenado.
- Índice de daño: 1 base + valor de lo robado (medio 1, alto 3, más de S/ 10 000: 5) + arma 3 + lesión 4. Un robo de vehículo sin valor indicado se asume alto.
- Alertas para decidir (`/gestion-datos`): un reporte alerta solo si tiene lesión, es robo de vehículo o su daño es ≥ 6. Las zonas se ordenan por daño acumulado (media ≥ 6, alta ≥ 12); tres o más robos de bajo impacto quedan en vigilancia. Así un único robo de auto aparece aunque no haya otros reportes cerca, y dos robos de lentes no disparan una alerta. Los pesos son supuestos del demo para calibrar con expertos.

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
- `insights.py`: agregados públicos, gestión privada y alertas por gravedad.
- `legal.py`: clasificación orientativa hurto/robo según el Código Penal.
- `ai_analysis.py`: interpretación con IA (Claude) y análisis por reglas de respaldo.
- `actions.py`: acciones preventivas y evaluación antes/durante frente al resto del área.
- `web/actions-ui.js`: página de seguimiento de acciones.
- `evaluation.py`: escenarios y métricas de referencia.
- `web/app.js`: navegación, portada y flujo de reportes.
- `web/analysis-ui.js`: paneles analíticos y laboratorio.
- `web/map.js`: adaptadores Leaflet/local y Google; `grid-map.js`: alternativa esquemática.
- `web/report-pdf.js`: PDF generado en el navegador.

La app Android usa esta misma API (ver «Conectar la app Android»). El próximo paso sería migrar a PostgreSQL/PostGIS con cuentas y roles; no es necesario reescribir los gráficos para hacerlo.

## Validación y límites

Ejecuta `python3 -m unittest discover -s tests -v`: 26 pruebas cubren registro/revisión, acceso, datos agregados, filtros, matriz temporal, recurrencia, métricas y escenarios DBSCAN. Las comprobaciones visuales y límites específicos están en `docs/VALIDACION.md`.

180 registros ficticios se crean una vez por base. La base y fotos de las pruebas no se incluyen. La web trabaja con el servidor local Python/SQLite; no debe publicarse tal cual para recibir datos personales reales. No hay monitoreo GPS continuo, notificaciones, análisis automático de fotografías, predicción, despacho de patrullas ni integración municipal.
