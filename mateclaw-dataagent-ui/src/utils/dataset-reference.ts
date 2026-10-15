/** Persisted dataset IDs are valid only while their backing record still exists. */
export function isPersistedDatasetReferenceAvailable(
  datasetId: unknown,
  datasets: readonly { id: unknown }[],
): boolean {
  const id = String(datasetId ?? '')
  if (!/^[1-9]\d*$/.test(id)) return false
  return datasets.some((dataset) => String(dataset.id ?? '') === id)
}
