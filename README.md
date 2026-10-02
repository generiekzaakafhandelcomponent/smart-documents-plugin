# SmartDocuments plugin

A GZAC/Valtimo plugin that generates documents with the [SmartDocuments](https://www.smartdocuments.nl/) web API.

This plugin used to live in the Valtimo monorepo as `com.ritense.valtimo:smartdocuments`. It has the same functionality and
the same plugin key (`smartdocuments`), so existing plugin configurations and process links keep working.

> **Warning:** never put this artifact (`com.ritense.valtimoplugins:smartdocuments`) on the same classpath as
> `com.ritense.valtimo:smartdocuments`. Both register the plugin key `smartdocuments`, so the application fails to start
> with a duplicate-plugin error. Remove `com.ritense.valtimo:smartdocuments` from your project when you install this plugin.

## Features

- **Generate document** (`generate-document`): generates a document (DOCX, HTML, PDF or XML) from a SmartDocuments
  template, filled with data from the case.
- **Get template names** (`get-template-names`): fetches the template names of a template group and stores them as a
  list in a process variable.

`generate-document` saves the result as a **temporary resource** and puts its id in the process variable you configure.
The temporary resource is not stored permanently, so another step must store it, for example the Documenten API plugin.

## Installation

### Backend

```kotlin
dependencies {
    implementation("com.ritense.valtimoplugins:smartdocuments:<version>")
}
```

Remove `com.ritense.valtimo:smartdocuments` from your dependencies (see the warning above).

### Frontend

```shell
npm i @valtimo-plugins/smartdocuments
```

Add the module and the specification to your `app.module.ts`:

```typescript
import {SmartDocumentsPluginModule, smartDocumentsPluginSpecification} from '@valtimo-plugins/smartdocuments';

@NgModule({
    imports: [
        SmartDocumentsPluginModule,
    ],
    providers: [
        {
            provide: PLUGINS_TOKEN,
            useValue: [
                smartDocumentsPluginSpecification,
            ],
        },
    ],
})
export class AppModule {}
```

If you imported `SmartDocumentsPluginModule` or `smartDocumentsPluginSpecification` from `@valtimo/plugin` before, change
the import to `@valtimo-plugins/smartdocuments`.

## Configuration

| Property   | Required | Description                                  |
|------------|----------|----------------------------------------------|
| `url`      | Yes      | The base URL of the SmartDocuments web API   |
| `username` | Yes      | The SmartDocuments username                  |
| `password` | Yes      | The SmartDocuments password (stored as secret) |

### Application properties

| Property                                | Default | Description                                         |
|-----------------------------------------|---------|-----------------------------------------------------|
| `valtimo.smartdocuments.max-file-size-mb` | `10`    | The maximum size (in MB) of a generated document |

## Documentation

- [Plugin documentation](documentation/plugin.md): configuration, actions and usage
- [Example application](documentation/example-application.md): running the example app locally and testing the plugin with the included SmartDocuments mock
- [Getting started](documentation/getting-started.md): development instructions
- [Release notes](documentation/release-notes.md)

## Contact

Team Valtimo (Ritense), support@ritense.com
