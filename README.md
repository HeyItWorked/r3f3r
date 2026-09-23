# ReferralFlow

Track specialist referrals and see which ones need a follow-up.

ReferralFlow is a small practice project for recording a fictional patient reference, a specialist office, and a follow-up date. Staff will use one page to add referrals and change their status.

The repository currently contains the build plan. The application has not been implemented, so there is nothing to run yet. Work is tracked in the [roadmap](docs/roadmap.md) and [GitHub issues](https://github.com/HeyItWorked/referralflow/issues).

## First version

The page will have an add form above a referral table. Each referral starts as New and can move to Sent or Done. A status can be changed back if someone makes a mistake.

A referral will show an Overdue label when its follow-up date is before today and its status is not Done. Referrals due today will not be overdue.

The build uses Vue 3 with Vite and plain JavaScript, a Java 21 Spring Boot API, and Oracle. Spring Data JPA handles persistence. The frontend uses native fetch and plain CSS.

## Demo data only

Use fictional references such as DEMO-101 and invented office names. Do not enter patient names, dates of birth, diagnoses, or clinical notes.

This is a local learning application, not software for clinical use. The first version has no login, messaging, attachments, or connections to healthcare systems.

## Running the app

Setup instructions will be added once the frontend, backend, and Oracle connection work from a clean clone. The [roadmap](docs/roadmap.md#build-order) lists the tasks needed to reach that point.
