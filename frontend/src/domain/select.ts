/** MUI Select hands back the chosen value as an untyped string or number; these keep ids typed. */

/** The id as a number, or '' for the empty "none" option. */
export function selectValue(value: unknown): number | '' {
  return value === '' || value === null || value === undefined ? '' : Number(value);
}

/** The id as a number, or undefined for the empty "none" option. */
export function optionalId(value: unknown): number | undefined {
  const v = selectValue(value);
  return v === '' ? undefined : v;
}
