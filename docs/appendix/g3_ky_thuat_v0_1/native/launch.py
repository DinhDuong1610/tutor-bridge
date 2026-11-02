"""Disposable local Docker run. Does not install Docker or touch a user's existing DB."""
from pathlib import Path
import subprocess,sys,os,json,datetime,venv,shutil
ROOT=Path(__file__).resolve().parents[1]
E=ROOT/'evidence';E.mkdir(exist_ok=True)
def main():
 report={'startedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'status':'BLOCKED','steps':[],'nativeExecuted':False}
 lock=ROOT/'.native_runner.lock';owns_lock=False;owns_project=False
 run_env=os.environ.copy();run_env['TB_REVIEW_PORT']='55434'
 def command(args,step,env=None):
  with (E/(step+'.log')).open('w',encoding='utf-8') as out:
   p=subprocess.run(args,cwd=ROOT,env=env or run_env,stdout=out,stderr=subprocess.STDOUT,text=True,timeout=900)
  report['steps'].append({'name':step,'exitCode':p.returncode})
  if p.returncode:raise RuntimeError(step)
 try:
  if sys.version_info<(3,11):raise RuntimeError('PYTHON_3_11_REQUIRED')
  try:
   fd=os.open(lock,os.O_CREAT|os.O_EXCL|os.O_WRONLY,0o600);os.write(fd,str(os.getpid()).encode());os.close(fd);owns_lock=True
  except FileExistsError:raise RuntimeError('ANOTHER_RUN_OR_STALE_LOCK_CHECK_BEFORE_REMOVING')
  command([sys.executable,'tests/run_harness.py'],'harness_tests')
  if not shutil.which('docker'):raise RuntimeError('INSTALL_AND_START_DOCKER_DESKTOP_FIRST')
  command(['docker','info','--format','{{.ServerVersion}}'],'docker_info')
  command(['docker','compose','version'],'compose_version')
  docker_host=run_env.get('DOCKER_HOST','')
  if docker_host and not docker_host.startswith(('unix://','npipe://')):raise RuntimeError('REMOTE_DOCKER_HOST_NOT_ALLOWED')
  context=subprocess.run(['docker','context','inspect','--format','{{.Endpoints.docker.Host}}'],cwd=ROOT,env=run_env,capture_output=True,text=True,timeout=30)
  if context.returncode or not (docker_host or context.stdout.strip()).startswith(('unix://','npipe://')):
   raise RuntimeError('LOCAL_DOCKER_DESKTOP_CONTEXT_REQUIRED')
  current=subprocess.run(['docker','compose','ps','-a','-q'],cwd=ROOT,env=run_env,capture_output=True,text=True,timeout=30)
  if current.returncode:raise RuntimeError('COMPOSE_STATUS_UNAVAILABLE')
  if current.stdout.strip():raise RuntimeError('EXISTING_TEST_PROJECT_PRESERVED_USE_A_FRESH_FOLDER_OR_REVIEW_ITS_CONTAINERS')
  print('Preparing a local Python environment. First run needs Internet.',flush=True)
  vdir=ROOT/'.native_venv'
  if not vdir.exists():venv.create(vdir,with_pip=True)
  python=str(vdir/('Scripts/python.exe' if os.name=='nt' else 'bin/python'))
  command([python,'-m','pip','install','-r','native/requirements.txt'],'python_dependencies')
  command([python,'native/prepare.py'],'prepare_secret')
  print('Starting disposable PostgreSQL on 127.0.0.1:55434.',flush=True)
  owns_project=True
  command(['docker','compose','up','-d','--wait'],'compose_up')
  env=os.environ.copy();env['TB_REVIEW_PORT']='55434'
  command([python,'native/run.py'],'native_run',env=env);report['nativeExecuted']=True
  command([python,'native/evaluate_evidence.py'],'evaluate_evidence')
  report['status']='DB_COMPONENT_PASS'
  print('DB component checks passed. Team/user evidence is still required for G3.',flush=True)
 except (OSError,RuntimeError,subprocess.SubprocessError) as err:
  report['status']='NOT_ACCEPTED' if report['nativeExecuted'] else 'BLOCKED_OR_FAILED'
  report['reason']=str(err) if isinstance(err,RuntimeError) else type(err).__name__
  print('Run did not complete. Check evidence/execution_status.json and step logs.',flush=True)
 finally:
  if owns_project:
   try:
    p=subprocess.run(['docker','compose','down'],cwd=ROOT,env=run_env,capture_output=True,text=True,timeout=60)
    report['steps'].append({'name':'compose_down','exitCode':p.returncode})
   except (OSError,subprocess.SubprocessError):report['cleanup']='CHECK_TEST_PROJECT_MANUALLY'
  if owns_lock:lock.unlink(missing_ok=True)
  report['finishedAt']=datetime.datetime.now(datetime.timezone.utc).isoformat()
  (E/'execution_status.json').write_text(json.dumps(report,indent=2))
 return 0 if report['status']=='DB_COMPONENT_PASS' else 1
if __name__=='__main__':sys.exit(main())
