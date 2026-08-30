# Timefold Slot-Selection Boundary

## Authority and scope

- Timefold selects every automated owner-facing slot: the initial suggestion, every requested replacement, and every owner-authorized fallback. No controller, repository query, stream sort, or staff automation may choose such a slot outside this boundary.
- Staff may manually choose an exact slot for an offer or direct booking without Timefold. It still passes the same authoritative availability, conflict, and hold transaction.
- One solve handles one immutable confirmed request revision against one fixed calendar snapshot. Existing appointments are problem facts and can never move.
- A solver result is advisory and owner-invisible until the hold transaction succeeds.

## Planning model

`SlotSelectionSolution` contains:

- exactly one nullable `SlotAssignment` planning entity;
- a `CandidateSlot` planning value range;
- immutable request, policy, veterinarian, capacity, appointment, hold, and exclusion facts;
- a `BendableScore` with one hard and four lexicographic soft levels.

The application enumerates possible 15-minute starts in deterministic `(startAt, veterinarianId)` order within the snapshot horizon. Enumeration is not selection: Timefold scores every candidate, including hard-ineligible candidates, and assigns the best value. A null assignment and every hard-ineligible assignment have a negative hard score. Only hard score zero can proceed to hold acquisition.

Timefold configuration:

- Environment/reproducibility mode is deterministic.
- The random seed is fixed even though this phase contains no intentional randomized search.
- `moveThreadCount=NONE` (Community Edition default; no parallel move evaluation).
- One construction-heuristic phase uses `ALLOCATE_ENTITY_FROM_QUEUE`.
- Forager `pickEarlyType=NEVER` ensures all candidate values are compared.
- There is no local-search phase.
- Candidate/fact collections have stable ordering and equality keys.
- The attempt receives only the time remaining from the original five-second monotonic deadline.

A watchdog marks the attempt expired and calls `terminateEarly()` at the deadline. Timefold can return a partial best without a public termination reason, so the adapter accepts a solution only when the construction phase completed naturally and returned before the deadline flag/time. Any partial or deadline-crossed result is `TIMEOUT` and never becomes an offer.

## Versioned input snapshot

The persisted canonical input contains:

| Field group | Required contents |
|---|---|
| Contract | `snapshotSchemaVersion`, `solverConfigurationVersion`, execution/attempt IDs |
| Request | Request/revision IDs and versions, solve mode, duration, care type/specialty, veterinarian preference/requirement |
| Time | Clinic zone, fixed `now`, owner notice boundary, booking-horizon end, grid minutes |
| Windows | Concrete allowed, preferred, fallback-allowed, and excluded instants |
| Exclusions | Rejected/expired `(veterinarianId,startAt)` keys for this revision |
| Catalog | Stable veterinarian IDs and specialty assignments |
| Capacity | Clinic hours/closures, shifts/exceptions/leave, policy/configuration versions |
| Reservations | Existing appointments and unexpired holds/resource blocks for veterinarian and pet |
| Candidates | Stable ID, veterinarian, start/end, preference classification, and stable ordinal |

Every input record uses stable IDs and explicit instants. The snapshot hash is retained with canonical JSON. Attempt 2 after a stale acquisition has a new snapshot, version, and hash under the same execution.

Example excerpt:

```json
{
  "snapshotSchemaVersion": "1.0",
  "solverConfigurationVersion": "slot-selection-1",
  "requestId": 41,
  "requestRevisionId": 3,
  "requestRevisionVersion": 2,
  "mode": "PREFERRED_ONLY",
  "clinicZone": "Europe/Amsterdam",
  "now": "2026-09-01T08:00:00Z",
  "noticeBoundary": "2026-09-01T10:00:00Z",
  "horizonEnd": "2026-11-30T22:59:59Z",
  "durationMinutes": 30,
  "configurationVersion": 17,
  "candidates": [
    {
      "id": "3@2026-09-02T09:30:00Z",
      "veterinarianId": 3,
      "startAt": "2026-09-02T09:30:00Z",
      "endAt": "2026-09-02T10:00:00Z",
      "preferenceClass": "STANDARD",
      "stableOrdinal": 12
    }
  ]
}
```

## Shared scoring policy

`SlotScorePolicy` is a pure function used by Timefold's stateless `EasyScoreCalculator`. It returns both `BendableScore` and an `SlotScoreComponents` structure serialized for audit. There is one formula, so the persisted explanation cannot drift from solver scoring.

### Hard level

Each violated rule contributes a named negative component:

