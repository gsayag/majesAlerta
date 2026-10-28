# Validación del demo 2.0

Comprobaciones realizadas el 28/10/2026 en Linux/Python y Chromium. Los resultados se refieren a datos ficticios y escenarios controlados.

## Pruebas automatizadas

Comando: `python3 -m unittest discover -s tests -v`.

Resultado: **26 pruebas correctas**. Incluyen registro, validación de entradas, seguimiento, revisión, acceso a evidencia y métricas, estadísticas, DBSCAN, comparación de periodos, matriz temporal, agregación espacial, recurrencia, mediana de revisión, emparejamiento uno a uno y límites del laboratorio.

Se comprobó la sintaxis JavaScript de los módulos modificados con `node --check`. Node se usó para desarrollo; no es un requisito para ejecutar el demo.

## Comprobaciones funcionales y visuales

- Panel público: filtros de 7/30 días, pestañas, selección de modalidades, capas y ficha de agrupación.
- Mapa local: calles reales visibles, atribución visible, capa de calor y ajuste de tamaño; no depende de un servidor de teselas.
- Laboratorio: escenario por defecto, cambio de parámetros y comparación de 100/300/600/1000 puntos.
- PDF: el navegador genera un documento de cuatro páginas y muestra el enlace Guardar PDF. El mismo generador se ejecutó en un entorno JavaScript local; se renderizaron e inspeccionaron las cuatro páginas, sin recortes. `EJEMPLO_INFORME.pdf` es ese archivo de prueba.
- La captura automatizada del evento de descarga del navegador no finalizó; no se da por verificada esa descarga de extremo a extremo. Prueba Guardar PDF en tu navegador antes de exponer. El ejemplo incluido sirve como respaldo para mostrar el formato.
- Gestión privada: autenticación y métricas cubiertas por pruebas HTTP; no se completó una revisión visual de la sesión autenticada en el navegador de pruebas.

## Límites de esta verificación

No se probó una clave activa de Google Maps, Windows/Android Studio, un teléfono físico ni conexión con la app Android. No se ejecutaron pruebas de carga de producción, auditoría de seguridad ni una evaluación de usabilidad con personas. El diseño es adaptable, pero no se realizó una matriz completa de dispositivos.

El tiempo del laboratorio mide DBSCAN sobre una muestra. Cambia por equipo, carga y parámetros; no representa latencia de la plataforma completa. Con semilla 42, 300 puntos, radio 250 m y mínimo 4, el escenario controlado recupera los tres grupos de referencia. Ese resultado no es una estimación de precisión sobre robos reales.

Antes de la exposición: abre `landing_page`, ejecuta `py -3 server.py`, recorre `GUIA_EXPOSICION.md` y conserva el ejemplo PDF. La cartografía local funciona sin clave de Google.
