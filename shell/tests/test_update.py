"""更新脚本编排回归：mock外部命令，不连接服务器/真实MySQL。"""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
NAMES = ["20260922_membership_entitlement.sql", "20260922_ai_training_summary.sql"]
FAKE = r'''#!/usr/bin/env python3
import hashlib,json,os,re,sys
from pathlib import Path
cmd=Path(sys.argv[0]).name; args=sys.argv[1:]
p=Path(os.environ['TEST_STATE']); s=json.loads(p.read_text())
def save(): p.write_text(json.dumps(s))
def fail(): save(); sys.exit(1)
s['trace'].append(cmd+' '+' '.join(args));save()
if cmd=='git':
 if args[:2]==['rev-parse','--git-path']: print('.git/update.lock')
 elif args[:2]==['rev-parse','--short']: print('testhead')
 elif args[0]=='pull' and s.get('pull_fail'): fail()
 sys.exit(0)
if cmd=='flock': sys.exit(0)
if cmd=='sleep': sys.exit(0)
if cmd=='sha256sum': print(hashlib.sha256(Path(args[0]).read_bytes()).hexdigest()+'  '+args[0]);sys.exit(0)
if cmd=='curl':
 if s.get('health_fail'): fail()
 print('{"code":0,"data":"pong"}');sys.exit(0)
if cmd!='docker': fail()
if args[0]=='inspect': print('sha256:old');sys.exit(0)
if args[0]=='compose':
 if 'build' in args and s.get('build_fail'): fail()
 if 'port' in args: print('0.0.0.0:80')
 sys.exit(0)
if args[0] in ['info','tag']: sys.exit(0)
if args[0]=='exec':
 if 'mysqldump' in args[-1]:
  if s.get('backup_fail'): fail()
  print('-- test database backup');sys.exit(0)
 sql=sys.stdin.read().strip()
 if sql=='SELECT 1;': print(1)
 elif "TABLE_NAME='fitcoach_schema_migration'" in sql: print(int(s.get('ledger',False)))
 elif 'SELECT CONCAT(checksum' in sql:
  name=re.search("version='([^']+)'",sql)[1]
  if name in s['history']: print(':'.join(s['history'][name]))
 elif sql.startswith('SELECT version'): print('\n'.join(sorted(s['history'])))
 elif '@fc_' in sql:
  name='20260922_membership_entitlement.sql' if "TABLE_NAME='membership_entitlement'" in sql else '20260922_ai_training_summary.sql'
  print(s['states'][name])
 elif sql.startswith('CREATE TABLE IF NOT EXISTS fitcoach_schema_migration'): s['ledger']=True
 elif sql.startswith('INSERT INTO fitcoach_schema_migration'):
  name,h=re.search(r"VALUES\('([^']+)', '([^']+)'",sql).groups();s['history'][name]=[h,'STARTED']
 elif sql.startswith('UPDATE fitcoach_schema_migration'):
  name=re.search("version='([^']+)'",sql)[1];s['history'][name][1]='APPLIED'
 else:
  name='20260922_membership_entitlement.sql' if 'CREATE TABLE IF NOT EXISTS membership_entitlement' in sql else '20260922_ai_training_summary.sql'
  s['executed'].append(name)
  if s.get('sql_fail')==name: fail()
  s['states'][name]='APPLIED'
 save();sys.exit(0)
fail()
'''

class UpdateTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        (self.root / 'shell').mkdir()
        (self.root / '.git').mkdir()
        (self.root / '.env.prod').write_text('')
        shutil.copy(ROOT / 'shell/update.sh', self.root / 'shell/update.sh')
        shutil.copytree(ROOT / 'fitcoach-app/src/main/resources/sql', self.root / 'fitcoach-app/src/main/resources/sql')
        bindir = self.root / 'bin'; bindir.mkdir()
        runner = bindir / 'fake'; runner.write_text(FAKE); runner.chmod(0o755)
        for name in ['git','docker','flock','sha256sum','curl','sleep']:
            (bindir/name).symlink_to(runner)
        self.statefile = self.root/'state.json'
        self.state = dict(trace=[],history={},executed=[],states={n:'PENDING' for n in NAMES})
        self.env = dict(os.environ, PATH=str(bindir)+':'+os.environ['PATH'],TEST_STATE=str(self.statefile),FITCOACH_BACKUP_DIR=str(self.root/'backups'))
    def run_update(self):
        self.statefile.write_text(json.dumps(self.state))
        result = subprocess.run(['bash','shell/update.sh'],cwd=self.root,env=self.env,capture_output=True,text=True,errors="replace")
        self.state = json.loads(self.statefile.read_text())
        return result
    def test_fresh_and_repeat(self):
        result=self.run_update(); self.assertEqual(result.returncode,0,result.stderr)
        self.assertEqual(self.state['executed'],NAMES)
        result=self.run_update();self.assertEqual(result.returncode,0,result.stderr)
        self.assertEqual(self.state['executed'],NAMES)
        self.assertTrue(all(v[1]=='APPLIED' for v in self.state['history'].values()))
        self.assertEqual(sum('git pull --ff-only'==t for t in self.state['trace']),2)
    def test_adopt_manual_migrations(self):
        self.state['states']={n:'APPLIED' for n in NAMES}
        result=self.run_update();self.assertEqual(result.returncode,0,result.stderr)
        self.assertEqual(self.state['executed'],[])
        self.assertEqual(len(self.state['history']),2)
    def test_pull_failure_stops_before_docker(self):
        self.state['pull_fail']=True
        self.assertNotEqual(self.run_update().returncode,0)
        self.assertFalse(any(t.startswith('docker') for t in self.state['trace']))
    def test_partial_schema_stops_before_build(self):
        self.state['states'][NAMES[1]]='CONFLICT'
        self.assertNotEqual(self.run_update().returncode,0)
        self.assertFalse(any('build app' in t for t in self.state['trace']))
    def test_build_failure_does_not_stop_app(self):
        self.state['build_fail']=True
        self.assertNotEqual(self.run_update().returncode,0)
        self.assertFalse(any('stop app' in t for t in self.state['trace']))
    def test_backup_failure_no_migration_or_start(self):
        self.state['backup_fail']=True
        self.assertNotEqual(self.run_update().returncode,0)
        self.assertEqual(self.state['history'],{})
        self.assertFalse(any('up -d' in t for t in self.state['trace']))
    def test_failed_sql_leaves_started_and_blocks_retry(self):
        self.state['sql_fail']=NAMES[0]
        self.assertNotEqual(self.run_update().returncode,0)
        self.assertEqual(self.state['history'][NAMES[0]][1],'STARTED')
        self.assertFalse(any('up -d' in t for t in self.state['trace']))
        self.assertNotEqual(self.run_update().returncode,0)
        self.assertEqual(self.state['executed'],[NAMES[0]])
    def test_checksum_change_blocks_update(self):
        self.assertEqual(self.run_update().returncode,0)
        p=self.root/'fitcoach-app/src/main/resources/sql'/NAMES[0]
        p.write_text(p.read_text()+'\n-- changed')
        self.state['trace']=[]
        self.assertNotEqual(self.run_update().returncode,0)
        self.assertFalse(any('build app' in t for t in self.state['trace']))
    def test_no_migrations_still_deploys(self):
        (self.root/'fitcoach-app/src/main/resources/sql/migrations.list').write_text('# no migrations\n')
        result=self.run_update();self.assertEqual(result.returncode,0,result.stderr)
        self.assertEqual(self.state['executed'],[])
        self.assertTrue(any('up -d app nginx' in t for t in self.state['trace']))
    def test_unknown_recorded_migration_blocks_downgrade(self):
        self.state['ledger']=True
        self.state['history']['20990101_future.sql']=['a'*64,'APPLIED']
        self.assertNotEqual(self.run_update().returncode,0)
        self.assertFalse(any('build app' in t for t in self.state['trace']))

    def test_health_failure_stops_app(self):
        self.state['health_fail']=True
        self.assertNotEqual(self.run_update().returncode,0)
        self.assertIn('stop app',self.state['trace'][-1])

if __name__=='__main__': unittest.main()
