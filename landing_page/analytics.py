"""Análisis descriptivo del demo. Solo biblioteca estándar de Python."""
import math
from collections import Counter
from datetime import datetime, timedelta, timezone

LIMA = timezone(timedelta(hours=-5))
# Ventana ilustrativa de El Pedregal, Majes. No es el límite administrativo.
BOUNDS = {"south": -16.380, "north": -16.335, "west": -72.212, "east": -72.160}
CENTER = {"lat": -16.357, "lng": -72.187}
TYPES = {"persona": "Robo a persona", "negocio": "Robo a negocio", "vivienda": "Robo a vivienda", "vehiculo": "Robo de vehículo"}
STATUSES = {"recibido": "Recibido", "validado": "Validado", "cerrado": "Cerrado", "duplicado": "Duplicado", "descartado": "Descartado"}
VALID_STATUSES = {"validado", "cerrado"}

# Valor aproximado de lo robado. Rangos ilustrativos en soles; deben calibrarse con actores locales.
VALUE_BANDS = {"sin_dato": "No indicado", "bajo": "Menos de S/ 500", "medio": "S/ 500 a S/ 2 000",
               "alto": "S/ 2 000 a S/ 10 000", "muy_alto": "Más de S/ 10 000"}

# Pesos del índice de daño. Un robo menor vale 1; un robo de vehículo de alto valor vale 6 o más.
# Son supuestos explícitos del demo (pendiente calibrar con expertos, p. ej. AHP), no un estándar.
HARM_VALUE = {"sin_dato": 0, "bajo": 0, "medio": 1, "alto": 3, "muy_alto": 5}
HARM_WEAPON = 3
HARM_INJURY = 4
CRITICAL_HARM = 6


def now():
    return datetime.now(LIMA)


def parse_time(value):
    dt = datetime.fromisoformat(value)
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=LIMA)
    return dt.astimezone(LIMA)


def distance(a, b):
    """Distancia de Haversine, en metros."""
    p1, p2 = math.radians(a['lat']), math.radians(b['lat'])
    dp = p2 - p1
    dl = math.radians(b['lng'] - a['lng'])
    h = math.sin(dp / 2)**2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2)**2
    return 6371000 * 2 * math.asin(math.sqrt(min(1, max(0, h))))


def dbscan(rows, eps=250, min_samples=4):
    """DBSCAN espacial con Haversine. Incluye el punto en min_samples.

    Implementación O(n²) para muestras pequeñas; no usar a escala municipal.
    -1 = ruido, None = no visitado. El periodo se filtra antes de agrupar.
    """
    labels = [None] * len(rows)
    neighbors = [[j for j, b in enumerate(rows) if distance(a, b) <= eps] for a in rows]
    cluster = 0
    for i in range(len(rows)):
        if labels[i] is not None:
            continue
        if len(neighbors[i]) < min_samples:
            labels[i] = -1
            continue
        labels[i] = cluster
        queue = list(neighbors[i])
        enqueued = set(queue)
        for j in queue:
            if labels[j] == -1:
                labels[j] = cluster
            if labels[j] is not None:
                continue
            labels[j] = cluster
            if len(neighbors[j]) >= min_samples:
                for k in neighbors[j]:
                    if k not in enqueued:
                        queue.append(k)
                        enqueued.add(k)
        cluster += 1
    return labels


def value_band(report):
    """Valor declarado; un robo de vehículo sin dato se asume al menos 'alto'."""
    band = report.get('value_band') or 'sin_dato'
    if band not in VALUE_BANDS:
        band = 'sin_dato'
    if band in {'sin_dato', 'bajo', 'medio'} and report.get('subtype') == 'vehiculo':
        return 'alto'
    return band


def harm(report):
    """Índice de daño del reporte: 1 base + valor perdido + violencia."""
    parts = [
        {"label": "Base", "points": 1},
        {"label": "Valor de lo robado", "points": HARM_VALUE[value_band(report)]},
        {"label": "Arma reportada", "points": HARM_WEAPON if report.get('weapon') else 0},
        {"label": "Lesión reportada", "points": HARM_INJURY if report.get('injury') else 0},
    ]
    return {"score": sum(p['points'] for p in parts), "parts": parts}


def critical_reasons(report):
    """Motivos por los que un solo reporte merece alerta, sin esperar a que se acumulen."""
    reasons = []
    if report.get('injury'):
        reasons.append('Lesión reportada')
    if report.get('subtype') == 'vehiculo':
        reasons.append('Robo de vehículo')
    if value_band(report) == 'muy_alto' and report.get('subtype') != 'vehiculo':
        reasons.append('Pérdida mayor a S/ 10 000')
    if harm(report)['score'] >= CRITICAL_HARM and not reasons:
        reasons.append(f'Índice de daño ≥ {CRITICAL_HARM}')
    return reasons


