# SmartDocuments plugin

A GZAC/Valtimo plugin that generates documents with the [SmartDocuments](https://www.smartdocuments.nl/) web API.

This plugin used to live in the Valtimo monorepo as `com.ritense.valtimo:smartdocuments`. It has the same functionality and
the same plugin key (`smartdocuments`), so existing plugin configurations and process links keep working.

> **Warning:** never put this artifact (`com.ritense.valtimoplugins:smartdocuments`) on the same classpath as
> `com.ritense.valtimo:smartdocuments`. Both register the plugin key `smartdocuments`, so the application fails to start
> with a duplicate-plugin error. Remove `com.ritense.valtimo:smartdocuments` from your project when you install this plugin.

## Compatibility

The plugin is built and released separately for each Valtimo major version. Use the version that matches your Valtimo
version:

| Valtimo / GZAC | Plugin version | Branch | Java | Angular |
|----------------|----------------|--------|------|---------|
| 13.x | `1.1.0` | [`main`](../../tree/main) | 21 | 19 |
| 12.x | `1.1.0-V12` | [`v12`](../../tree/v12) | 17 | 17 |

Both versions have the same features and the same plugin key, so plugin configurations and process links can move
from Valtimo 12 to 13 without changes.

The Valtimo 12 versions end in `-V12`. npm treats that suffix as a pre-release, so install it with the exact version:
a range like `^1.1.0` does not select it. The example application and the SmartDocuments mock are only available on
`main` (Valtimo 13).

## Features

- **Generate document** (`generate-document`): generates a document (DOCX, HTML, PDF or XML) from a SmartDocuments
  template, filled with data from the case.
- **Keep line breaks**: set the payload format of `generate-document` to `XML` to keep line breaks in text values. See
  the [plugin documentation](documentation/plugin.md#keeping-line-breaks-payload-format-xml).
- **Get template names** (`get-template-names`): fetches the template names of a template group and stores them as a
  list in a process variable.

`generate-document` saves the result as a **temporary resource** and puts its id in the process variable you configure.
The temporary resource is not stored permanently, so another step must store it, for example the Documenten API plugin.

## Installation

### Backend

```kotlin
dependencies {
    // Valtimo 13
    implementation("com.ritense.valtimoplugins:smartdocuments:1.1.0")
    // Valtimo 12
    implementation("com.ritense.valtimoplugins:smartdocuments:1.1.0-V12")
}
```

Remove `com.ritense.valtimo:smartdocuments` from your dependencies (see the warning above).

### Frontend

```shell
# Valtimo 13
npm i @valtimo-plugins/smartdocuments@1.1.0
# Valtimo 12 (exact version, see Compatibility)
npm i @valtimo-plugins/smartdocuments@1.1.0-V12
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
- [Example application](../../blob/main/documentation/example-application.md) (`main` branch, Valtimo 13): running the example app locally and testing the plugin with the included SmartDocuments mock
- [Getting started](documentation/getting-started.md): development instructions
- [Release notes](documentation/release-notes.md)

## Contact

Team Valtimo (Ritense), support@ritense.com
