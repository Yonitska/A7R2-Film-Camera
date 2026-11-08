# Publishing this repository

This repository is **source-only**. It does not include a public APK release:
the maintainer's local camera build bundles sample LUTs, a pose pack, and the
Sony A7R II 42m memory map whose public redistribution is not cleared.

## What is public

- Original Java/C++ source and scripts.
- Documentation, host tests, and build instructions.
- `android-assets/` cleared assets: fonts, UI catalogs, first-party icons, and
  Unsplash photographs.
- An **empty** `android-assets/CATALOG.JSN`.

## What must never be committed

- `local-assets/` (sample LUTs, POSE pack/images).
- `vendor/` (Sony memory maps, pinned dependency checkouts, toolchains).
- `out/` (`test.keystore` and local builds), `dist/`, `baselines/`.
- `SONYLUT/` or any device journal, photo, or log.
- APKs, signing keys, passwords, Wi-Fi profiles, or private camera data.

`.gitignore` excludes all of the above. The staging and package-check scripts
accept an empty LUT catalog and zero pose images, so a fresh clone still builds.

## First push

Create an **empty** repository on GitHub (no README, no license file), then:

```sh
git remote add origin git@github.com:Yonitska/A7R2-Film-Camera.git
git push -u origin main
```

or, over HTTPS, `https://github.com/Yonitska/A7R2-Film-Camera.git`.

Verify the pushed tree does not contain any excluded file:

```sh
git ls-files | grep -E '^(local-assets|vendor|POSE|SONYLUT|baselines|out|dist)/|\.apk$|\.keystore$'
```

The command must print nothing.

## Releases

Do not attach an APK built from `local-assets/` to a public release. If a public
binary is desired later, build it from the public tree (empty catalog, no pose
pack), document that it has no built-in films, and verify the package with
`python3 scripts/check_package.py --variant release`. The A7R II memory map must
still come from a user-supplied `vendor/` tree; do not redistribute it.
