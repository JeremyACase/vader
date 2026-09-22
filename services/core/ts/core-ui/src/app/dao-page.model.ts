/** The subset of a DAO query-controller page response every caller here needs. */
export interface DaoPage<T> {
  content: T[];
  totalElements: number;
}
