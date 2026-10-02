# Example Application

This project also contains a working example application which is meant to showcase the plugin.

## Running the example application

All commands below should be run from the **project root** directory.

### Prerequisites

- Java 21
- [Docker (Desktop)](https://www.docker.com/products/docker-desktop/)

### Start docker

Make sure docker is running.

Start with gradle script:

```shell
./gradlew :backend:app:composeUp
```

### Start backend

By gradle script:

```shell
./gradlew :backend:app:bootRun
```

### Start frontend

```shell
nvm use 20
npm run clean
npm install
npm run build
npm start
```

### Keycloak users

The example application has a few test users that are preconfigured.

| Name         | Role           | Username  | Password  |
|--------------|----------------|-----------|-----------|
| James Vance  | ROLE_USER      | user      | user      |
| Asha Miller  | ROLE_ADMIN     | admin     | admin     |
| Morgan Finch | ROLE_DEVELOPER | developer | developer |

## Testing the SmartDocuments plugin

The example application contains a case (`Example`) that tests the whole flow: it generates a document with the
SmartDocuments plugin, stores it in the Documenten API and links it to the zaak, so you can open it in the case.

You don't need a SmartDocuments account. Docker compose starts `smartdocuments-mock` on `http://localhost:8099`, a mock
of the SmartDocuments web API. It fills its templates with the template data the plugin sends and returns real PDF, DOCX,
HTML and XML files. The autodeployed plugin configuration (`SmartDocuments (Autodeployed)`) points to this mock.

### Run the test case

1. Start the backend and frontend (see above) and log in as `admin` / `admin`.
2. Go to **Cases → Example** and start a new case. Fill in the start form:

   | Field          | Example value      | Notes                                                       |
   |----------------|--------------------|-------------------------------------------------------------|
   | Name           | `Jane`             | Sent to SmartDocuments as template data (key `name`)        |
   | Template group | `Example`          | The mock has `Example` and the nested group `Letters`       |
   | Template name  | `Example template` | `Letters` has `Confirmation letter` and `Rejection letter`  |
   | Format         | `PDF`              | `DOCX`, `PDF`, `HTML` or `XML`                              |

3. The process runs these service tasks:

   | Task                                | Plugin action                        | Result                                             |
   |-------------------------------------|--------------------------------------|----------------------------------------------------|
   | Create zaak                         | Zaken API `create-zaak`              | A zaak in Open Zaak, linked to the case            |
   | Get SmartDocuments template names   | SmartDocuments `get-template-names`  | `templateNames` in the case                        |
   | Generate SmartDocuments document    | SmartDocuments `generate-document`   | A temporary resource, its id in `generatedDocumentResourceId` |
   | Store document in Documenten API    | Documenten API `store-temp-document` | The document in Open Zaak, its URL in `storedDocumentUrl` |
   | Link document to zaak               | Zaken API `link-document-to-zaak`    | The document is linked to the zaak                 |

4. Open the case:
   - **Documents** tab: the generated document. Open it to see your data in the letter.
   - **Summary** tab: the input and the results of each step.
   - **Audit** tab: the document generated event.

### The mock UI

Open `http://localhost:8099`:

- **Requests**: every request the plugin sent, with the template data it received. For a generate request you can
  preview and download the document in each format.
- **Templates**: the template groups and templates. Edit the title and body of a letter; `{{key}}` is replaced with the
  value of template data key `key`. **Preview** renders the template with the data of the last request. Edits are saved
  in `backend/app/imports/smartdocuments-mock/data/` (ignored by git); **Reset to defaults** removes them. The defaults
  are in `backend/app/imports/smartdocuments-mock/templates.json`.
- **Settings**: simulate a `400`, `401` or `500` response, for the next request or for all requests, or add a delay.
  The service tasks run when the case starts, so a SmartDocuments error makes starting the case fail.

The mock checks basic auth (`valtimo` / `valtimo`) and returns `400` for a template group or template that doesn't
exist, like SmartDocuments does.

### Use a real SmartDocuments server

Set these variables in `.env.properties` in the project root. They override the mock settings:

```properties
VALTIMO_SMART_DOCUMENTS_URL=https://your-smartdocuments-host/
VALTIMO_SMART_DOCUMENTS_USERNAME=...
VALTIMO_SMART_DOCUMENTS_PASSWORD=...
```

The plugin configuration is only created on the first start. If it already exists, change it in **Admin → Plugins**.

### ZGW services

The ZGW services (Open Zaak, Objecten API, Objecttypen API, Open Notificaties) share the network of the `localhost`
container, which publishes their ports: Open Zaak on `8001`, Objecten API on `8010`, Objecttypen API on `8011` and Open
Notificaties on `8012`. The admin user of each service is `admin` / `admin`.

If you recreate the `localhost` container, the ZGW services lose their network. Recreate them too, or restart the whole
stack. To recreate one service without touching `localhost`, use `--no-deps`:

```shell
cd backend/app
docker compose -p gzac-docker-compose --profile zgw up -d --no-deps --force-recreate <service>
```

## Source code

The source code is split up into two modules:

1. [Frontend](/frontend)
2. [Backend](/backend)
