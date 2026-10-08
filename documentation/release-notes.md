# Release notes

Overzicht van wijzigingen per versie van de SmartDocuments plugin.

## 1.1.0

- New optional property `payloadFormat` (`JSON` or `XML`) on the `generate-document` action. With `XML`, the template data
  is sent as XML and text values with a line break get `xml:space="preserve"`, so SmartDocuments keeps the line breaks.
  The default stays `JSON`, so existing process links don't change.
- With `XML`, a template data key that is not a valid XML element name fails the action with an error that names the key.
- With `XML`, a soft line break (vertical tab, as Word uses) or a page break in a text value becomes a line break, and
  other control characters that XML does not allow are removed.
- In the process-link screen, `format` and `payloadFormat` can be a value resolver (for example `doc:/format` or
  `pv:payloadFormat`): a radio button switches between a fixed value (dropdown) and a value resolver (text field). A
  path from a configuration file is shown and kept when you save the link.

## 1.0.0

- Moved from the Valtimo monorepo, same functionality as Valtimo 13.48.0, plugin key unchanged (`smartdocuments`).
- New coordinates: backend `com.ritense.valtimoplugins:smartdocuments`, frontend `@valtimo-plugins/smartdocuments`.
- Remove `com.ritense.valtimo:smartdocuments` when you install this version. Both artifacts register the same plugin key.
