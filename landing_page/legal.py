"""Clasificación orientativa de hurtos y robos según el Código Penal peruano.

La persona que reporta no elige el artículo: responde preguntas simples
(¿hubo violencia o amenaza?, ¿participaron dos o más personas?, etc.) y estas
reglas sugieren la figura legal. La revisión humana confirma o corrige.
No reemplaza la calificación de la Policía, la Fiscalía ni un juez.
"""
import json
from analytics import parse_time, value_band, FALTA_LIMIT

LEGAL_CLASSES = {
    'falta_hurto': {'label': 'Falta por hurto', 'article': 'Art. 444', 'family': 'hurto',
                    'penalty': 'Servicios comunitarios o días-multa',
                    'definition': f'Hurto simple de un bien que no supera el 10% de la UIT (S/ {FALTA_LIMIT}).'},
    'hurto_simple': {'label': 'Hurto simple', 'article': 'Art. 185', 'family': 'hurto',
                     'penalty': '1 a 3 años',
                     'definition': 'Apoderamiento de un bien ajeno sin violencia ni amenaza.'},
    'hurto_agravado': {'label': 'Hurto agravado', 'article': 'Art. 186', 'family': 'hurto',
                       'penalty': '3 a 15 años según la agravante',
                       'definition': 'Hurto con agravantes: de noche, con destreza o escalamiento, en inmueble habitado, '
                                     'por dos o más personas, sobre un vehículo u organización criminal.'},
    'hurto_uso': {'label': 'Hurto de uso', 'article': 'Art. 187', 'family': 'hurto',
                  'penalty': 'No mayor de 1 año',
                  'definition': 'Sustracción de un bien mueble para usarlo momentáneamente y devolverlo.'},
    'robo': {'label': 'Robo', 'article': 'Art. 188', 'family': 'robo',
             'penalty': '3 a 8 años',
             'definition': 'Apoderamiento de un bien con violencia física o amenaza de un peligro inminente.'},
    'robo_agravado': {'label': 'Robo agravado', 'article': 'Art. 189', 'family': 'robo',
                      'penalty': '12 a 20 años, 20 a 30 años o cadena perpetua',
                      'definition': 'Robo con agravantes: a mano armada, con lesiones, de noche, por dos o más personas, '
                                    'en inmueble habitado, usando o sobre un vehículo, u organización criminal.'},
}
FAMILIES = {'hurto': 'Hurtos', 'robo': 'Robos'}

FORCES = {'sin_violencia': 'Sin violencia ni amenaza', 'amenaza': 'Con amenaza',
          'violencia': 'Con violencia física', 'sin_dato': 'No indicado'}

# Hechos que puede declarar quien reporta. 'organizacion' solo lo marca la revisión.
CIRCUMSTANCES = {
    'dos_o_mas': 'Participaron dos o más personas',
    'casa_habitada': 'Dentro de una vivienda habitada',
    'destreza': 'Con destreza, escalamiento o rotura de puertas, ventanas o candados',
    'vehiculo_medio': 'Los autores usaron un vehículo (moto, auto)',
    'uso_momentaneo': 'El bien fue devuelto o abandonado tras usarlo',
    'organizacion': 'Indicios de organización criminal',
}


def circumstances(report):
    raw = report.get('circumstances') or []
    if isinstance(raw, str):
        try:
            raw = json.loads(raw)
        except ValueError:
            raw = []
    return [c for c in raw if c in CIRCUMSTANCES]


def is_night(report):
    """Aproximación de 'durante la noche' por la hora del hecho (18:00-05:59, hora de Perú)."""
    hour = parse_time(report['occurred_at']).hour
    return hour >= 18 or hour < 6


