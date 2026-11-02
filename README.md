# Invoice Scanner for Android

Invoice Scanner turns photos of receipts and invoices into a searchable expense register. Capture or import a document, review the extracted details and amount confidence, categorize the expense, and export the register with supporting images for accounting workflows.

## Download and install

[Download Invoice Scanner v1.7.0 (APK)](https://github.com/Arun-Peter/Android_InvoiceScanner/raw/refs/heads/main/releases/InvoiceScanner-v1.7.0-release.apk)

Requires Android 10 or newer and Google Play services. Open the downloaded APK on your Android device and allow installation from that source if prompted. The document scanner module may need an internet connection for its first download. On a managed device, your organization may restrict sideloading.

## What it does

- Captures or imports a document and helps crop and correct perspective with Google ML Kit's document scanner.
- Uses on-device OCR and a rule-based extractor, with optional AI-assisted extraction via a user-configured Azure OpenAI or OpenAI-compatible service.
- Extracts invoice number, date, vendor, subtotal, tax, amount and currency. It scores confidence in the amount using checks against the document and an independent on-device reading. Review the values before saving: the score is an estimate, not a guarantee.
- Supports business codes, expense categories, narration, duplicate warnings, editing and reclassification.
- Stores document images on the device and maintains a searchable register with date, business and category filters.
- Exports a CSV register, or a ZIP containing the CSV and supporting images, for downstream accounting automation.
- Offers optional biometric/device-credential unlock and experimental authenticator-based app locking.

## Privacy and AI settings

The on-device text reader and rule-based extraction can work without an AI service. When AI extraction is enabled, the cropped invoice image and OCR text are sent to the endpoint you configure. Use a service appropriate for your documents and organization. Configure the provider and credentials in the app's settings; the downloadable APK has no preconfigured AI key or endpoint.

Treat API keys as secrets. Do not commit `local.properties`, signing keys, invoices, exports, or other private data. Embedding a key in an APK exposes it to anyone who can inspect the app; use an appropriately authenticated service for shared deployments.

## Build from source

Open this repository in Android Studio and let Gradle sync, or install JDK 17, Android SDK platform 35 and Gradle 8.9, then run:

```sh
gradle testDebugUnitTest assembleDebug
```

The debug APK is created under `app/build/outputs/apk/debug/`.

For a signed release, configure your own signing key in the ignored `keystore/` directory and run `gradle assembleRelease`. Updates to an installed APK must be signed with the same key as that installation. The published release was signed with the project owner's key; the private key is not included.

## Project layout

- `app/src/main/` — Kotlin Android application, OCR, extraction, storage, settings and UI
- `app/src/test/` — unit tests for extraction, confidence scoring, register and export logic
- `releases/` — installable APK

## License

No license has been specified. Until one is added, standard copyright restrictions apply to reuse of the source.
