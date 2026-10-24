# Majes Alerta - demo universitario

Prototipo funcional para reportar **únicamente robos**, revisar los registros y explorar concentraciones geográficas y temporales. El ámbito ilustrativo es El Pedregal, distrito de Majes, Caylloma, Arequipa, según el artículo proporcionado. No es un sistema municipal oficial.

## Abrir en Windows

1. Extrae **todo el ZIP** en una carpeta.
2. Instala **Python 3.10 o posterior** si aún no lo tienes: https://www.python.org/downloads/ . Activa `Add Python to PATH` durante la instalación.
3. Haz doble clic en `INICIAR_WINDOWS.bat`.
4. Abre http://127.0.0.1:8000 si el navegador no se abre solo.
5. Mantén abierta la ventana de la consola mientras pruebas el sistema. Para detenerlo, presiona `Ctrl+C`.

También puedes abrir una terminal dentro de la carpeta y ejecutar:

```powershell
py -3 server.py
```

En macOS o Linux:

```bash
python3 server.py
```

No necesitas Node, npm, pip, Docker, cuentas externas ni claves API. `package.json` solo facilita la vista previa de desarrollo; no es necesario para abrir el demo.

**No abras `web/index.html` con doble clic.** El formulario y el panel necesitan el servidor para compartir los datos.

## Qué puedes probar

| Pantalla | Ruta local | Función implementada |
| --- | --- | --- |
| Landing | `/` | Presenta el proyecto y enlaza los módulos. |
| Reporte móvil web | `/reportar` | Modalidad de robo, fecha/hora, descripción, ubicación, indicadores declarados y foto opcional. |
| Panorama público | `/panel` | Calor/celdas, filtros, serie diaria, franjas horarias, DBSCAN y exportación CSV agregada. |
| Revisión | `/gestion` | Prioridad explicable, candidatos a duplicado y cambio de estado con nota interna. |
| Seguimiento | `/seguimiento` | Consulta del estado y su historial mediante un código privado aleatorio. |

Contraseña inicial del área de revisión: **`MajesDemo2026!`**. Es una credencial pública de demostración, no una contraseña apta para producción. Puedes cambiarla al iniciar el servidor con la variable `DEMO_ADMIN_PASSWORD`.

## Demostración de 5 minutos

1. Abre **Explorar el mapa** y observa los indicadores de 30 días.
2. En **Reportar un robo**, selecciona una modalidad, completa una descripción ficticia, pulsa **Punto de prueba**, confirma el aviso y envía el formulario. La foto es opcional.
3. Guarda el identificador y el código de seguimiento. El nuevo registro queda **Recibido** y todavía no cuenta en las estadísticas.
4. En **Revisar reportes**, ingresa la contraseña del demo. Busca el identificador si no aparece seleccionado.
5. Revisa la explicación del puntaje, cambia el estado a **Validado**, escribe una nota y guarda.
6. En **Panorama territorial**, selecciona 30 días y todas las modalidades/horarios. El indicador aumenta al incluir el nuevo registro. Una celda solo aparece si tiene al menos 3 reportes validados.
7. Consulta el código en **Mi seguimiento**. Ahora debe aparecer la validación en el historial.
8. Prueba otros periodos, cambia entre **Calor** y **Celdas**, y descarga el CSV.

Para demostrar duplicados, registra dos casos ficticios de la misma modalidad, en el mismo punto y a una distancia temporal de menos de 30 minutos. El sistema solo propone coincidencias; la persona revisora decide. Al marcar uno como duplicado debes indicar el identificador del reporte original.

## Datos y mapa

- Se generan **180 reportes sintéticos** al crear una base nueva. Las fechas se calculan respecto al día de la primera ejecución. Ninguno describe un hecho real.
- Los registros se guardan en `data/demo.sqlite3`, incluso después de cerrar el navegador o reiniciar el servidor.
- La base utilizada durante las comprobaciones **no está incluida** en el ZIP. Se crea una base limpia al abrirlo.
- La cuadrícula, los códigos de celda y las coordenadas de prueba son ilustrativos. El rectángulo no es el límite oficial del distrito y el demo no cubre todo Majes.
- El mapa funciona sin internet en modo cuadrícula. **Ver calles** abre OpenStreetMap en otra pestaña y requiere conexión. El calor del demo permanece en la cuadrícula; no se superpone en esa pestaña externa. La carga de teselas de calles no se incluye porque el proveedor bloqueó la vista previa. En la versión final hay que integrar un proveedor de cartografía autorizado mediante Leaflet. No se descargan mapas para uso sin conexión.
- El calor representa un suavizado visual de conteos por celda. No es un KDE estadístico calibrado ni representa probabilidad de delito. La escala de color se ajusta al filtro actual: no compares colores de filtros diferentes como si fueran una escala absoluta.
- DBSCAN se calcula separadamente sobre las coordenadas de los reportes validados dentro del filtro: radio de 250 metros, mínimo 4 puntos, distancia Haversine. El periodo se filtra antes; no es ST-DBSCAN.
- Las celdas públicas son de 0.006 grados por lado, aproximadamente 640 x 670 m en esta latitud. Se omiten las que tienen menos de 3 reportes. Este umbral es una precaución del demo, **no una garantía de anonimato**.
- Las fotos se reducen y se vuelven a codificar a JPEG en el navegador, con máximo de 1 MB al recibirlas. Se guardan en SQLite y solo se sirven con sesión de revisión. La API del demo verifica tamaño y firma básica; una versión real debe decodificar, validar y eliminar metadatos también en el servidor.