def priority(report, all_rows, clock=None):
    """Reglas ilustrativas para ordenar revisión, NO una predicción de delito."""
    clock = clock or now()
    event = parse_time(report['occurred_at'])
    age = max(0, (clock - event).total_seconds() / 3600)
    recent = 15 if age <= 24 else 8 if age <= 168 else 0
    close = sum(1 for r in all_rows if r['id'] != report['id'] and r['status'] in VALID_STATUSES
                and 0 <= (event - parse_time(r['occurred_at'])).total_seconds() <= 604800
                and distance(report, r) <= 250)
    parts = [
        {"label": "Base de revisión", "points": 10},
        {"label": "Arma reportada", "points": 30 if report['weapon'] else 0},
        {"label": "Lesión reportada", "points": 15 if report['injury'] else 0},
        {"label": "Valor de lo robado", "points": {"alto": 10, "muy_alto": 15, "medio": 5}.get(value_band(report), 0)},
        {"label": "Recencia del hecho", "points": recent},
        {"label": "Recurrencia cercana (7 días)", "points": min(15, close * 3)},
    ]
    score = sum(p['points'] for p in parts)
    return {"score": score, "level": "alta" if score >= 60 else "media" if score >= 30 else "baja", "parts": parts}


def duplicate_candidates(report, rows):
    """Solo candidatos: misma modalidad, <=100 m, <=30 min. Requiere revisión."""
    return [r['id'] for r in rows if r['id'] != report['id'] and r['status'] not in {'duplicado', 'descartado'}
            and r['subtype'] == report['subtype'] and distance(report, r) <= 100
            and abs((parse_time(report['occurred_at']) - parse_time(r['occurred_at'])).total_seconds()) <= 1800]


def selected(rows, days=30, subtype='all', band='all', clock=None, previous=False):
    clock = clock or now()
    end = clock.replace(hour=0, minute=0, second=0, microsecond=0) + timedelta(days=1)
    if previous:
        end -= timedelta(days=days)
    start = end - timedelta(days=days)
    result = []
    for r in rows:
        t = parse_time(r['occurred_at'])
        if not start <= t < end or (subtype != 'all' and r['subtype'] != subtype):
            continue
        if band != 'all' and t.hour // 6 != int(band):
            continue
        result.append(r)
    return result


def statistics(rows, days=30, subtype='all', band='all', clock=None):
    clock = clock or now()
    verified = [r for r in rows if r['status'] in VALID_STATUSES]
    current = selected(verified, days, subtype, band, clock)
    previous = selected(verified, days, subtype, band, clock, True)
    # Cada celda pública representa ~640 x 670 m, no una dirección individual.
    step = .006
    grouped = {}
    for r in current:
        col = math.floor((r['lng'] - BOUNDS['west']) / step)
        row = math.floor((r['lat'] - BOUNDS['south']) / step)
        grouped.setdefault((col, row), []).append(r)
    cells = []
    for (col, row), items in sorted(grouped.items()):
        if len(items) < 3:
            continue
        cells.append({"name": f"Celda {chr(65 + col)}{row + 1}", "count": len(items),
                      "harm": sum(harm(r)['score'] for r in items),
                      "lat": BOUNDS['south'] + (row + .5) * step,
                      "lng": BOUNDS['west'] + (col + .5) * step, "step": step})
    cells.sort(key=lambda c: (-c['count'], c['name']))
    timeline = Counter(parse_time(r['occurred_at']).date().isoformat() for r in current)
    start = clock.date() - timedelta(days=days - 1)
    hours = [sum(1 for r in current if parse_time(r['occurred_at']).hour // 6 == h) for h in range(4)]
    modalities = [{"key": k, "label": v, "count": sum(r['subtype'] == k for r in current)} for k, v in TYPES.items()]
    labels = dbscan(current)
    clusters = len(set(labels) - {-1})
    return {
        "total": len(current), "previous_total": len(previous),
        "change_pct": round((len(current) - len(previous)) / len(previous) * 100, 1) if previous else None,
        "days": days, "generated_at": clock.isoformat(), "synthetic": True,
        "clusters": clusters, "noise": labels.count(-1), "cells": cells,
        "visible_reports": sum(c['count'] for c in cells),
        "suppressed_reports": len(current) - sum(c['count'] for c in cells),
        "peak_band": hours.index(max(hours)) if current else None, "hours": hours,
        "modalities": modalities,
        "timeline": [{"date": (start + timedelta(days=i)).isoformat(), "count": timeline[(start + timedelta(days=i)).isoformat()]} for i in range(days)],
        "method": {"algorithm": "DBSCAN", "eps_meters": 250, "min_samples": 4, "grid_degrees": step, "min_public_count": 3},
    }
