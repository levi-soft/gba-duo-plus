import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {execFileSync} from 'node:child_process';
import {inspect} from './upstream-boundary.mjs';

test('tracks integration edits and prevents hidden upstream copies or new extensions in the host', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'plus-boundary-'));
  const git = (...args) => execFileSync('git', args, {cwd: root, encoding: 'utf8'}).trim();
  const put = (file, content) => {const dest = path.join(root, file); fs.mkdirSync(path.dirname(dest), {recursive: true}); fs.writeFileSync(dest, content);};
  try {
    git('init', '-q');
    put('app/src/main/java/Host.kt', 'upstream');
    git('add', '.');
    git('-c', 'user.name=Boundary Test', '-c', 'user.email=boundary@example.invalid', 'commit', '-qm', 'baseline');
    const lock = {commit: git('rev-parse', 'HEAD'), integrations: {'app/src/main/java/Host.kt': 'lifecycle hook'}};
    put('app/src/main/java/Host.kt', 'host hook');
    put('plus/src/main/java/Feature.kt', 'extension');
    let result = inspect(root, lock);
    assert.equal(result.problems.length, 0);
    assert.equal(result.hooks.length, 1);
    assert.equal(result.extensions.length, 1);
    assert.equal(inspect(root, {...lock, integrations: {}}).problems.length, 1);
    put('plus/src/main/java/Host.kt', 'hidden upstream replacement');
    assert.match(inspect(root, lock).problems.join('\n'), /shadows an upstream/);
    put('app/src/main/java/NewFeature.kt', 'misplaced extension');
    assert.match(inspect(root, lock).problems.join('\n'), /belongs in plus/);
    fs.unlinkSync(path.join(root, 'app/src/main/java/Host.kt'));
    assert.equal(inspect(root, lock).hooks.length, 1, 'upstream deletions remain visible');
  } finally {
    // Only the exact temporary fixture created above is removed.
    fs.rmSync(root, {recursive: true, force: true});
  }
});
