# dao

A dynamic JPA Criteria query engine, ported and trimmed from ubiquia. Given an entity `Class`
and either a structured `QueryFilter` or a raw request-parameter map, it builds paginated,
sorted, predicate-filtered queries at runtime — no hand-written repositories.

## Pieces

- **`model/`** — `QueryFilter` (an `AND`-combined list of `QueryFilterParameter{key, operator,
  value}`, plus `sort` / `sortBy` / `page` / `pageSize`), `QueryOperatorType`
  (`EQUAL`, `LIKE`, `GREATER_THAN`, `GREATER_THAN_OR_EQUAL_TO`, `LESS_THAN`,
  `LESS_THAN_OR_EQUAL_TO`), `SortType`, and `GenericPageImplementation` (a JSON-stable
  `PageImpl`).
- **`component/EntityDao<T>`** — the entry point. `getPage` / `getCount` from a `QueryFilter` or
  a `Map<String,String[]>`.
- **`component/FilterDao<T>` / `ParameterDao<T>`** — the two query builders behind `EntityDao`.
- **`service/builder/`**, **`service/logic/`** — predicate construction and reflection helpers.
- **`service/PageValidator`** — caps page size at `vader.dao.max-page-size` (default 100).
- **`controller/GenericVaderDaoController<T,D>`** — an abstract read-only REST controller. A
  subclass annotates itself `@RestController @RequestMapping("/...")` and supplies its
  `EntityDao` + entity→DTO mapper. Exposes, relative to its base path:
  - `GET  /query/params` — `?field=value`, `?field*=%like%`, `?field>=...`, `?field=null`
  - `GET  /query/{id}`
  - `POST /query` — a `QueryFilter` body
  - `POST /query/count` — a `QueryFilter` body → `{"count": n}`

## Keychains

A parameter `key` may be a dotted path through associations, e.g.
`{"key": "taskPlan.objective", "operator": "LIKE", "value": "%party%"}` on `WorkflowEntity`
joins through to the task plan. A `value` of `"null"` / `"!null"` tests nullness.

## Notes

- `commons-lang3` + `org.reflections` are used for field discovery and (for JOINED inheritance)
  subclass resolution. vader has no polymorphic entity families today, so the subclass scan is
  effectively a no-op — a bad field name just yields `NoSuchFieldException`.
- ubiquia's `getPageMultiSelect` (projection queries) and its micrometer / ingress-response
  plumbing were not ported.
