# MILESTONE.md: Lab 7 working log

A step-by-step record of how the three milestones were done, with the evidence
behind each claim in `REFACTOR.md`. Work happens on branch `lab07-refactor`.
Agent: Claude Code, model Claude Opus 5.5 (`claude-opus-5-5`).

---

## Step 0: Baseline

- Toolchain: `java --version` gives Temurin 21.0.8; `mvn --version` gives Maven 3.9.16.
- `mvn -B test` on the untouched starter (commit `cedea51`):

```
Tests run: 4,  ... NotificationHubTest
Tests run: 7,  ... ReportServiceTest
Tests run: 18, ... BookingWorkflowTest
Tests run: 6,  ... PriceCalculatorTest
Tests run: 35, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

---

## Milestone 1: Characterization first, then refactor

### Step 1.1: Orientation: what `BookingWorkflow` does vs. what the tests pin

`BookingWorkflow` switches on `BookingType` in four methods: `submit` (l.55),
`cancel` (l.187), `priceOf` (l.237) and `describe` (l.272).

These behaviors are in the code but **not pinned** by any shipped test:

| # | Behavior | Where | Closest shipped test and why it misses |
|---|----------|-------|----------------------------------------|
| 1 | RECURRING overlap is **inclusive** (`<= 0`); REGULAR and BLOCKED are exclusive (`< 0`). A series skips a week that only touches an existing booking. | `submit` l.121-122 vs l.68-69, l.152-153 | `recurringSubmitBooksEveryWeekOfAnOpenSeries` uses an empty room, so nothing is ever skipped |
| 2 | Occurrence index = week + 1, so skipped weeks leave gaps in numbering | `submit` l.133 | Only `describe` pins index 1 |
| 3 | Cancelling a RECURRING occurrence cancels it **and every later one**, never earlier ones, with one notification each | `cancel` l.199-210 | `recurringCancelReleasesTheOccurrence` cancels the *last* occurrence, so "later ones" is never exercised |
| 4 | `priceOf` on any occurrence sums the **whole live series**, including earlier occurrences | `priceOf` l.246-251 | `priceOfRecurringSumsTheOccurrences` asks about the *first* occurrence only |
| 5 | RECURRING never checks the member's other bookings (REGULAR does) | `submit` l.75-84 has no RECURRING twin | none |
| 6 | A series with every week taken: `accepted=false`, but a series id is still consumed | `submit` l.113, `BookingOutcome.series` | none |
| 7 | REGULAR cancel ignores `adminOverride`; a missing member makes the cancel go to facilities | `cancel` l.188-194, `recipientFor` | none |
| 8 | Notification subject and body text for recurring, blocked and cancel messages | throughout | Only the REGULAR confirmation text is pinned (`NotificationHubTest`) |

**Picked #1 (with #2 folded in).** Of all of these, #1 is the one a
refactor to polymorphism is most likely to change by accident. The three
overlap loops look like duplicate code, and "extract a shared `overlaps()`
helper" is the natural move.

### Step 1.2: The pin

- File: `src/test/java/edu/cmu/cs214/scheduling/workflow/BookingWorkflowCharacterizationTest.java`
  (a new class, so no existing test method is touched).
- Test: `recurringSubmitSkipsAWeekThatOnlyTouchesAnExistingBooking`.
- Setup: room C-200. Grace (m-2) holds a REGULAR booking Mon 2026-10-05 08:00-09:00.
  Ada (m-1) requests RECURRING 09:00-10:00 for 3 weeks.
- Asserts:
  - accepted;
  - `getSkipped() == [2026-10-05 09:00-10:00]`;
  - message is `series S-1: 2 booked, 1 skipped`;
  - first booked is week 2 with index 2, second has index 3;
  - outbox size 3.

Run against the shipped code:

```
Tests run: 1,  ... BookingWorkflowCharacterizationTest
Tests run: 18, ... BookingWorkflowTest
Tests run: 36, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

**The mutation check (does the pin actually bite?).** I temporarily changed
`BookingWorkflow.java` l.121-122 from `<= 0` to `< 0`, ran the suite, then
restored the file with `git checkout`:

```
[ERROR] Tests run: 1, Failures: 1 ... BookingWorkflowCharacterizationTest
        recurringSubmitSkipsAWeekThatOnlyTouchesAnExistingBooking <<< FAILURE!
Tests run: 18, Failures: 0 ... BookingWorkflowTest
Tests run: 36, Failures: 1, Errors: 0, Skipped: 0
```

So all **35 shipped tests stay green** under the mutation. Green was not pinned.

### Step 1.3: Commit the pin before any refactor

The pin section of `REFACTOR.md`, this log and the test are committed together
*before* the directive is issued, so `git log` shows the order.
