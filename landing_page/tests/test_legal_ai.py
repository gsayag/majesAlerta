import json
import unittest
from datetime import timedelta

from analytics import LIMA, now
from legal import suggest, classify
import ai_analysis
from ai_analysis import analyze, build_context, rules_analysis


def at(hour):
    """Fecha de ayer a la hora indicada (hora de Perú)."""
    return (now() - timedelta(days=1)).astimezone(LIMA).replace(hour=hour, minute=0, second=0, microsecond=0).isoformat()


def record(rid='r', subtype='persona', force='sin_violencia', facts=(), value='medio', hour=10, status='validado', **more):
    r = dict(id=rid, subtype=subtype, force=force, circumstances=json.dumps(list(facts)), value_band=value,
             occurred_at=at(hour), created_at=now().isoformat(), lat=-16.357, lng=-72.187, status=status,
             weapon=False, injury=False, description='Caso ficticio con datos personales: Juan 999888777.')
    r.update(more)
    return r


class LegalTests(unittest.TestCase):
    def test_hurto_simple_art_185(self):
        self.assertEqual(suggest(record())['key'], 'hurto_simple')

    def test_falta_art_444_when_low_value(self):
        self.assertEqual(suggest(record(value='bajo'))['key'], 'falta_hurto')

    def test_hurto_agravado_art_186(self):
        self.assertEqual(suggest(record(hour=21))['key'], 'hurto_agravado')  # de noche
        self.assertEqual(suggest(record(facts=['destreza']))['key'], 'hurto_agravado')
        self.assertEqual(suggest(record(subtype='vivienda', facts=['casa_habitada']))['key'], 'hurto_agravado')

    def test_low_value_but_aggravated_is_not_a_falta(self):
        self.assertEqual(suggest(record(value='bajo', facts=['dos_o_mas']))['key'], 'hurto_agravado')

    def test_hurto_de_uso_art_187(self):
        self.assertEqual(suggest(record(facts=['uso_momentaneo']))['key'], 'hurto_uso')

    def test_robo_art_188(self):
        self.assertEqual(suggest(record(force='amenaza'))['key'], 'robo')
        self.assertEqual(suggest(record(force='violencia'))['key'], 'robo')

    def test_robo_agravado_art_189(self):
        r = suggest(record(force='amenaza', weapon=True))
        self.assertEqual(r['key'], 'robo_agravado')
        self.assertIn('A mano armada', r['reasons'])
        self.assertEqual(suggest(record(force='violencia', subtype='vehiculo'))['key'], 'robo_agravado')

    def test_weapon_without_declared_force_is_still_robbery(self):
        self.assertEqual(suggest(record(force='sin_dato', weapon=True))['family'], 'robo')

    def test_old_reports_without_force_keep_robbery_with_low_confidence(self):
        r = suggest(record(force='sin_dato'))
        self.assertEqual(r['family'], 'robo')
        self.assertEqual(r['confidence'], 'baja')

    def test_reviewer_confirmation_wins(self):
        c = classify(record(force='amenaza', legal_class='hurto_simple'))
        self.assertEqual(c['key'], 'hurto_simple')
        self.assertTrue(c['confirmed'])
        self.assertEqual(c['suggested'], 'robo')


class AiTests(unittest.TestCase):
    def rows(self):
        far = dict(lat=-16.372, lng=-72.170)
        return ([record(f'h{i}', value='bajo') for i in range(4)] +
                [record(f'v{i}', subtype='vehiculo', force='violencia', value='muy_alto', hour=20, **far) for i in range(2)])

    def test_context_never_includes_descriptions_or_ids(self):
        text = json.dumps(build_context(self.rows(), 7), ensure_ascii=False)
        self.assertNotIn('999888777', text)
        self.assertNotIn('"v0"', text)

    def test_rules_prioritize_severe_zone(self):
        ctx = build_context(self.rows(), 7)
        result = rules_analysis(ctx)
        first = result['zonas_prioritarias'][0]
        self.assertEqual(first['zona'], ctx['zonas'][0]['zona'])
        self.assertTrue(any('Patrullaje' in a['accion'] for a in first['acciones']))

    def test_analyze_falls_back_without_sdk(self):
        saved = ai_analysis.anthropic
        ai_analysis.anthropic = None
        try:
            result = analyze(self.rows(), 7, refresh=True)
        finally:
            ai_analysis.anthropic = saved
        self.assertEqual(result['source'], 'reglas')
        self.assertIn('pip install anthropic', result['notice'])
        self.assertTrue(result['map_cells'])

    def test_unknown_zones_from_ai_are_dropped(self):
        saved_sdk, saved_ask = ai_analysis.anthropic, ai_analysis.ask_claude

        class FakeSDK:  # solo necesita las clases de error para los except
            class AuthenticationError(Exception): pass
            class RateLimitError(Exception): pass
            class APIConnectionError(Exception): pass
            class APIStatusError(Exception): pass

        def fake(context):
            real = context['zonas'][0]['zona']
            return {'resumen': 'ok', 'patrones': [], 'limitaciones': [],
                    'zonas_prioritarias': [{'zona': real, 'prioridad': 'alta', 'diagnostico': 'd', 'acciones': [], 'indicador': 'i'},
                                           {'zona': 'Celda Z99', 'prioridad': 'alta', 'diagnostico': 'd', 'acciones': [], 'indicador': 'i'}]}, 'modelo'
        ai_analysis.anthropic, ai_analysis.ask_claude = FakeSDK, fake
        try:
            result = analyze(self.rows(), 7, refresh=True)
        finally:
            ai_analysis.anthropic, ai_analysis.ask_claude = saved_sdk, saved_ask
        self.assertEqual(result['source'], 'claude')
        self.assertEqual([z['zona'] for z in result['analysis']['zonas_prioritarias']], [result['map_cells'][0]['name']])


if __name__ == '__main__':
    unittest.main()