def suggest(report):
    """Figura legal sugerida con los motivos que la explican."""
    force = report.get('force') or 'sin_dato'
    facts = set(circumstances(report))
    night = is_night(report)
    vehicle_target = report.get('subtype') == 'vehiculo'
    violent = force in {'amenaza', 'violencia'} or report.get('weapon') or report.get('injury')
    reasons, confidence = [], 'media'

    if force == 'sin_dato' and not violent:
        # Hasta octubre de 2026 el demo solo registraba robos; sin dato se mantiene esa lectura.
        violent = True
        confidence = 'baja'
        reasons.append('Sin dato de violencia: registro anterior, se asume robo')

    if violent:
        if force == 'violencia':
            reasons.append('Violencia física')
        elif force == 'amenaza':
            reasons.append('Amenaza')
        aggravants = []
        if report.get('weapon'):
            aggravants.append('A mano armada')
        if report.get('injury'):
            aggravants.append('Con lesiones')
        if night:
            aggravants.append('Durante la noche (por la hora)')
        if 'dos_o_mas' in facts:
            aggravants.append('Dos o más personas')
        if 'casa_habitada' in facts:
            aggravants.append('En inmueble habitado')
        if 'vehiculo_medio' in facts:
            aggravants.append('Usando un vehículo')
        if vehicle_target:
            aggravants.append('Sobre vehículo automotor')
        if 'organizacion' in facts:
            aggravants.append('Organización criminal')
        key = 'robo_agravado' if aggravants else 'robo'
        return _result(key, reasons + aggravants, confidence)

    if 'uso_momentaneo' in facts:
        return _result('hurto_uso', ['Sin violencia', 'Se usó y devolvió o abandonó el bien'], confidence)

    reasons.append('Sin violencia ni amenaza')
    aggravants = []
    if night:
        aggravants.append('Durante la noche (por la hora)')
    if 'destreza' in facts:
        aggravants.append('Con destreza o escalamiento')
    if 'casa_habitada' in facts:
        aggravants.append('En inmueble habitado')
    if 'dos_o_mas' in facts:
        aggravants.append('Dos o más personas')
    if vehicle_target:
        aggravants.append('Sobre vehículo automotor')
    if 'organizacion' in facts:
        aggravants.append('Organización criminal')
    if aggravants:
        return _result('hurto_agravado', reasons + aggravants, confidence)

    band = value_band(report)
    if band == 'bajo':
        return _result('falta_hurto', reasons + [f'Valor hasta 10% de la UIT (S/ {FALTA_LIMIT})'], confidence)
    if band == 'sin_dato':
        return _result('hurto_simple', reasons + [f'Valor no indicado: si no supera S/ {FALTA_LIMIT} sería falta (Art. 444)'], 'baja')
    return _result('hurto_simple', reasons + [f'Valor mayor a S/ {FALTA_LIMIT}'], confidence)


def _result(key, reasons, confidence):
    info = LEGAL_CLASSES[key]
    return {'key': key, 'label': info['label'], 'article': info['article'], 'family': info['family'],
            'penalty': info['penalty'], 'reasons': reasons, 'confidence': confidence, 'confirmed': False}


def classify(report):
    """Figura vigente: la confirmada en revisión o, si no hay, la sugerida."""
    suggested = suggest(report)
    confirmed = report.get('legal_class')
    if confirmed in LEGAL_CLASSES:
        info = LEGAL_CLASSES[confirmed]
        return {**suggested, 'key': confirmed, 'label': info['label'], 'article': info['article'],
                'family': info['family'], 'penalty': info['penalty'], 'confirmed': True,
                'suggested': suggested['key']}
    return {**suggested, 'suggested': suggested['key']}


def family(report):
    return classify(report)['family']


def distribution(rows):
    counts = {k: 0 for k in LEGAL_CLASSES}
    for r in rows:
        counts[classify(r)['key']] += 1
    total = len(rows)
    return [{'key': k, 'label': v['label'], 'article': v['article'], 'family': v['family'], 'count': counts[k],
             'percent': round(100 * counts[k] / total, 1) if total else 0} for k, v in LEGAL_CLASSES.items()]
