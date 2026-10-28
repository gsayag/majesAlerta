import json
import unittest
from datetime import datetime,timedelta
from analytics import LIMA
from insights import enhanced_statistics, management_metrics
from evaluation import compare, evaluate, scenario
from test_demo import record

CLOCK=datetime(2026,10,28,18,tzinfo=LIMA)

class InsightsTests(unittest.TestCase):
    def test_matrix_comparison_and_modalities_share_filters(self):
        rows=[record('a',event=CLOCK),record('b',event=CLOCK,subtype='negocio'),record('c',event=CLOCK-timedelta(days=7)),record('d',event=CLOCK,status='recibido')]
        s=enhanced_statistics(rows,7,'persona','3',CLOCK)
        self.assertEqual(s['total'],1);self.assertEqual(s['previous_total'],1)
        self.assertEqual(sum(map(sum,s['matrix'])),1)
        self.assertEqual(sum(d['previous_count'] for d in s['timeline']),1)
        self.assertEqual(sum(d['count'] for d in s['modalities']),1)
        self.assertEqual(s['matrix'][2][18],1)

    def test_recurrence_requires_multiple_publishable_weeks(self):
        rows=[record(str(i),event=CLOCK-timedelta(days=offset)) for i,offset in enumerate([1,1,1,8,8,8])]
        s=enhanced_statistics(rows,30,clock=CLOCK)
        c=s['recurrence'][0]
        self.assertEqual(c['publishable_weeks'],2)
        self.assertEqual(c['pattern'],'Recurrente')
        self.assertIsNone(c['previous'])
        self.assertTrue(all(v is None or v>=3 for v in c['weeks']))

    def test_public_cluster_geometry_is_aggregated(self):
        rows=[record('private-'+str(i),lat=-16.357+i*.00001,event=CLOCK,description='secreto') for i in range(5)]
        s=enhanced_statistics(rows,clock=CLOCK);serialized=json.dumps(s)
        self.assertNotIn('private-',serialized);self.assertNotIn('secreto',serialized)
        self.assertEqual(s['cluster_details'][0]['count'],5)
        self.assertTrue(all(c['count']>=3 for g in s['cluster_details'] for c in g['cells']))
        self.assertNotEqual(s['cluster_details'][0]['cells'][0]['lat'],rows[0]['lat'])

    def test_empty_is_not_division_by_zero(self):
        s=enhanced_statistics([],clock=CLOCK)
        self.assertEqual(s['matrix'],[[0]*24 for _ in range(7)])
        self.assertEqual(s['cluster_details'],[]);self.assertIsNone(s['change_pct'])
        q=management_metrics([],[],set(),clock=CLOCK)
        self.assertIsNone(q['median_review_minutes']);self.assertTrue(all(x['percent'] is None for x in q['quality']))

    def test_review_uses_first_action_and_excludes_negative_time(self):
        rows=[record('a',event=CLOCK,created_at=CLOCK.isoformat()),record('b',event=CLOCK,created_at=CLOCK.isoformat()),record('c',event=CLOCK,status='recibido',created_at=(CLOCK-timedelta(days=2)).isoformat())]
        history=[{'report_id':'a','status':'validado','timestamp':(CLOCK+timedelta(minutes=10)).isoformat()}, {'report_id':'a','status':'cerrado','timestamp':(CLOCK+timedelta(minutes=90)).isoformat()}, {'report_id':'b','status':'validado','timestamp':(CLOCK-timedelta(minutes=5)).isoformat()}]
        s=management_metrics(rows,history,{'a'},clock=CLOCK+timedelta(hours=2))
        self.assertEqual(s['median_review_minutes'],10)
        self.assertEqual(s['review_sample'],1);self.assertEqual(s['invalid_timestamps'],1)
        self.assertEqual(s['pending_over_24h'],1)
        self.assertEqual(s['quality'][2]['count'],1)

    def test_reference_matching_cannot_double_count_split_group(self):
        s=compare([0,0,0,0],[0,0,1,1])
        self.assertEqual(s['matched_clusters'],1);self.assertEqual(s['unmatched_clusters'],1)
        self.assertEqual(s['precision'],50)

    def test_reference_benchmark_reproducible_and_sensitive(self):
        self.assertEqual(scenario(100,42),scenario(100,42))
        good=evaluate(300,250,4,42);bad=evaluate(300,50,30,42)
        self.assertEqual(good['matched_clusters'],3)
        self.assertEqual(good['precision'],100)
        self.assertEqual(bad['matched_clusters'],0)
        self.assertGreater(good['elapsed_ms'],0)

    def test_benchmark_bounds(self):
        for args in [(10000,250,4,42),(300,0,4,42),(300,250,2,42),(300,250,4,-1)]:
            with self.assertRaises(ValueError):evaluate(*args)
