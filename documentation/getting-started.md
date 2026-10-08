# Getting Started

This repository contains the SmartDocuments plugin:

- `backend/plugin`: the backend plugin (`com.ritense.valtimoplugins:smartdocuments`)
- `frontend/projects/plugin`: the frontend library (`@valtimo-plugins/smartdocuments`)
- The example application (`backend/app` and `frontend/src`) and the SmartDocuments mock are only on the `main` branch
  (Valtimo 13), see [Example Application](../../../blob/main/documentation/example-application.md). This `v12` branch
  builds and tests the plugin against Valtimo 12.

## Build and test

```shell
# Backend: compiles, runs ktlint, and runs unit and integration tests (starts a PostgreSQL container)
./gradlew :backend:plugin:build

# Frontend (Node 20, see frontend/.nvmrc)
cd frontend
npm install
npx ng build @valtimo-plugins/smartdocuments
npm run lint
```

For more information on how to build a plugin, see
the [Custom Plugin Definition](https://docs.valtimo.nl/features/plugins/plugins/custom-plugin-definition) documentation.
