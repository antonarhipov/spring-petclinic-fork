# Spec Review: Smart Appointment Scheduling

## Summary

- Feature: Smart Appointment Scheduling
- Verdict: PASS
- Counts: 0 blockers, 0 majors, 0 minors
- Action: The relative-calendar extension is ready for implementation planning.

## Discipline Check

Pass. All 300 behavior IDs resolve from 374 acceptance criteria. The added UC3-B58 through UC3-B69 behaviors are covered by UC3-AC67 through UC3-AC83, every new criterion maps to shared or UC3 rules, and the criteria use event, combined, unwanted-behavior, and lower/upper/outside boundary forms supported by deterministic clock and calendar seams.

## Conflicts

Pass. Relative expressions remain anchored at confirmation as required by the existing request lifecycle. Monday-through-Sunday resolution, clipped month rows, Thursday-through-Sunday end-of-week eligibility, hard allowed ranges, soft preferred ranges, exclusions, and horizon clarification compose with UC4's existing owner-window hard constraint and preferred-window first soft rank. Normalized symbolic and materialized rows supersede the lossy legacy scalar window fields without changing staff fallback's independent direct-booking boundary.

## Codebase Grounding

Pass. `java.time`, the injected `Clock` and clinic `ZoneId`, Spring Data JPA, vendor-specific Flyway migrations, and the existing `CandidateSlot` allowed/excluded/preferred flags provide viable implementation seams. H2, MySQL, and PostgreSQL all support the required date, time, timestamp, integer, string-enum, foreign-key, and ordered child-row representation. No new library is required. The current code demonstrably drops `responseDto.windows()` and hardcodes candidate window flags, so the planned child repositories, confirmation materializer, and problem-factory integration have concrete homes.

## EARS ↔ Test Strategy

Pass. Table-driven resolver tests can freeze confirmation dates around week, month, year, and horizon boundaries; repository tests can verify symbolic/materialized replacement and cascade behavior; AI adapter tests can exercise strict schema combinations; problem-factory and Timefold tests can assert hard allowed/excluded eligibility and soft preferred ranking; effective-availability fixtures cover closed and open weekends. Negative criteria can assert that clarification prevents solver dispatch.

## Risk Hotspots

1. Closed AI schema evolution / existing model responses omit newly required fields / keep backward-incompatible validation explicit and cover representative Ollama JSON with adapter tests.
2. Calendar composition / weekday, ordinal week, month, and time intersections can silently become unions / centralize expansion in one table-driven resolver with exact expected intervals.
3. Persistence replacement / stale symbolic or materialized rows could survive text replacement or reconfirmation / replace request-owned rows transactionally and test lifecycle cleanup.
4. Feasibility drift / solver and live hold validation may read different owner-window representations / make materialized intervals the sole shared source and test both paths.
5. Migration portability / three database dialects differ in identity and timestamp syntax / add matching V3 migrations and exercise the existing database migration matrix.
