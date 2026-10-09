# Dynamic DAO query engine

The attacker is any REST or MCP client of the query API, including a task agent.

- `common/java/library/dao` builds JPA Criteria queries from client-supplied filters, sort fields
  and operators. Can any of these reach a string-built query (JPQL/SQL injection), or name an
  attribute, path or join the API shouldn't expose?
- Can a client query entities or fields that aren't meant to be queryable, such as secrets,
  transcripts or another workflow's data, through `VaderDaoRegistry`, the generic controller or the
  `query` tools?
- Page size, nesting depth and operator combinations: can one request force an unbounded or very
  expensive query?
