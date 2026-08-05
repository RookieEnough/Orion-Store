# Orion Store translations

Orion Store changes the interface language immediately without restarting the
application. The preference selected under **Settings > Language** is saved on
the device.

## Translation system files

- `locales/en/*.json`: the original English source catalogs. They are generated
  automatically and should be used as the reference.
- `locales/pt-BR/*.json`: the Brazilian Portuguese catalogs. Translate the
  values in these files.
- `i18n.ts`: loads the catalogs, detects the device language, saves the user's
  selection, and updates the interface without reloading the application.
- `components/SettingsModal.tsx`: contains the language choices displayed in
  Settings.
- `scripts/extract_translations.mjs`: finds translatable text in the source code
  for the main React interface.
- `scripts/extract_domain_translations.mjs`: extracts and maintains the
  catalogs for the other project areas.
- `scripts/extract_all_translations.mjs`: runs both extractors through the
  `translations:extract` package command.
- `utils/i18n.test.ts`: tests translation and live language switching without a
  restart.
- `index.tsx`: starts the translation observer with the application.

## Translating existing text

Open the appropriate JSON file under `locales/pt-BR/` and translate only the
value after the colon. Do not change the key.

```json
{
  "app_detail.download": "Translated download label",
  "settings_modal.language": "Translated language label"
}
```

A value that is still written in English is pending translation. Product names
and terms such as `Orion Store`, `GitHub`, `Android`, and `APK` may remain
unchanged.

## Catalogs and their source files

| Catalog | Source area | What belongs in it |
| --- | --- | --- |
| `common.json` | `App.tsx`, `components/`, `hooks/`, `constants.ts`, `utils/discovery.ts` | Main React interface labels, buttons, dialogs, messages, placeholders, and accessibility text |
| `app-content.json` | `localData.ts` | App descriptions, patch names, categories, platforms, versions, and size labels |
| `dino.json` | `public/dino/index.html`, `public/dino/game.html`, `public/dino/index.js` | Visible minigame titles and instructions |
| `redirect.json` | `docs/redirect.html` | Visible redirect-page labels, status messages, errors, and buttons |
| `android.json` | `android/app/src/main/res/values/strings.xml`, `DownloadForegroundService.java`, `AppTrackerPlugin.java` | Native notification titles, progress messages, and Android channel descriptions |
| `workers.json` | `workers/` | Worker errors that can reach users and discovery content produced by the core worker |

Tests, generated files under `android/app/src/main/assets/`, CSS classes, URLs,
package names, protocol values, internal event names, hashes, and source-code
comments are intentionally excluded because they are not user-facing text or
must remain technically exact.

Dynamic messages contain placeholders such as `{{count}}`, `{{version}}`, or
`{{app.name}}`. Keep them exactly as written in the translated value:

```json
{
  "updates_available": "{{count}} translated updates message"
}
```

## Adding new interface text

Add the text normally to its corresponding component. The extractor primarily
scans:

- `App.tsx`;
- every `.tsx` file under `components/`;
- `.ts` and `.tsx` files under `hooks/`;
- `constants.ts`;
- `utils/discovery.ts`.

Then run:

```sh
npm run translations:extract
```

This command updates all six English and Portuguese catalogs. Existing
Portuguese translations are preserved. Newly discovered entries initially use
their English source value, making pending translations easy to find.

The extractor recognizes visible JSX text and fields such as `title`, `label`,
`description`, `desc`, `message`, `placeholder`, `eyebrow`, `meta`, `alt`, and
`aria-label`. If text is stored in another type of field or in a directory that
is not scanned, update `sourceFiles`, `translatableAttributes`, or
`translatableProperties` in `scripts/extract_translations.mjs`.

## Adding another language

For example, to add Spanish:

1. Create an `es` directory and copy every JSON file from `locales/en/` into
   `locales/es/`, then translate only the values.
2. In `i18n.ts`, import the new catalog, add `es` to `LanguagePreference` and
   `ResolvedLanguage`, and include the catalog in the translation function.
3. Update `resolveLanguage()` in `i18n.ts` if the language should also be
   selected automatically from the device language.
4. Add the new language choice to the language panel in
   `components/SettingsModal.tsx`.
5. Add cases to `utils/i18n.test.ts` to verify translation, persistence, and
   switching without a restart.
6. Run the validation commands:

```sh
npm run translations:extract
npm run lint
npm test -- --run
npm run build
```

You do not need to modify `index.tsx` for each new language. It only starts the
shared translation system.

## Prepared language templates

The repository includes ready-to-translate folders for 32 locales. The complete
list and translation status are stored in `locales/locales.json`. Each folder
contains the same six JSON catalogs as the English source.

Run the following command after adding a locale to `supportedLocaleTemplates`
in `scripts/scaffold_locales.mjs`:

```sh
npm run translations:scaffold
```

The scaffold command preserves existing translated values and adds missing
source keys in English. A locale marked `template` must not be enabled in the
application's language selector until its values have been translated and
reviewed. Change its manifest status only after that review.

Arabic and Hebrew are marked with `direction: "rtl"`. Enabling either language
also requires verifying right-to-left layout behavior throughout the UI.

## Resetting a translation catalog

The following command discards the current `pt-BR` translations and recreates
the file from the English source:

```sh
npm run translations:extract -- --reset
```

Use `--reset` only when you intentionally want to lose completed translations.
