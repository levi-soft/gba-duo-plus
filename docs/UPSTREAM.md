# Upstream origin and maintenance boundary

Plus-owned implementations now live under [`plus/src/main`](../plus/README.md).
The application uses that source set plus explicit upstream integration edits;
it does not replace official classes with hidden overlay copies. The baseline and
integration inventory are pinned in [`plus/upstream.json`](../plus/upstream.json).
Run `node tools/upstream-boundary.mjs check` before committing, and `compare <tag>`
before the next upgrade. See the extension README for the pristine checkout and
upgrade procedure.

- Original application: https://github.com/khumam/gba-duo
- Creator/project owner: **khumam**, with upstream contributors.
- Plus baseline: **v0.1.9**, commit `61582fcb5e07d98b19675e75e9bde532a7c68c4c`.
- Plus repository/maintenance: https://github.com/levi-soft/gba-duo-plus
- Overall application license: GNU GPL v3, retaining individual component notices.

The publication started from official v0.1.6 history. The 0.1.9 upgrade merges the official v0.1.9 tag while retaining the Plus-specific additions. Original authorship remains visible in Git history. It does not import the private experimental Dev branch history, local machine configuration, credentials, device data or private companion releases.

Some Plus work was developed in a local experimental Dev edition before being selected for Plus. Hash-override and translation experiments were deliberately excluded from this edition. Their absence is tested; they are not simply hidden menu items.

When updating the fork, compare the chosen upstream revision first. Do not label an inherited capability as a new Plus feature. For example, external LaunchActivity support and saved shader selection already belong to the official v0.1.6 baseline; Plus adopts/extends them. The comparison in [FEATURES](FEATURES.md) is explicitly pinned to the current 0.1.9 baseline.

The `upstream` remote is a local development convenience and is not embedded in GitHub clones. Contributors may add it themselves:

```sh
git remote add upstream https://github.com/khumam/gba-duo.git
git fetch upstream --tags
```

Report fork-specific issues here. Do not assume that a bug caused by Plus additions reproduces in the official application, and do not send private ROM/credential material to either issue tracker.
