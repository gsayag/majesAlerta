"""Agregación pública, recurrencia por celdas estables y gestión privada."""
import math
from collections import Counter, defaultdict
from datetime import timedelta
from statistics import median
from time import perf_counter
from analytics import (BOUNDS, TYPES, STATUSES, VALID_STATUSES, VALUE_BANDS, HARM_VALUE, HARM_WEAPON,
                       HARM_INJURY, CRITICAL_HARM, now, parse_time, selected, statistics, dbscan, priority,
                       harm, critical_reasons, value_band)

STEP = .006
# Daño acumulado de una celda para pedir atención. 6 = un robo grave o seis robos menores.
ZONE_MEDIUM = 6
ZONE_HIGH = 12

def cell_key(r):
    return (math.floor((r['lng'] - BOUNDS['west']) / STEP), math.floor((r['lat'] - BOUNDS['south']) / STEP))

def cell_info(key):
    col, row = key
    return {'name': f'Celda {chr(65 + col)}{row + 1}',
            'lat': BOUNDS['south'] + (row + .5) * STEP,
            'lng': BOUNDS['west'] + (col + .5) * STEP, 'step': STEP}

def modalities(rows):
    counts = Counter(r['subtype'] for r in rows)
    return [{'key': k, 'label': v, 'count': counts[k],
             'percent': round(100 * counts[k] / len(rows), 1) if rows else 0} for k, v in TYPES.items()]

