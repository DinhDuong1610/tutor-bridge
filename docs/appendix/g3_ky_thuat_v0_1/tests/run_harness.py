from pathlib import Path
import sys,unittest,json,datetime
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'tests'))
suite=unittest.defaultTestLoader.discover(str(ROOT/'tests'),pattern='test_native_harness.py')
result=unittest.TextTestRunner(verbosity=2).run(suite)
record={'executedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'scope':'Native runner regression tests only; synthetic/mocked data; no database executed','testsRun':result.testsRun,'failures':len(result.failures),'errors':len(result.errors),'status':'PASS' if result.wasSuccessful() else 'FAIL','native_database_status':'NOT_EXECUTED'}
(ROOT/'evidence/harness_results.json').write_text(json.dumps(record,indent=2))
sys.exit(0 if result.wasSuccessful() else 1)
