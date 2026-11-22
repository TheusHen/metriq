# Hevy integration and manual goals

## Hevy integration

Hevy is **read-only**. Workouts are pulled from the Hevy API; nothing is written back.

- **API key** is entered in `Settings → Integração Hevy`, trimmed, and stored in
  `EncryptedSharedPreferences` backed by a Keystore `MasterKey` (`HevyKeyStore`).
  It is never logged or rendered as plain text. If the keystore is unavailable the
  app shows a message instead of silently dropping the key.
- **Connect** performs a one-off sync. **Sincronizar** repeats it on demand.
  **Sincronização automática** re-syncs when the app resumes, but only when the
  last successful sync is at least 30 minutes old (and a key is present).
- **Sync** pages the API at 25 workouts per page, up to 3 pages (75 fetched per
  run). Results are deduplicated by workout id; the newest 120 are kept in the
  local settings table. The decoded cache is held in memory for the duration of a
  refresh so a dashboard recomposition does not re-parse the JSON nine times.
- **Failure handling**: a failed sync keeps the existing cache. Network/key/rate
  errors are shown as "Último erro" in settings; a successful sync clears it. If
  encoding the cache fails, the previous value is preserved rather than blanked.
- **Disconnect** (`Desconectar`) clears the key, the workout cache, the last-sync
  and last-error values, and the connected flag. With no key present, cached
  workouts are ignored even if the flag is still set from a stale write.
- **Training signal**: Hevy volume, duration and muscle groups are merged with
  Health Connect energy/minutes/sessions. Both sources count on a mixed day.

## Manual calorie goals

`Editar meta` in the energy-budget card opens `GoalEditorSheet`. Values are saved
locally (`GoalStore`) and applied on the next dashboard refresh. `Voltar ao
sugerido` clears the override.

- The entered calories are checked against a BMR/macro floor (10% maximum deficit
  for minors). A clamped goal shows why in the budget card and in the editor's live
  preview before it is applied.
- Rate warnings are shown when a target weight and deadline imply an unsafe weekly
  rate; the deadline is extended and the reason displayed.
- Minors always see the "não substitui orientação de um responsável ou profissional
  de saúde" advisory exactly once.
- **Composition**: the goal override is applied first, then training-based macro
  adaptation runs on the adjusted targets. The "Macro adaptation" card therefore
  describes the deltas that are actually in the budget shown.
