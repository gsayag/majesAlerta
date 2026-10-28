# Alineación con GSTI - Artículo Hito 1

La implementación desarrolla los objetivos de captura, análisis espacial/temporal, priorización y evaluación técnica previstos en el documento. El documento original no fue modificado.

| Dimensión del documento | Evidencia en el demo 2.0 | Pendiente |
| --- | --- | --- |
| Captura estructurada | Formulario de prueba, ubicación, fecha/hora, evidencia opcional; app Android separada | Integración móvil/API y captura de precisión GPS en el modelo compartido |
| Zonas críticas | DBSCAN, fichas de agrupaciones y huellas aproximadas | Delimitación oficial y validación territorial |
| Patrones temporales | Matriz semanal/horaria y comparación entre periodos | Datos de campo suficientes y análisis de subregistro |
| Prioridad | Puntaje y explicación por criterio | Estimar pesos con especialistas; AHP real si se mantiene esa elección |
| Tasa de detección | Laboratorio con referencias sintéticas y coincidencia Jaccard | Validación externa y definición de criterios antes del experimento |
| Eficiencia | Medición de DBSCAN por tamaño de muestra | Repeticiones, distribución de tiempos, pruebas de carga de extremo a extremo |
| Usabilidad | Mayor tipografía, contraste, modo presentación | SUS aplicado a participantes; no hay puntuaciones inventadas |

Correcciones recomendadas del artículo:
1. Cambiar “robos y hurtos” del título por “robos” si ese es el alcance definitivo.
2. Retirar o reformular el párrafo y la referencia de residuos sólidos; no sustenta directamente este problema de robos.
3. Actualizar la selección tecnológica según la app Android real. El documento menciona Expo como selección preliminar; el repositorio actual usa Kotlin/Gradle. Android Studio es el entorno, no el lenguaje.
4. Distinguir la arquitectura objetivo (API compartida/PostGIS) del demo actual (Python/SQLite local, app separada).
5. Declarar que los resultados del laboratorio son sintéticos y no representativos de la criminalidad de Majes.

Título sugerido: Desarrollo de una plataforma basada en análisis geoespacial y temporal para identificar concentraciones y priorizar reportes de robos en el distrito de Majes, Caylloma, Arequipa.
