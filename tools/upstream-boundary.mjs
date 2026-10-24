import fs from 'node:fs';
import path from 'node:path';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

export function inspect(root, lock) {
  const git = (...args) => execFileSync('git', args, {cwd: root, encoding: 'utf8', maxBuffer: 32 * 1024 * 1024}).trim();
  git('cat-file', '-e', `${lock.commit}^{commit}`);
  const baseline = new Set(git('ls-tree', '-r', '--name-only', lock.commit).split('\n'));
  // Include unstaged edits, additions and deletions; no staging required for review.
  const files = new Set([
    ...git('diff', '--name-only', lock.commit).split('\n'),
    ...git('ls-files', '--others', '--exclude-standard').split('\n'),
  ].filter(Boolean));
  const hooks = [], extensions = [], problems = [];
  for (const file of files) {
    if (file.startsWith('plus/src/main/')) {
      const original = file.replace('plus/src/main/', 'app/src/main/');
      if (baseline.has(original)) problems.push(`${file}: shadows an upstream implementation; keep it as an explicit integration edit`);
      extensions.push(file);
    } else if (file.startsWith('app/src/main/') || file === 'app/build.gradle.kts' || file === 'app/proguard-rules.pro') {
      if (!baseline.has(file)) problems.push(`${file}: new Plus implementation belongs in plus/src/main`);
      else if (!lock.integrations[file]) problems.push(`${file}: upstream edit has no recorded integration reason`);
      else hooks.push({file, reason: lock.integrations[file]});
    }
  }
  return {baseline: lock.commit, hooks, extensions, problems};
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const root = fileURLToPath(new URL('../', import.meta.url));
  const lock = JSON.parse(fs.readFileSync(path.join(root, 'plus/upstream.json'), 'utf8'));
  const result = inspect(root, lock);
  const command = process.argv[2] ?? 'check';
  if (command === 'check') {
    for (const problem of result.problems) console.error(problem);
    console.log(`${result.hooks.length} documented upstream integration files; ${result.extensions.length} Plus extension files.`);
    if (result.problems.length) process.exitCode = 1;
  } else if (command === 'report') {
    console.log(JSON.stringify(result, null, 2));
  } else if (command === 'compare') {
    const target = process.argv[3];
    if (!target || target.startsWith('-')) throw Error('Usage: node tools/upstream-boundary.mjs compare <candidate-commit-or-tag>');
    const changed = new Set(execFileSync('git', ['diff', '--name-only', lock.commit, target, '--'], {cwd: root, encoding: 'utf8'}).trim().split('\n'));
    const touched = result.hooks.filter(hook => changed.has(hook.file));
    console.log(JSON.stringify({candidate: target, integrationFilesChangedUpstream: touched,
      note: 'Review these first, then all upstream changes and run regression checks. No overlap is not proof of compatibility.'}, null, 2));
  } else throw Error('Supported commands: check, report, compare <candidate>');
}
