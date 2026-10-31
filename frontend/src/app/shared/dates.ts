/** Jour local 'YYYY-MM-DD' (offset en jours) : les inputs date et l'API raisonnent en jours, pas en instants UTC. */
export function localDay(offsetDays = 0): string {
  const d = new Date();
  d.setDate(d.getDate() + offsetDays);
  return new Date(d.getTime() - d.getTimezoneOffset() * 60_000).toISOString().slice(0, 10);
}
