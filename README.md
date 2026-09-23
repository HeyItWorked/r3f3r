# r3f3r

Track specialist referrals and see which ones need a follow-up.

## How it works

![Request path](docs/diagrams/architecture.svg)

One page, one table, one path: native fetch through a Vite proxy to Spring, overdue computed on the way back.

![API sequence](docs/diagrams/sequence.svg)

Three calls: list newest-first, create as NEW (201), patch status only (200). Bad input gets 400 with field errors; unknown IDs get 404.

![Status states](docs/diagrams/state.svg)

NEW → SENT → DONE, freely reversible for corrections. Saving the unchanged status is a no-op. Done → Sent re-arms overdue.

![Referral record](docs/diagrams/data-model.svg)

One table, six fields, zero joins. Overdue is computed on read and never stored. Fictional demo rows only (`DEMO-101`).

## First version

The page has an add form (patient reference, specialist office, follow-up date) above a referral table with a status dropdown and Save per row. A referral shows Overdue when its follow-up date is before today and its status is not Done; today is not overdue.

Vue 3 + Vite with plain JavaScript and CSS (no Router, Pinia, component lib). Java 21 Spring Boot API (`backend/pom.xml` pins Boot 3.5.16), Spring Data JPA against Oracle Database Free.

## Demo data only

Use fictional references such as DEMO-101 and invented office names. Do not enter patient names, dates of birth, diagnoses, or clinical notes.

This is a local learning application, not software for clinical use. No login, messaging, attachments, or connections to healthcare systems.

## Running the app

The application is not implemented yet, so there is nothing to run. Work is tracked in the [roadmap](docs/roadmap.md) and [GitHub issues](https://github.com/HeyItWorked/r3f3r/issues).
