# Upstream origin and maintenance boundary

- Original application: https://github.com/khumam/gba-duo
- Creator/project owner: **khumam**, with upstream contributors.
- Plus baseline: **v0.1.6**, commit `0c201e9faa224eb1527651ac0ca3e12a3ce0fe5c`.
- Plus repository/maintenance: https://github.com/levi-soft/gba-duo-plus
- Overall application license: GNU GPL v3, retaining individual component notices.

The publication starts from official v0.1.6 history and commits the Plus source changes on top. Original authorship remains visible in Git history. It does not import the private experimental Dev branch history, local machine configuration, credentials, device data or private companion releases.

Some Plus work was developed in a local experimental Dev edition before being selected for Plus. Hash-override and translation experiments were deliberately excluded from this edition. Their absence is tested; they are not simply hidden menu items.

When updating the fork, compare the chosen upstream revision first. Do not label an inherited capability as a new Plus feature. For example, external LaunchActivity support and saved shader selection already belong to the official v0.1.6 baseline; Plus adopts/extends them. The comparison in [FEATURES](FEATURES.md) is explicitly pinned to that baseline.

The `upstream` remote is a local development convenience and is not embedded in GitHub clones. Contributors may add it themselves:

```sh
git remote add upstream https://github.com/khumam/gba-duo.git
git fetch upstream --tags
```

Report fork-specific issues here. Do not assume that a bug caused by Plus additions reproduces in the official application, and do not send private ROM/credential material to either issue tracker.
