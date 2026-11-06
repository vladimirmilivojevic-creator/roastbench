# RoastBench

Roast profile and green coffee inventory app for small roasteries.

> **Status: under active development.** The sections below describe the target scope; the repository is built up phase by phase, each phase ending in a working, tested state.

## What it does

- **Imports roast logs** from CSV and from [Artisan](https://github.com/artisan-roaster-scope/artisan) `.alog` files.
- **Calculates** rate of rise (RoR), development time ratio (DTR) and roast loss for every batch.
- **Compares** each batch with a reference profile in an interactive chart (bean/environment temperature, RoR, roast events, drying / Maillard / development phases).
- **Tracks green coffee**: lots with origin details, an append-only stock-movement ledger, automatic stock deduction when a roast is recorded, and low-stock alerts.
- **REST API** documented with OpenAPI.

## Tech stack

Java 21 · Spring Boot 3 · PostgreSQL · Flyway · React · TypeScript · MUI · Apache ECharts · JUnit 5 · Testcontainers · Playwright · Docker · GitHub Actions

## Sample data

All roast curves shipped with this repository (`samples/` and the demo seed) are **synthetic**: they are generated to look like real roasts, but they do not come from a real roaster.

## License

[MIT](LICENSE)
