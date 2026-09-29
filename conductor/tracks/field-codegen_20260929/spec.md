# Spec: Parametrized field-codegen (entity-field list → case class + migration + codec)

## Overview
Adds a standalone codegen tool that extends a service already generated from this repo's giter8
template (previous track) with extra domain fields beyond the fixed `item`/`quantity`/`status`
trio - driven by a YAML field-spec file, applied as a post-generation step. Coordinates edits
across the domain case class, `*Store` (in-memory + Postgres, including SQL), migration,
request/response DTOs, and every existing test call site whose arity changes.

## Functional Requirements
1. **Field-spec file (YAML):** a list of fields, each with `name` (camelCase), `type` (one of
   `String`, `Int`, `Boolean`, `Instant`), and `example` (a literal value used to rewrite existing
   test call sites and seed the tool's own new assertions). All fields are required (NOT NULL) -
   no optional/nullable support in v1.
   ```yaml
   fields:
     - name: sku
       type: String
       example: "WIDGET-1"
     - name: weightGrams
       type: Int
       example: 250
   ```
2. **Standalone sbt tool** (`tools/codegen/`, own `build.sbt`, sibling to - NOT aggregated into -
   the reference service's build, so neither the reference service nor the generated template
   becomes multi-module). Invoked as `sbt run <generated-project-dir> <field-spec.yaml>`.
3. **Every field is added to all of:** the domain case class, `Create*Request`, `Update*Request`,
   `*Response` (+ its `apply(entity)` conversion), `*Store.create`/`update` signatures (both
   in-memory and Postgres backends, including the INSERT/SELECT/UPDATE SQL column lists and tuple
   (de)construction), the Flyway migration's `CREATE TABLE` columns, and the route handlers'
   pass-through of request fields into `store.create`/`update` calls. New fields are inserted
   between `status` and `createdAt` in field order.
4. **Rewrites every existing test call site** whose arity changes (`*StoreSuite`,
   `*StorePostgresSuite`, `*RoutesSuite`, `*DocsSuite`) using each field's `example` value, and
   updates `MigrationsSuite`'s expected-columns assertion.
5. **Real unit tests (munit)** for the tool's own transformation logic - not just an end-to-end
   smoke check - per this project's TDD workflow, since the tool rewrites other people's
   generated source.
6. **Stable anchor comments** (e.g. `// codegen:fields`) embedded in the g8 template's generated
   output at every insertion point, so the tool hooks into known markers rather than
   pattern-matching against Scala/SQL code shape.

## Acceptance Criteria
- Given a fresh `widget` service (generated per the previous track) and a field-spec file with 2+
  fields, running the tool produces a project whose `sbt scalafmtCheck test` passes, exercising
  the new fields through create/get/update/delete and the full CRUD lifecycle test.
- The tool's own unit test suite covers its transformation logic per file/anchor point.
- Running the tool twice (idempotency) is explicitly out of scope - not designed for reapplication
  or incremental field addition, just one field-spec applied once to a freshly generated project.

## Out of Scope
- Optional/nullable fields (`Option[T]`, nullable columns) - fast-follow once required-field path
  is proven.
- Field types beyond `String`/`Int`/`Boolean`/`Instant`.
- Per-field visibility control (create-only, response-only, etc.) - every field appears in all of
  create/update/response.
- Re-running the tool on an already-codegen'd project, or removing/renaming fields.
- Automated generation → repo-creation → push → CI pipeline (separate backlog item).
