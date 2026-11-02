"""Generate a local test-only password. Never overwrite an existing password."""
from pathlib import Path
import secrets
root=Path(__file__).resolve().parents[1]
target=root/'secrets/review_password.txt';target.parent.mkdir(mode=0o700,exist_ok=True)
try:
 with target.open('x',encoding='utf8') as f:f.write(secrets.token_urlsafe(32)+'\n')
 target.chmod(0o600)
 print('Local review secret generated; value is not printed.')
except FileExistsError:print('Existing local review secret retained.')
