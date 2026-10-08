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
Commit: `b09b11d Pin recurring touching-slot skip before refactoring BookingWorkflow`.

### Step 1.4: The directive

Refactor chosen: **Replace Conditional with Polymorphism**, done by extracting
one class per booking type. The full directive, with in-scope and out-of-scope
paths, is pasted in `REFACTOR.md`. In short:

- In scope: `workflow/` only.
- Out of scope: tests, `domain/`, `notify/`, `pricing/`, `reporting/`, `pom.xml`, CI.
- Rules: move code verbatim; do **not** unify the overlap loops; keep the public
  API; allow one type-to-handler selection point.
- Done means 36/36 green and a diff that touches only `workflow/`.

The agent for both the directive and the refactor was Claude Code (Opus 5.5),
working in this session at the user's request.

### Step 1.5: The refactor

Resulting structure in `src/main/java/edu/cmu/cs214/scheduling/workflow/`:

| File | Role |
|---|---|
| `BookingWorkflow.java` | Public API unchanged. Keeps the shared lookups (null request, unknown room, unknown booking, already cancelled, room-name fallback) and one exhaustive `switch` in `handlerFor(BookingType)` |
| `BookingTypeHandler.java` | Package-private interface: `submit(request, room)`, `cancel(booking, roomName, adminOverride)`, `price(booking)`, `describe(booking, roomName)`; also holds `FACILITIES_CONTACT` and `recipientFor` |
| `RegularBookingHandler.java` | The old `case REGULAR` bodies |
| `RecurringBookingHandler.java` | The old `case RECURRING` bodies, plus `MAX_SERIES_WEEKS`. The `<= 0` overlap is kept at l.59-60 |
| `BlockedBookingHandler.java` | The old `case BLOCKED` bodies |

Commit: `2fab2fa`. `git diff --stat b09b11d 2fab2fa`:

```
 .../scheduling/workflow/BlockedBookingHandler.java |  71 ++++++
 .../scheduling/workflow/BookingTypeHandler.java    |  33 +++
 .../cs214/scheduling/workflow/BookingWorkflow.java | 246 ++-------------------
 .../workflow/RecurringBookingHandler.java          | 121 ++++++++++
 .../scheduling/workflow/RegularBookingHandler.java |  91 ++++++++
 5 files changed, 340 insertions(+), 222 deletions(-)
```

### Step 1.6: Verification

1. **Suite:** `Tests run: 36, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.
2. **Scope:** `git diff --stat b09b11d 2fab2fa -- src/test pom.xml .github` and
   the same command over `domain notify pricing reporting` both print nothing.
3. **Type conditionals gone:** `grep -n switch workflow/*.java` finds only
   `BookingWorkflow.handlerFor`.
4. **Verbatim move:** I stripped the indentation from every line of the three
   handlers and listed the ones not found in the old `BookingWorkflow.java`. All
   of them are class, constructor or method signatures, `@Override`, or Javadoc.
   The one exception is that `recipientFor(member)` became
   `BookingTypeHandler.recipientFor(member)`.
5. **Old/new output diff:**
   - I compiled the `main` sources from `b09b11d` and from `2fab2fa` separately.
     A throwaway driver (`scratchpad/diffcheck/Driver.java`, not committed) drove
     each one through the public API:
     - 22 submits, covering every rejection message, the touching-slot skip, a
       26-week series, a fully skipped series, a member double-booked by a
       series, multi-day and touching blocks, and a weekend booking;
     - a booking whose room and member are unknown, added directly to the store;
     - 8 cancels: the middle of a series, cancelled twice, blocked without and
       with admin, regular with admin, the unknown-member booking, and a
       missing id;
     - `describe` and `priceOf` on every booking, before and after the cancels;
     - a dump of the full outbox and store, plus the exception paths.
   - Result: both outputs are **218 lines and identical** under `diff`.
6. **The one thing looked at twice:** the four `default:` branches were
   removed. They are unreachable: `BookingType` has 3 values,
   `Booking`/`BookingRequest` reject a null type, and switching on null already
   threw an NPE. The exhaustive `switch` expression now turns a future 4th type
   into a compile error instead of a silent reject or $0.

Surprises confirmed by the driver (the behavior is the same before and after):
- RECURRING skips touching weeks.
- RECURRING doesn't check the member's other bookings: Ada got series S-3 in
  C-200 while holding W-101 at the same time.
- A fully skipped series reports `rejected: series S-4: 0 booked, 1 skipped`
  but still uses up `S-4`.
- Cancelling occurrence 3 of S-1 cancels 3 and 4 but not 2.
- `priceOf` on any occurrence returns the live series total.

### Step 1.7: Refactor or regenerate?

Written in `REFACTOR.md`. Verdict: **refactor**. Test coverage is thin at the
edges (the mutation survived 35 tests), the spec is weak (nothing written about
the boundaries; `TimeSlot`'s Javadoc contradicts RECURRING), and reach is high
(every write and notification). Code age (young, one commit) is the only
question that leans toward regenerating. My answer would flip if the rules were
written down and the tests killed every mutant of them.

---

## Milestone 2: The pattern critique of `notify/`

### Step 2.1: Evidence gathered (grep over `src/`)

| Search | Hits outside its own declaration | Conclusion |
|---|---|---|
| `subscribe(` | only `NotificationHub.java:23` (the hub's own constructor) | Observer has exactly one subscriber, and no external caller ever subscribes |
| `NotifierFactory` | `NotificationHub.java:22`, plus test `factoryHandsBackTheSameInstance` | Factory and Singleton have one caller; the only test checks the pattern itself |
| `NotificationStrategy` implementors | `EmailNotificationStrategy` only | One strategy. There's no setter or constructor parameter, so it can't be swapped |
| `subscriberCount` | test `hubDeliversToItsOneSubscriber` only | Asserts structure, not behavior |
| `new NotificationHub(` | 6 call sites (3 in `NotificationHubTest`, 1 each in `BookingWorkflowTest`, `ReportServiceTest`, the pin), all no-arg | Even `NotificationHub(Outbox)` is unused outside the class |

### Step 2.2: Write-up

In `REFACTOR.md`. The patterns are Strategy, Factory, Singleton, Observer and
Adapter, and none of their problems exists here. The proposal is a 3-class
`notify/`: `NotificationHub.publish` formats and appends directly, and
`Outbox` and `NotificationMessage` stay. The layers that would come back: Strategy
for per-recipient SMS vs. email, Observer for ticketing plus an audit log,
Factory for config-chosen format. The verdict is misuse (speculative
generality), except the global Singleton lookup, which is an anti-pattern
because it blocks injection.

Caveat to raise at recitation: two shipped tests (`hubDeliversToItsOneSubscriber`,
`factoryHandsBackTheSameInstance`) pin structure, so the proposal can't keep
them green. It is not applied, because the lab forbids deleting shipped tests.

---

## Milestone 3: The missing pattern in `pricing/`

- `PriceCalculator.price` applies four order-sensitive adjustments in sequence:
  base (l.30), weekend surcharge (l.32-35), long-booking discount (l.37-39),
  tier discount (l.41-44).
- Pattern: **Decorator**, or an ordered chain of `PricingRule`s. The problem
  that makes it fit is a published, ordered sequence of independent price
  adjustments that policy will add to and reorder.
- Apply today? **No.** There are four fixed rules in one readable 24-line
  method with six tests, and no requirement yet for a fifth or configurable rule.

---

## Wrap-up

- README: added the tools/models line.
- Final suite: see the last commit. 36/36 green.
- Not committed: `lab07.md` (the handout copy, left untracked) and the
  scratchpad driver.
