Check Maven and npm for a new Valtimo minor or patch release. If one exists on the same major version, update all version references and open a pull request.

## Step 1 — Read current version

Read `gradle.properties`. Extract `valtimoVersion` (e.g. `13.41.0.RELEASE`).
Strip the `.RELEASE` suffix to get the bare semver string (e.g. `13.41.0`). Call this `CURRENT`.
Parse into `CURRENT_MAJOR`, `CURRENT_MINOR`, `CURRENT_PATCH`.

## Step 2 — Fetch latest backend version from Maven

Fetch the Maven metadata XML:
```
https://valtimo-releases.s3.eu-central-1.amazonaws.com/com/ritense/valtimo/valtimo-dependency-versions/maven-metadata.xml
```

Parse all `<version>` elements. Keep only those ending in `.RELEASE`. Strip `.RELEASE`, parse semver.
Filter to versions where `major == CURRENT_MAJOR`. Pick the highest remaining version. Call this `LATEST_BACKEND`.

## Step 3 — Fetch latest frontend version from npm

Fetch:
```
https://registry.npmjs.org/@valtimo/plugin
```

Read `dist-tags.latest`. Parse semver. Call this `LATEST_FRONTEND`.

## Step 4 — Decide whether to update

Both `LATEST_BACKEND` and `LATEST_FRONTEND` must have the same major as `CURRENT` and be strictly greater than `CURRENT` (minor or patch bump). They must also agree on the same version string — backend and frontend are always released together.

If any condition is not met, print a clear reason ("already up to date", "major-only update skipped", "backend/frontend versions diverge: X vs Y") and stop without making any changes.

Otherwise set `NEW` = the agreed new semver string (e.g. `13.42.0`).

## Step 5 — Apply changes

**`gradle.properties`** — replace:
```
valtimoVersion=<CURRENT>.RELEASE
```
with:
```
valtimoVersion=<NEW>.RELEASE
```

**`frontend/package.json`** — for every `"@valtimo/<package>": "<CURRENT>"` entry (both in `dependencies` and `devDependencies`), replace the value with `"<NEW>"`.

Do not touch any other lines.

## Step 6 — Commit and open a pull request

1. Create a new branch: `chore/update-valtimo-<NEW>`
2. Stage and commit the two changed files with the message:
   ```
   chore: update Valtimo to <NEW>
   ```
3. Push the branch and open a GitHub pull request targeting `main` with:
   - **Title**: `chore: update Valtimo to <NEW>`
   - **Body**:
     ```
     ## Valtimo version bump

     | | Version |
     |---|---|
     | Previous | `<CURRENT>` |
     | New      | `<NEW>`     |

     ### Changed files
     - `gradle.properties` — `valtimoVersion`
     - `frontend/package.json` — all `@valtimo/*` entries

     _Automated update by the `/update-valtimo` skill._
     ```
