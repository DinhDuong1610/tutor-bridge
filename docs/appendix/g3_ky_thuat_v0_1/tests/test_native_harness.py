"""Harness regression tests only. No database or fake native PASS evidence is written."""
from pathlib import Path
import sys,json,copy,unittest
from unittest.mock import patch,Mock
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'native'))
from run import race_verdict
import run as native_runner
from evaluate_evidence import evaluate
class HarnessTests(unittest.TestCase):
 def test_commit_after_lock_is_required(self):self.assertEqual(race_verdict(True,{'outcome':'COMMITTED','sqlstate':None},None),'PASS')
 def test_error_without_sqlstate_never_passes(self):self.assertEqual(race_verdict(True,{'outcome':'ROLLED_BACK','sqlstate':None},None),'FAIL')
 def test_missing_outcome_never_passes(self):self.assertEqual(race_verdict(True,{},None),'FAIL')
 def test_expected_sqlstate_requires_rollback(self):self.assertEqual(race_verdict(True,{'outcome':'ROLLED_BACK','sqlstate':'23P01'},'23P01'),'PASS')
 def test_wrong_sqlstate_never_passes(self):self.assertEqual(race_verdict(True,{'outcome':'ROLLED_BACK','sqlstate':'08006'},'23P01'),'FAIL')
 def test_no_lock_wait_is_not_concurrency_evidence(self):self.assertEqual(race_verdict(False,{'outcome':'COMMITTED'},None),'FAIL')
 def test_rollback_failure_never_passes(self):self.assertEqual(race_verdict(True,{'outcome':'ERROR','sqlstate':None},None),'FAIL')
 def test_embedded_evidence_is_rejected(self):self.assertIn('ENGINE_NOT_NATIVE',evaluate({'engine':'PGlite'})['errors'])
 def test_partial_evidence_is_rejected(self):self.assertIn('SQL_CASE_SET_INCOMPLETE_OR_DUPLICATE',evaluate({'engine':'Native PostgreSQL','runtime':{'version_num':'170000'},'cases':[],'concurrency':[]})['errors'])
 def test_summary_alone_cannot_approve(self):self.assertEqual(evaluate({'engine':'Native PostgreSQL','counts':{'passed':69,'total':69}})['status'],'NOT_ACCEPTED')
 def test_connect_initializes_schema_on_each_connection(self):
  fake_driver=Mock();fake_conn=Mock();fake_driver.connect.return_value=fake_conn
  with patch.object(native_runner,'psycopg',fake_driver),patch('pathlib.Path.read_text',return_value='synthetic-test-only'):
   native_runner.connect();native_runner.connect()
  self.assertEqual(fake_conn.execute.call_count,2)
  fake_conn.execute.assert_called_with('SET search_path=tutorbridge,public')
 def test_complete_synthetic_evidence_shape_is_accepted_only_as_component(self):
  cases=[{'id':x['id'],'status':'PASS'} for name in ['baseline_cases.json','unified_cases.json'] for x in json.loads((ROOT/'tests'/name).read_text())]
  races=[{'id':x,'status':'PASS','sessionPids':[101,102],'lockWaitObserved':True,'outcome':'COMMITTED' if x=='N05_ONE_TIME_TOKEN' else 'ROLLED_BACK'} for x in ['N01_TUTOR','N02_STUDENT','N03_ADJACENT','N04_REVOKE_PUBLISH','N05_ONE_TIME_TOKEN']]
  result=evaluate({'engine':'Native PostgreSQL','runtime':{'version_num':'170000'},'cases':cases,'concurrency':races})
  self.assertEqual(result['status'],'DB_COMPONENT_PASS')
  self.assertTrue(result['g3_status'].startswith('NOT_CLOSED'))
if __name__=='__main__':unittest.main(verbosity=2)
