# Cartografía local y atribución

El archivo `web/majes-streets.geojson` contiene una extracción de **873 vías/tramos** de OpenStreetMap, obtenida el 28/10/2026. Es cartografía de referencia real; las incidencias del demo son ficticias.

- Fuente: © OpenStreetMap contributors.
- API de extracción: `https://api.openstreetmap.org/api/0.6/map?bbox=-72.218,-16.386,-72.154,-16.329`.
- Selección: vías con etiqueta `highway`. Solo se conservan nombre, clase de vía, referencia y geometría; no hay información de denunciantes.
- Licencia de la base derivada: Open Data Commons Open Database License (ODbL) 1.0: <https://opendatacommons.org/licenses/odbl/1-0/>.
- Atribución y derechos: <https://www.openstreetmap.org/copyright>.

Se distribuye el GeoJSON editable completo bajo ODbL, junto con la atribución visible en el mapa. Mantén esa atribución y la licencia al redistribuirlo.

Esta es una copia local de datos vectoriales, no una descarga masiva ni una caché de imágenes del servidor de teselas. Solo cubre el área de demostración, no todo Majes. Algunas geometrías pueden extenderse fuera del rectángulo porque son vías completas que lo atraviesan. La exactitud, completitud y nombres dependen de los aportes de OpenStreetMap. No se utiliza como límite administrativo oficial ni cartografía policial.

La base local no se actualiza sola. Google Maps, cuando se configura una clave, aporta su propia cartografía en línea y sus atribuciones; el GeoJSON local no se dibuja encima de Google.
