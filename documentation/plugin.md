# SmartDocuments plugin

## Overview

The SmartDocuments plugin generates documents with the SmartDocuments web API. It can generate a document from a template
with data from a case, and it can fetch the template names of a template group.

The plugin key is `smartdocuments`. This is the same key as the plugin that used to be part of the Valtimo monorepo
(`com.ritense.valtimo:smartdocuments`), so existing plugin configurations and process links keep working.

> **Warning:** never put this artifact on the same classpath as `com.ritense.valtimo:smartdocuments`. Both register the
> plugin key `smartdocuments`, so the application fails to start with a duplicate-plugin error.

## Dependencies

### Backend

```kotlin
dependencies {
    implementation("com.ritense.valtimoplugins:smartdocuments:<version>")
}
```

### Frontend

```json
{
  "dependencies": {
    "@valtimo-plugins/smartdocuments": "<version>"
  }
}
```

In your `app.module.ts`:

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
```

## Migrating from `com.ritense.valtimo:smartdocuments`

1. Remove `com.ritense.valtimo:smartdocuments` from your backend dependencies.
2. Add `com.ritense.valtimoplugins:smartdocuments`.
3. Install `@valtimo-plugins/smartdocuments` and import `SmartDocumentsPluginModule` and
   `smartDocumentsPluginSpecification` from it instead of from `@valtimo/plugin`.

You do not need to change plugin configurations or process links. On startup, Valtimo stores the new class name under the
existing plugin key.

## Configuration

| Property   | Type   | Required | Description                                       |
|------------|--------|----------|---------------------------------------------------|
| `url`      | string | Yes      | The base URL of the SmartDocuments web API        |
| `username` | string | Yes      | The SmartDocuments username                       |
| `password` | string | Yes      | The SmartDocuments password. Stored as a secret.  |

### Application properties

| Property                                  | Default | Description                                   |
|-------------------------------------------|---------|-----------------------------------------------|
| `valtimo.smartdocuments.max-file-size-mb` | `10`    | The maximum size (in MB) of a generated document |

### Autodeployment

You can deploy a plugin configuration on startup with a `*.pluginconfig.json` file:

```json
[
    {
        "id": "b3bfac2b-06bf-4933-8527-af8015335a3d",
        "title": "SmartDocuments",
        "pluginDefinitionKey": "smartdocuments",
        "properties": {
            "url": "${VALTIMO_SMART_DOCUMENTS_URL}",
            "username": "${VALTIMO_SMART_DOCUMENTS_USERNAME}",
            "password": "${VALTIMO_SMART_DOCUMENTS_PASSWORD}"
        }
    }
]
```

## Actions

Both actions can be linked to the start of a service task.

### Generate document (`generate-document`)

Generates a document from a SmartDocuments template, filled with data from the case.

| Parameter                              | Type                       | Required | Description                                                                                         |
|----------------------------------------|----------------------------|----------|-----------------------------------------------------------------------------------------------------|
| `templateGroup`                        | string                     | Yes      | The SmartDocuments template group                                                                    |
| `templateName`                         | string                     | Yes      | The template name within the template group                                                          |
| `format`                               | `DOCX`, `HTML`, `PDF`, `XML` | Yes    | The format of the generated document                                                                 |
| `templateData`                         | array of `{key, value}`    | Yes      | The data sent to the template. A value can be a literal or a value resolver path such as `doc:/name` or `pv:name` |
| `resultingDocumentProcessVariableName` | string                     | Yes      | The process variable that receives the id of the generated temporary resource                        |

The generated document is saved as a **temporary resource**. The action puts the resource id in the process variable
`resultingDocumentProcessVariableName`. The temporary resource is not stored permanently, so a following step must store it,
for example the Documenten API plugin action that stores a temporary document.

The action also publishes a `DossierDocumentGeneratedEvent`, which ends up in the case audit log.

### Get template names (`get-template-names`)

Fetches the names of the templates in a template group. The group is searched recursively, so nested groups are found too.

| Parameter                                      | Type   | Required | Description                                                   |
|------------------------------------------------|--------|----------|---------------------------------------------------------------|
| `templateGroupName`                            | string | Yes      | The name of the template group                                |
| `resultingTemplateNameListProcessVariableName` | string | Yes      | The process variable that receives the list of template names |

If the template group does not exist, the process variable is set to an empty list.

## Usage

1. Go to **Admin → Plugins** and add a SmartDocuments plugin configuration with the URL, username and password.
2. In a BPMN process, link a service task to the SmartDocuments plugin and pick an action.
3. For `generate-document`, add a following task that stores the temporary resource, for example with the Documenten API
   plugin.