def enhanced_statistics(rows, days=30, subtype='all', band='all', clock=None):
    started = perf_counter()
    clock = clock or now()
    s = statistics(rows, days, subtype, band, clock)
    reviewed = [r for r in rows if r['status'] in VALID_STATUSES]
    current = selected(reviewed, days, subtype, band, clock)
    previous = selected(reviewed, days, subtype, band, clock, True)
    start = clock.date() - timedelta(days=days-1)
    previous_start = start - timedelta(days=days)
    pc = Counter(parse_time(r['occurred_at']).date().isoformat() for r in previous)
    for i, d in enumerate(s['timeline']):
        d['previous_date'] = (previous_start + timedelta(days=i)).isoformat()
        d['previous_count'] = pc[d['previous_date']]
    matrix = [[0]*24 for _ in range(7)]
    by_cell, by_previous = defaultdict(list), defaultdict(list)
    for r in current:
        t = parse_time(r['occurred_at']); matrix[t.weekday()][t.hour] += 1
        by_cell[cell_key(r)].append(r)
    for r in previous:
        by_previous[cell_key(r)].append(r)
    recurrence = []
    for key, items in by_cell.items():
        if len(items) < 3:
            continue
        # Week bins aligned to the start of the selected window (last bin may be partial).
        n_bins = math.ceil(days/7)
        weeks = [0]*n_bins
        for r in items:
            weeks[(parse_time(r['occurred_at']).date()-start).days//7] += 1
        prev = len(by_previous[key])
        # Do not reveal sparse historical counts for public spatial cells.
        visible_prev = prev if prev >= 3 else None
        recurrence.append({**cell_info(key), 'count': len(items), 'previous': visible_prev,
                           'change': len(items)-prev if visible_prev is not None else None,
                           'weeks': [v if v >= 3 else None for v in weeks],
                           'publishable_weeks': sum(v >= 3 for v in weeks), 'total_weeks': n_bins,
                           'pattern': 'Recurrente' if sum(v >= 3 for v in weeks) >= 2 else 'Concentración del periodo'})
    recurrence.sort(key=lambda c: (-c['publishable_weeks'], -c['count']))
    labels = dbscan(current)
    clusters = []
    for label in sorted(set(labels)-{-1}):
        items = [r for r,l in zip(current,labels) if l == label]
        counts = Counter(cell_key(r) for r in items)
        cells = [{**cell_info(k), 'count': v} for k,v in counts.items() if v >= 3]
        # Geometry consists only of publishable cell bounds, never point hulls.
        if not cells:
            continue
        cs = sorted(cells,key=lambda c:(c['lat'],c['lng']))
        hours = [sum(parse_time(r['occurred_at']).hour//6 == h for r in items) for h in range(4)]
        clusters.append({'id': f'G{label+1}', 'name': f'Agrupación {label+1}', 'count': len(items),
                         'cells': cs, 'visible': sum(c['count'] for c in cs),
                         'modalities': modalities(items), 'hours': hours,
                         'first_date': min(parse_time(r['occurred_at']).date().isoformat() for r in items),
                         'last_date': max(parse_time(r['occurred_at']).date().isoformat() for r in items)})
    s.update({'matrix': matrix, 'modalities': modalities(current), 'recurrence': recurrence,
              'cluster_details': clusters, 'difference': s['total']-s['previous_total'],
              'period': {'start': start.isoformat(), 'end': clock.date().isoformat(),
                         'previous_start': previous_start.isoformat(), 'previous_end': (start-timedelta(days=1)).isoformat()},
              'subtype': subtype, 'band': band,
              'elapsed_ms': round((perf_counter()-started)*1000,2)})
    return s

def decision_alerts(rows, days=30, subtype='all', band='all', clock=None):
    """Alertas para decidir dónde mirar primero.

    Contar reportes trata igual un robo de lentes y un robo de auto. Aquí se suman
    dos señales: (1) eventos graves que alertan solos y (2) zonas ordenadas por
    índice de daño acumulado, no solo por cantidad.
    """
    clock = clock or now()
    active = [r for r in rows if r['status'] not in {'duplicado', 'descartado'}]
    cohort = selected(active, days, subtype, band, clock)
    individual = []
    for r in cohort:
        reasons = critical_reasons(r)
        if reasons:
            individual.append({'id': r['id'], 'subtype': r['subtype'], 'occurred_at': r['occurred_at'],
                               'status': r['status'], 'value_band': value_band(r), 'harm': harm(r)['score'],
                               'reasons': reasons, 'cell': cell_info(cell_key(r))['name']})
    # Pendientes primero: son los que todavía nadie miró.
    individual.sort(key=lambda a: a['occurred_at'], reverse=True)
    individual.sort(key=lambda a: (a['status'] != 'recibido', -a['harm']))
    by_cell = defaultdict(list)
    for r in cohort:
        if r['status'] in VALID_STATUSES:
            by_cell[cell_key(r)].append(r)
    zones = []
    for key, items in by_cell.items():
        total = sum(harm(r)['score'] for r in items)
        critical = sum(bool(critical_reasons(r)) for r in items)
        level = ('alta' if total >= ZONE_HIGH else 'media' if total >= ZONE_MEDIUM
                 else 'vigilancia' if len(items) >= 3 else None)
        if level is None:
            continue
        if level == 'vigilancia':
            reason = 'Varios robos de bajo impacto'
        elif critical:
            reason = f'{critical} evento(s) grave(s) en la zona'
        elif len(items) >= 3:
            reason = 'Concentración de robos con daño relevante'
        else:
            reason = 'Pocos reportes, pero de alto daño'
        zones.append({**cell_info(key), 'count': len(items), 'harm': total, 'critical': critical,
                      'level': level, 'reason': reason, 'only_by_harm': len(items) < 3})
    for i, z in enumerate(sorted(zones, key=lambda z: (-z['count'], z['name']))):
        z['rank_by_count'] = i + 1
    zones.sort(key=lambda z: (-z['harm'], -z['count'], z['name']))
    for i, z in enumerate(zones):
        z['rank_by_harm'] = i + 1
    return {'individual': individual[:25], 'individual_total': len(individual),
            'individual_pending': sum(a['status'] == 'recibido' for a in individual),
            'zones': zones, 'hidden_by_count': sum(z['only_by_harm'] for z in zones),
            'rules': {'critical_harm': CRITICAL_HARM, 'zone_medium': ZONE_MEDIUM, 'zone_high': ZONE_HIGH,
                      'weights': {'base': 1, 'weapon': HARM_WEAPON, 'injury': HARM_INJURY,
                                  'value': {VALUE_BANDS[k]: v for k, v in HARM_VALUE.items()}}}}

def management_metrics(rows, history, evidence_ids, days=30, subtype='all', band='all', clock=None):
    clock=clock or now()
    cohort=selected(rows,days,subtype,band,clock)
    counts=Counter(r['status'] for r in cohort)
    first={}
    for h in history:
        if h['status'] != 'recibido':
            t=parse_time(h['timestamp'])
            if h['report_id'] not in first or t<first[h['report_id']]:first[h['report_id']]=t
    reviewed_times=[]; pending=[]; negative=0; complete=0; located=0
    scores=Counter()
    for r in cohort:
        created=parse_time(r['created_at'])
        if r['id'] in first:
            mins=(first[r['id']]-created).total_seconds()/60
            if mins >= 0:reviewed_times.append(mins)
            else:negative+=1
        elif r['status']=='recibido':pending.append(max(0,(clock-created).total_seconds()/3600))
        required=('id','subtype','description','lat','lng','occurred_at','created_at','status')
        complete+=all(r.get(k) is not None and r.get(k)!='' for k in required)
        located+=bool(isinstance(r.get('lat'),(int,float)) and isinstance(r.get('lng'),(int,float))
                      and math.isfinite(r['lat']) and math.isfinite(r['lng'])
                      and BOUNDS['south']<=r['lat']<=BOUNDS['north'] and BOUNDS['west']<=r['lng']<=BOUNDS['east'])
        scores[priority(r,rows,clock)['level']]+=1
    n=len(cohort)
    pct=lambda v:round(v/n*100,1) if n else None
    return {'total':n, 'days':days, 'states':[{'key':k,'label':v,'count':counts[k]} for k,v in STATUSES.items()],
            'priorities':[{'label':k.title(),'count':scores[k]} for k in ('alta','media','baja')],
            'median_review_minutes':round(median(reviewed_times),1) if reviewed_times else None,
            'review_sample':len(reviewed_times),'invalid_timestamps':negative,
            'pending_over_24h':sum(v>24 for v in pending),
            'quality':[{'label':'Campos obligatorios completos','count':complete,'percent':pct(complete)},
                       {'label':'Coordenadas dentro del área del demo','count':located,'percent':pct(located)},
                       {'label':'Reportes con foto opcional','count':sum(r['id'] in evidence_ids for r in cohort),'percent':pct(sum(r['id'] in evidence_ids for r in cohort))},
                       {'label':'Duplicados confirmados en revisión','count':counts['duplicado'],'percent':pct(counts['duplicado'])}],
            'alerts':decision_alerts(rows,days,subtype,band,clock),
            'synthetic':True}