Para comenzar otra demostración sin perder la anterior, detén el servidor y **renombra o respalda** `data/demo.sqlite3`; al iniciar se generarán otros 180 registros de prueba. Para una base nueva vacía usa:

```bash
python3 server.py --db data/demo_vacio.sqlite3 --empty
```

## Prioridad explicable

| Criterio ilustrativo | Puntos |
| --- | ---: |
| Base de revisión | 10 |
| Arma declarada | 40 |
| Lesión declarada | 15 |
| Hecho de hasta 24 horas / hasta 7 días / más antiguo | 20 / 10 / 0 |
| Otros reportes validados a 250 m, durante los 7 días anteriores al hecho | 3 por reporte, máximo 15 |

Puntaje máximo: 100. Alta: 60 o más; media: 30 a 59; baja: menos de 30. Los pesos y umbrales son **supuestos de demostración**: no provienen de una calibración, de AHP aplicado ni de una evaluación con especialistas. No se utiliza una foto como criterio para dar mayor prioridad. La puntuación ordena la revisión; no despacha patrullas ni toma decisiones operativas.

Estados: `recibido`, `validado`, `cerrado`, `duplicado`, `descartado`. Solo `validado` y `cerrado` alimentan las estadísticas. “Validado” significa revisión del registro dentro del prototipo, no verificación policial. “Cerrado” significa cierre del trámite del demo, no resolución del delito. Las notas internas no se publican en el seguimiento.

## Qué conecta la app y la web

Ambas interfaces consumen la **misma API** y esta usa una **única base de datos**. En este demo viven en el mismo servidor local. La base no está en el navegador y no se usan archivos independientes para cada pantalla.

1. La interfaz de reporte hace `POST /api/reports`.
2. El servidor valida los campos y guarda el registro en SQLite.
3. El área de revisión consulta `/api/admin/reports` y modifica el estado con `/api/admin/status`.
4. La web pública consulta `/api/stats`, que devuelve indicadores y ubicaciones agrupadas, sin descripciones, fotos ni coordenadas individuales.
5. **Actualizar** vuelve a consultar los datos; el demo no implementa WebSockets ni actualizaciones automáticas en segundo plano.

La landing explica el proyecto y conduce al reporte y al panel. No necesita otra base de datos. Las instrucciones para convertir este prototipo en la arquitectura del artículo están en `docs/INTEGRACION.md`.

## Usarlo desde un celular

Es una **web adaptable al celular**, no una APK ni una aplicación React Native terminada.

Para verlo en un teléfono en la misma red Wi-Fi, inicia explícitamente:

```bash
python3 server.py --host 0.0.0.0 --port 8000
```

Busca la dirección IPv4 de tu PC con `ipconfig` (Windows) y abre `http://IP_DE_TU_PC:8000` en el teléfono. Ambos dispositivos deben estar en la misma red; las reglas de esa red pueden impedir la conexión. La URL `127.0.0.1` en el teléfono apunta al propio teléfono, no a la PC.

El GPS del navegador normalmente necesita **HTTPS o localhost** y permiso del usuario. Sobre HTTP desde un teléfono puede no funcionar; usa **Punto de prueba** o marca el mapa. El GPS obtiene la posición actual, que debe confirmarse como lugar del hecho. Si estás fuera del área ilustrativa, la ubicación se rechaza.

## Estructura del código

| Archivo | Responsabilidad |
| --- | --- |
| `server.py` | API HTTP, validaciones, sesión local y archivos web. |
| `store.py` | SQLite, registros sintéticos, persistencia e historial. |
| `analytics.py` | DBSCAN, filtros, agregación, prioridad y coincidencias. |
| `web/app.js` | Landing, formulario, panel, revisión y seguimiento. |
| `web/map.js` | Mapa Web Mercator, calor, celdas, selección y enlace externo a calles. |
| `web/app.css` | Diseño adaptable a escritorio y celular. |
| `tests/test_demo.py` | Pruebas de API, análisis, acceso y persistencia. |

## Comprobaciones

Ejecuta en la carpeta del proyecto:

```bash
python3 -m unittest discover -s tests -v
```

Se incluyen 16 pruebas de integración y análisis. Cubren, entre otras cosas, registro-revisión-seguimiento, exclusión de pendientes/duplicados, agrupación pública, filtros, DBSCAN, puntaje, controles de acceso, evidencias privadas y persistencia. La interfaz de portada, panel, filtros, registro y seguimiento se comprobó en navegador de escritorio. También se revisaron formulario y panel en marcos de 390 px de ancho para comprobar el diseño móvil. El acceso de revisión está cubierto por las pruebas HTTP; no se probó en un dispositivo físico ni se generó una APK.

## Límites del prototipo

El servidor de biblioteca estándar, SQLite, las sesiones en memoria y el acceso compartido son para demostraciones locales con datos ficticios. No publiques este paquete tal cual para recibir denuncias reales. No incluye autenticación ciudadana, cola de reportes sin conexión, notificaciones, despacho de emergencias, infraestructura de producción ni integración efectiva con Supabase.

El análisis muestra patrones de los **reportes recibidos**, que pueden tener errores, sesgo de participación o subregistro. No demuestra una reducción de robos. La evaluación académica debe medir primero calidad de registro, facilidad de uso, detección de agrupaciones sobre datos controlados y utilidad para una revisión humana.

Fecha de preparación: 24 de octubre de 2026.
