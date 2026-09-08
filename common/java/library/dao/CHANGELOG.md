# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.8.0]
### Added
- Initial module — a dynamic JPA Criteria query engine ported from ubiquia's
  `common/java/library/dao`, package `org.vader.common.library.dao`:
  - `model` — `QueryFilter`, `QueryFilterParameter`, `QueryOperatorType`, `SortType`,
    `GenericPageImplementation`.
  - `component` — `EntityDao<T>` (facade), `FilterDao<T>` (structured filter),
    `ParameterDao<T>` (request-param map). Both support dotted keychains that join through
    associations, `LIKE` / range / `null` operators, sort, and pagination.
  - `service/builder` — `NonNestedPredicateBuilder`, `NestedPredicateBuilder`.
  - `service/logic` — `ClassDeriver` (Reflections scoped to `org.vader`), `EntityDeriver`,
    `EmbeddableDeriver`.
  - `service/PageValidator` — `vader.dao.max-page-size` (default 100).
  - `interfaces/InterfaceVaderDaoController`, `controller/GenericVaderDaoController<T,D>` — an
    abstract read-only REST controller exposing `GET /query/params`, `GET /query/{id}`,
    `POST /query`, `POST /query/count`.
- Dependencies: `spring-boot-starter-data-jpa` / `-web` / `-validation`, `commons-lang3`,
  `org.reflections:reflections:0.10.2`.
### Not ported
- `getPageMultiSelect` (projection queries); ubiquia's micrometer telemetry,
  `IngressResponseBuilder`, and `InterfaceLogger` plumbing.