1. assignment exists;
2. start and duration align to the 15-minute grid;
3. start is at/after owner minimum notice and end is inside horizon;
4. full interval is inside a confirmed allowed window and outside every explicit/revision exclusion;
5. clinic is open and not closed for every occupied block;
6. veterinarian is working after closure → leave → date exception → recurring shift precedence;
7. veterinarian satisfies specialty and any staff-confirmed required-veterinarian constraint;
8. veterinarian and pet have no overlapping appointment or active hold block;
9. in `PREFERRED_ONLY`, the candidate is `STANDARD` rather than fallback.

The adapter distinguishes outcomes using named components:

- hard score zero: selected candidate may proceed to hold acquisition;
- only the preferred-tier rule fails while base eligibility passes: `NO_PREFERRED_MATCH`;
- no base-hard-feasible candidate: `NO_FEASIBLE_SLOT`.

### Soft levels

Levels are maximized in this exact order and therefore cannot trade against one another:

1. **Owner preference**: matched preferred windows and veterinarian preference. In fallback mode this still selects the least compromising allowed candidate.
2. **Earliest suitable start**: earlier eligible instant wins after preference fit.
3. **Clinic efficiency**: deterministic adjacent-gap measure; only breaks equal preference and time. It never performs workload balancing or displaces an earlier slot.
4. **Stable key**: negative stable candidate ordinal derived from start and veterinarian ID, guaranteeing a final repeatable tie-break.

The audit explanation contains each named component, its signed value and score level, the total score, and the public explanation code. It contains no clinical reasoning generated by the LLM.

Timefold 2.5 Community Edition's enterprise-only `SolutionManager.analyze` is not called. Tests prove the `EasyScoreCalculator` result equals `SlotScorePolicy.score()` and repeated full solves choose the same candidate/explanation.

## Preferred and fallback modes

`PREFERRED_ONLY` is used for an initial or replacement request. A candidate is `STANDARD` only when it meets confirmed preferred-window and preferred-veterinarian criteria when those criteria exist. If base-eligible candidates exist only outside that tier, the result is `NO_PREFERRED_MATCH`; no candidate is exposed or held.

Selecting **Find an alternative** creates a new `ALLOWED_FALLBACK` attempt from a fresh snapshot. Timefold may then select a hard-allowed candidate outside one or more preferences, using the same soft order. Its eventual offer is clearly `FALLBACK`. Selecting **Forward to staff** creates no solve.

After rejection or expiry, a new suggestion starts in `PREFERRED_ONLY` again and includes the revision's exact veterinarian/time exclusions. After five rejected/expired offers, no new solve is created.

## Result contract

Persist one of:

```json
{
  "outcome": "SELECTED",
  "selectedCandidate": {
    "candidateId": "3@2026-09-02T09:30:00Z",
    "veterinarianId": 3,
    "startAt": "2026-09-02T09:30:00Z",
    "endAt": "2026-09-02T10:00:00Z",
    "classification": "STANDARD"
  },
  "score": {
    "hard": [0],
    "soft": [2, -29811810, -1, -12]
  },
  "publicExplanationCode": "PREFERRED_TIME_AND_VETERINARIAN",
  "scoreComponents": [
    { "name": "preferred-window", "level": "soft-0", "value": 1 },
    { "name": "preferred-veterinarian", "level": "soft-0", "value": 1 }
  ],
  "completedNaturally": true
}
```

Outcomes are `SELECTED`, `NO_PREFERRED_MATCH`, `NO_FEASIBLE_SLOT`, `TIMEOUT`, `ERROR`, and `SUPERSEDED`. Only a whitelisted public explanation code reaches an owner. Raw score, candidate set, component detail, input snapshot, timing, and failures remain staff/audit data.

## Hold acquisition and stale retry

For `SELECTED`, a short transaction:

1. locks and rechecks request/revision state and versions;
2. reloads the policy and availability facts that affect the candidate;
3. expires target blocks whose holds are authoritatively expired;
4. revalidates the selected interval;
5. inserts every veterinarian and pet `ReservationBlock`;
6. creates one Offer and Hold and transitions request to `OFFER_HELD`.

Unrelated calendar-version changes do not invalidate a still-eligible candidate. A conflicting block or changed affecting fact means the result was stale. The attempt records `STALE_ACQUISITION`, exposes nothing, refreshes the snapshot, and runs Timefold once more if the shared monotonic deadline has time remaining. The second solve plus acquisition uses that same original five-second budget. A second conflict, timeout, error, or superseded revision routes the retained request to staff.

The following are forbidden:

- persisting an owner-visible offer before all blocks commit;
- returning the first stale candidate while retrying;
- starting a third solver attempt;
- extending the deadline for snapshot refresh or hold acquisition;
- selecting a fallback candidate before explicit owner authorization.
