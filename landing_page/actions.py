"""Acciones preventivas: registrar qué se hizo, dónde y cuándo, y medir el efecto.

Cierra el ciclo dato → decisión → resultado. La evaluación compara la zona
antes y después del inicio de la acción, y contrasta ese cambio con el resto
del área en las mismas fechas (si todo bajó, la acción no explica la baja).
Es una lectura descriptiva: no prueba que la acción causó el cambio.
"""
import re
from datetime import date, timedelta

from analytics import BOUNDS, VALID_STATUSES, harm, parse_time
from insights import STEP, cell_info, cell_key

ACTION_STATUSES = {'planificada': 'Planificada', 'en_curso': 'En curso', 'finalizada': 'Finalizada', 'cancelada': 'Cancelada'}
SOURCES = {'ia': 'Recomendada por IA', 'reglas': 'Recomendada por reglas', 'manual': 'Registrada manualmente'}
BANDS = ['00:00–05:59', '06:00–11:59', '12:00–17:59', '18:00–23:59']
DEFAULT_DAYS = 28  # 4 semanas, el plazo que usa el indicador de la IA
MIN_DAYS = 7
MIN_REPORTS = 5


def parse_zone(name):
    """'Celda E4' → clave de celda (col, fila). None si no corresponde al área."""
    m = re.fullmatch(r'Celda ([A-Z])(\d{1,2})', name or '')
    if not m:
        return None
    key = (ord(m.group(1)) - 65, int(m.group(2)) - 1)
    info = cell_info(key)
    inside = (BOUNDS['south'] - STEP <= info['lat'] <= BOUNDS['north'] + STEP and
              BOUNDS['west'] - STEP <= info['lng'] <= BOUNDS['east'] + STEP)
    return key if inside else None


def validate(data, today):
    """Valida una acción nueva. Devuelve el diccionario limpio o lanza ValueError."""
    zone = data.get('zone')
    if parse_zone(zone) is None:
        raise ValueError('Selecciona una zona válida del área.')
    title = data.get('title')
    if not isinstance(title, str) or not 6 <= len(title.strip()) <= 300:
        raise ValueError('Describe la acción en 6 a 300 caracteres.')
    responsible = data.get('responsible')
    if not isinstance(responsible, str) or not 2 <= len(responsible.strip()) <= 120:
        raise ValueError('Indica quién es responsable.')
    try:
        start = date.fromisoformat(data.get('start_date') or today.isoformat())
        end = date.fromisoformat(data['end_date']) if data.get('end_date') else start + timedelta(days=DEFAULT_DAYS - 1)
    except (TypeError, ValueError):
        raise ValueError('Usa fechas válidas (AAAA-MM-DD).')
    if start > today + timedelta(days=60) or start < today - timedelta(days=366):
        raise ValueError('La fecha de inicio debe estar dentro del último año o los próximos 60 días.')
    if end < start or (end - start).days > 365:
        raise ValueError('La fecha de fin debe ser posterior al inicio y dentro de un año.')
    band = str(data.get('band', 'all'))
    if band not in {'all', '0', '1', '2', '3'}:
        raise ValueError('Franja horaria no válida.')
    source = data.get('source', 'manual')
    if source not in SOURCES:
        raise ValueError('Origen no válido.')
    when = data.get('schedule', '')
    if not isinstance(when, str) or len(when) > 200:
        raise ValueError('El horario debe tener hasta 200 caracteres.')
    return {'zone': zone, 'title': title.strip(), 'responsible': responsible.strip(), 'schedule': when.strip(),
            'start_date': start.isoformat(), 'end_date': end.isoformat(), 'band': band, 'source': source,
            'status': 'en_curso' if start <= today else 'planificada'}


def _count(rows, key, band, first, last, inside):
    """Reportes validados y daño entre dos fechas (inclusive), dentro o fuera de la celda."""
    n = total = 0
    for r in rows:
        if r['status'] not in VALID_STATUSES:
            continue
        t = parse_time(r['occurred_at'])
        if not first <= t.date() <= last or (band != 'all' and t.hour // 6 != int(band)):
            continue
        if (cell_key(r) == key) != inside:
            continue
        n += 1
        total += harm(r)['score']
    return n, total


def _pct(before, after):
    return round((after - before) / before * 100, 1) if before else None


def evaluate(action, rows, today):
    """Compara la zona antes y después del inicio, frente al resto del área."""
    start = date.fromisoformat(action['start_date'])
    end = date.fromisoformat(action['end_date'])
    if today < start:
        return {'state': 'sin_iniciar', 'verdict': 'Aún no empieza.', 'days': 0}
    last = min(end, today)
    days = (last - start).days + 1
    before_first, before_last = start - timedelta(days=days), start - timedelta(days=1)
    key = parse_zone(action['zone'])
    band = action['band']
    before, harm_before = _count(rows, key, band, before_first, before_last, True)
    after, harm_after = _count(rows, key, band, start, last, True)
    rest_before, _ = _count(rows, key, band, before_first, before_last, False)
    rest_after, _ = _count(rows, key, band, start, last, False)
    zone_pct, rest_pct = _pct(before, after), _pct(rest_before, rest_after)

    if days < MIN_DAYS:
        state, verdict = 'pronto', f'Muy pronto para evaluar: lleva {days} día(s); espera al menos {MIN_DAYS}.'
    elif before + after < MIN_REPORTS:
        state, verdict = 'pocos_datos', f'Pocos reportes ({before + after}) para sacar conclusiones.'
    elif after > before:
        state, verdict = 'empeora', 'Los reportes aumentaron en la zona: revisar o reforzar la acción.'
    elif zone_pct is not None and rest_pct is not None and zone_pct <= rest_pct - 20:
        state, verdict = 'mejora', 'Bajaron más que en el resto del área en las mismas fechas.'
    elif after < before:
        state, verdict = 'similar', 'Bajaron, pero de forma parecida al resto del área: no se distingue un efecto propio.'
    else:
        state, verdict = 'sin_cambio', 'Sin cambio en la zona.'
    return {'state': state, 'verdict': verdict, 'days': days,
            'window_before': [before_first.isoformat(), before_last.isoformat()],
            'window_after': [start.isoformat(), last.isoformat()],
            'before': before, 'after': after, 'change_pct': zone_pct,
            'harm_before': harm_before, 'harm_after': harm_after,
            'rest_before': rest_before, 'rest_after': rest_after, 'rest_change_pct': rest_pct,
            'finished': today >= end}


def with_evaluation(actions, rows, today):
    result = []
    for a in actions:
        info = cell_info(parse_zone(a['zone']))
        result.append({**a, 'lat': info['lat'], 'lng': info['lng'], 'step': info['step'],
                       'band_label': BANDS[int(a['band'])] if a['band'] != 'all' else 'Todo el día',
                       'evaluation': evaluate(a, rows, today)})
    return result
