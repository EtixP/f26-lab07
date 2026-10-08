# REFACTOR.md

One section per milestone. Fill each one in as you go, in order.

Milestone 1 is written in two sittings, the pin before the refactor and the
rest after. A pin written afterwards is worth nothing, and a TA will ask.

Keep it short and specific. Point at methods, call sites, and test names.

---

## Milestone 1: Direct a refactor, characterization first

### The pin (write this section before you direct the refactor)

**The pin.** File and test name, plus one sentence naming the method and the
observable result it pins. Not "recurring bookings work". Green against the
shipped code, and you did not edit or delete an existing test method to get
there.

> `src/test/java/edu/cmu/cs214/scheduling/workflow/BookingWorkflowCharacterizationTest.java`,
> `recurringSubmitSkipsAWeekThatOnlyTouchesAnExistingBooking`. When `submit` gets a
> RECURRING request and one week's slot only *touches* an existing booking in the
> room (9-10 against an 8-9), it skips that week. The outcome lists the slot in
> `getSkipped()`, the message reads `series S-1: 2 booked, 1 skipped`, and the
> written occurrences are numbered 2 and 3, not 1 and 2. It is green against the
> shipped code (36 run, 0 failures). The pin is a new class, and no existing
> test method was touched.

**Why that one, and does a shipped test already cover it?** Of everything
`BookingWorkflow` does, why is this the behavior worth a test? If something
shipped comes close, say what your pin adds. If nothing does, say how you
checked.

> `submit` has three copies of the room-overlap loop. REGULAR
> (`BookingWorkflow.java:68-69`) and BLOCKED (`:152-153`) use `< 0`, so touching
> slots are free. RECURRING (`:121-122`) uses `<= 0`, so touching slots clash. That
> also contradicts `TimeSlot`'s "exclusive end". A refactor to polymorphism
> invites sharing that loop, and sharing it silently picks one rule. The closest
> shipped test is `regularSubmitAcceptsASlotThatStartsWhenAnotherEnds`, which
> pins the boundary for REGULAR only. `recurringSubmitBooksEveryWeekOfAnOpenSeries`
> uses an empty room, so no shipped test ever makes a series skip a week. My pin
> adds the RECURRING boundary, the `getSkipped()` contents, and the
> index-follows-week numbering. To check that nothing shipped covers it, I read
> all 18 `BookingWorkflowTest` methods, then changed lines 121-122 to `< 0`. The
> 35 shipped tests stayed green and only the pin failed.

**What a regeneration would do differently here.** Suppose someone
threw this class away and regenerated it from a one-line description of what a
booking workflow does. Name the decision that would be made a second time, and
say which way it would probably go.

> The decision made a second time is whether touching slots conflict. A
> regeneration from "members book one slot or a weekly series" would write one
> overlap check for every type. It would almost certainly be half-open, as
> `TimeSlot` documents. The series would then *book* the touching week rather
> than skip it. The regeneration would probably also number occurrences 1..n by
> booked count, not by week. Whether `<=` is a bug or a deliberate buffer between
> repeating meetings isn't written down anywhere, which is why it is pinned
> rather than "fixed".

### The directive

**The refactor and the exact directive.** Name the refactor (one from the menu
in the handout) and paste the directive you gave the agent, including the scope
you set, meaning which files and packages were in bounds, which were not, and
one line on why the boundary sits where it does.

> **Replace Conditional with Polymorphism**, done by extracting one class per
> booking type. The directive:
>
> ```
> Refactor BookingWorkflow: replace the switch on BookingType in submit, cancel,
> priceOf and describe with polymorphism, one class per booking type behind a
> common interface.
>
> In scope: src/main/java/edu/cmu/cs214/scheduling/workflow/ only. Edit
> BookingWorkflow.java; add package-private classes in the same package.
> Out of scope, must not change: domain/, notify/, pricing/, reporting/, all of
> src/test/, pom.xml, .github/.
>
> Rules:
> 1. BookingWorkflow's public constructor and the four public method signatures
>    stay exactly as they are.
> 2. When done, none of the four methods branches on BookingType. One place that
>    maps a type to its handler is allowed.
> 3. Move code, do not merge or fix it. Keep every comparison operator, message
>    string, notification text, check order and store/hub call order. Do NOT
>    unify the three room-overlap loops: RECURRING uses <= and the others use <,
>    and BookingWorkflowCharacterizationTest pins that.
> 4. No new features, logging, dependencies or test edits.
> Done means: mvn -B test reports 36 run, 0 failures, and git diff --stat
> touches only workflow/.
> ```
>
> The boundary sits at `workflow/` because all four switches live there. The
> alternative of putting behavior on the `BookingType` enum would edit `domain/`,
> and `domain/` is shared with `reporting/` and `pricing/`, which the pin does not
> protect.

### The result

**The diff and the suite.** How you are showing the diff to the TA (a commit,
`git diff`, a branch), and the totals line (the shipped count plus your pin,
all green).

> Branch `lab07-refactor`. The pin is commit `b09b11d`, and the refactor is the
> single commit `2fab2fa` on top of it. To show it: `git show 2fab2fa`, or
> `git diff b09b11d 2fab2fa --stat`. That gives 5 files in `workflow/`, +340/−222:
> `BookingWorkflow` shrinks to the shared lookups plus `handlerFor`, and the new
> files are `BookingTypeHandler`, `RegularBookingHandler`,
> `RecurringBookingHandler` and `BlockedBookingHandler`. Totals line after the
> refactor: `Tests run: 36, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`
> (35 shipped + 1 pin).

**What did NOT change: behavior and files.** The observable behavior you
checked is still the same, including anything that surprised you while reading.
Which files outside the scope are untouched, and how you verified that rather
than assumed it. If the agent reached outside the directive, say where and what
you did about it.

> *Behavior.* Beyond the suite, I compiled the pre-refactor `main` sources
> (`b09b11d`) and the post-refactor ones separately against the same throwaway
> driver. The driver ran 22 submits covering every rejection message, 8 cancels,
> and `describe`/`priceOf` on every booking before and after the cancels, then
> dumped the full outbox and store. Both outputs are 218 lines and `diff` finds
> no difference. The surprises it confirmed are all still there:
> - A RECURRING series never checks the member's other bookings. Ada got a
>   series in C-200 at the same hour she holds W-101.
> - A fully skipped series returns `accepted=false` but still uses up a series
>   id (`S-4`).
> - Cancelling occurrence 3 releases occurrences 3 and 4 but keeps 2.
> - `priceOf` on any occurrence prices the whole live series.
>
> *Files.* `git diff --stat b09b11d 2fab2fa -- src/test pom.xml .github` and the
> same command over `domain notify pricing reporting` both print nothing.
> `git status` showed only `workflow/` changes before the commit. The agent did
> not reach outside the directive.

**One thing the agent changed that you had to look at twice.** Something you
checked line by line before accepting. If there was nothing, say how carefully
you read the diff.

> The four `default:` branches are gone. Before, they returned
> `rejected("unsupported booking type")`, `false`, `0.0` and `"Booking #id in
> room"`. Now `handlerFor` is an exhaustive `switch` expression with no
> `default`. I checked that this is unobservable today: `BookingType` has
> exactly three values, `Booking` and `BookingRequest` reject a null type, and a
> `switch` statement on null already threw an NPE. The trade-off is that a
> fourth type now fails to *compile* instead of being silently rejected or priced
> at $0. I accepted that. I also confirmed mechanically that every non-signature
> line of the three handlers appears verbatim in the old file. The only body
> change is that `recipientFor(...)` is now qualified as
> `BookingTypeHandler.recipientFor(...)`. I also grepped for the RECURRING
> `<= 0` (`RecurringBookingHandler.java:59-60`).

### The closing explanation

**Refactor or regenerate?** Argue whether regenerating `BookingWorkflow` from scratch
would have been the better call, using the lecture's four questions (test
coverage, code age, spec quality, and reach). Be concrete about this codebase.

> Refactoring was the better call. Three of the four questions point that way.
> - **Test coverage: thin where it matters.** The 18 workflow tests exercise one
>   happy path per type. Changing the RECURRING overlap operator left all 35
>   green. At least eight behaviors are unpinned (`MILESTONE.md`, step 1.1),
>   including the cancel cascade, whole-series pricing and the missing
>   member-conflict check for series. A regeneration could change any of them
>   with the suite still green.
> - **Code age: young.** It is one agent-generated commit (`cedea51`) with no
>   bug-fix history, so there are no hard-won patches to lose. This is the one
>   question that leans toward regenerating.
> - **Spec quality: weak.** The spec is one README paragraph plus Javadoc. It
>   says nothing about touching slots, cancel cascades or series pricing, and
>   `TimeSlot`'s "exclusive end" contradicts what RECURRING does. A regeneration
>   would have to re-decide all of these, and nothing says which answer is right.
> - **Reach: high.** The class Javadoc says every store write and every
>   notification goes through it. `ReportService` reads the store state it
>   leaves behind (cancelled flags, series occurrences), and the outbox text is
>   what members actually receive.
>
> A refactor moved code verbatim and could be checked line by line and by an
> output diff. A regeneration could only be checked against tests that already
> miss the risky parts.

**What would flip your answer.** A condition about the artifact, not a feeling.

> I would regenerate if the booking rules were written down (overlap boundary
> per type, cancel scope, what `priceOf` charges for a series) *and* the
> workflow tests killed every mutant of those rules. One concrete check would be
> the `<=`→`<` change on lines 121-122, plus the matching changes to the cancel
> and price loops, each failing at least one test. At that point a regeneration
> could be verified rather than trusted, and the young code would no longer be
> worth preserving.

---

## Milestone 2: The pattern critique

Read `notify/`. It works and the outbox tests pass.

### The patterns present

List every design pattern you can name in that package. For each one, the class
or classes that carry it.

> | Pattern | Carried by |
> |---|---|
> | **Strategy** | `NotificationStrategy` (interface), `EmailNotificationStrategy` (only implementation), held in `NotificationHub.strategy` |
> | **Factory** (simple factory) | `NotifierFactory.createStrategy()` |
> | **Singleton** | `NotifierFactory`: private constructor, static `instance`, `synchronized getInstance()` |
> | **Observer** (publish/subscribe) | `NotificationHub` (subject: `subscribe`, `publish`), `NotificationSubscriber` (observer interface), `OutboxSubscriber` (concrete observer) |
> | **Adapter** | `OutboxSubscriber` adapts `Outbox.append(String)` to `NotificationSubscriber.onNotification(String)` |

### The problem each one solves

For each pattern you listed, what would have to be true about the requirements
for that pattern to be the right call? One sentence each, not in terms of
"flexibility".

> - **Strategy:** the same message has to be rendered in more than one format,
>   and which format is decided per call or per deployment. An example is email
>   for some recipients and SMS for others.
> - **Factory:** which concrete renderer to build depends on information the
>   caller should not have to know, such as configuration or the recipient's
>   channel.
> - **Singleton:** exactly one instance may exist because it owns a shared
>   resource or state, such as one connection pool or one rate limiter.
> - **Observer:** a number of independent receivers that varies or isn't known in
>   advance must each react to every published message, and they are added
>   without changing the publisher.
> - **Adapter:** an existing class whose interface you can't change has to be
>   plugged into code that expects a different interface.

### Which of those problems exist here

For each pattern, does the problem it solves exist in this codebase? Point at
the code that settles it.

> - **Strategy: no.** There is one implementation, and the hub can't even be
>   given another one. `NotificationHub.java:22` hard-codes
>   `NotifierFactory.getInstance().createStrategy()`, and no constructor or
>   setter takes a strategy. Every message ever rendered has the one shape
>   `"To: … | Subject: … | body"`.
> - **Factory: no.** `createStrategy()` (`NotifierFactory.java:19-21`) takes no
>   arguments, has no branch, and always returns `new
>   EmailNotificationStrategy()`. Its only caller is `NotificationHub.java:22`.
> - **Singleton: no.** `NotifierFactory` has no fields beyond `instance`, so
>   there is nothing to share and nothing that would break with two copies. The
>   only thing relying on it is the test `factoryHandsBackTheSameInstance`, which
>   tests the pattern, not a requirement.
> - **Observer: no.** `subscribe(` is called exactly once in the codebase,
>   inside the hub's own constructor (`NotificationHub.java:23`). The test
>   `hubDeliversToItsOneSubscriber` asserts there is exactly one. `BookingWorkflow`
>   and the tests read delivery back through `hub.getOutbox()`, which bypasses
>   the subscriber list altogether.
> - **Adapter: no.** `Outbox` is our own class (`Outbox.java`) and could take the
>   text directly. `OutboxSubscriber` exists only to fit the Observer interface,
>   which is itself unneeded.

### The simpler structure

**Your proposal.** What replaces `notify/`. Sketch the classes and the one
method that matters.

> There are three classes instead of eight. `NotificationMessage` and `Outbox`
> stay as they are, and `NotificationHub` keeps its name and public constructors
> so `BookingWorkflow` and the tests don't change:
>
> ```java
> public class NotificationHub {
>     private final Outbox outbox;
>     public NotificationHub() { this(new Outbox()); }
>     public NotificationHub(Outbox outbox) { /* reject null */ this.outbox = outbox; }
>
>     public void publish(NotificationMessage m) {
>         outbox.append("To: " + m.recipient() + " | Subject: " + m.subject()
>                 + " | " + m.body());
>     }
>     public Outbox getOutbox() { return outbox; }
> }
> ```
>
> This deletes `NotificationStrategy`, `EmailNotificationStrategy`,
> `NotifierFactory`, `NotificationSubscriber` and `OutboxSubscriber`.

**What stays the same.** The tested behavior it must still produce, named
precisely enough that a reader can check it against the shipped tests.

> - Each `publish` appends exactly one string to the outbox, in call order. The
>   outbox counts in `BookingWorkflowTest`, e.g. `regularCancelReleasesTheSlotAndNotifies`
>   expects 2, and the pin expects 3.
> - The string is exactly `To: <recipient> | Subject: <subject> | <body>`
>   (`publishedMessageLandsInTheOutboxFullyRendered`,
>   `aConfirmationFromTheWorkflowReachesTheOutbox`).
> - `new NotificationHub()` creates its own empty `Outbox`, and `getOutbox()`
>   returns it.
> - `NotificationMessage` still rejects a blank recipient and a null subject.
>
> Two shipped tests, `hubDeliversToItsOneSubscriber` and
> `factoryHandsBackTheSameInstance`, assert the structure rather than any
> behavior, so the proposal can't keep them green. That is why it stays a
> proposal: the lab forbids deleting shipped test methods.

**What you would keep, if anything.** If you would keep one interface, say
which and why. "None of it" is a fine answer if you can defend it.

> None of the interfaces. `Outbox` is already the seam that tests and callers
> observe delivery through, and `NotificationMessage` is a value type, not a
> layer. An interface with one implementation that nothing can swap in
> (`NotificationStrategy`) is just one more file to read.

### What would bring each layer back

For at least two of the layers you would remove, what requirement, if it
arrived next sprint, would make that layer the right structure? Be specific
about the requirement, not about the pattern.

> - **Strategy:** members can choose SMS instead of email. A 160-character text
>   ("Cedar Hall booked Mon 10/5 9:00") must go to those members, and the mail
>   format to the rest. That means two renderings of the same
>   `NotificationMessage`, chosen per recipient at publish time.
> - **Observer:** facilities wants every "Room blocked" and "Block released"
>   message filed in their ticketing system, and compliance wants every outbound
>   message written to an audit log. That gives three independent receivers of
>   the same publish, which ops turns on and off per deployment without
>   `BookingWorkflow` knowing.
> - **Factory:** the format is chosen at startup from configuration (for example
>   `notify.channel=sms`), so the code that builds the hub must not name a
>   concrete renderer class.
> - **Singleton:** none. Even if one shared resource such as an SMTP connection
>   pool arrived, I would build one instance and pass it in rather than reach a
>   global.

**Misuse or anti-pattern?** Say which this is and why the distinction matters.

> Mostly **misuse**: Strategy, Factory, Observer and Adapter are sound patterns
> applied to problems this codebase doesn't have (speculative generality). The
> fix is to delete them now and bring them back when the requirement arrives.
> The **Singleton** used as a hidden global lookup is the one part that is an
> **anti-pattern**. Calling `NotifierFactory.getInstance()` inside the hub's
> constructor hides the dependency and blocks injection. That is exactly why the
> Strategy can't be swapped even in a test, and it would be the wrong tool even
> if the requirement arrived. The distinction matters because the remedies
> differ: for misuse, wait for the requirement; for an anti-pattern, use a
> different design (pass the dependency in) whatever the requirements are.

---

## Milestone 3: The missing pattern

Read `pricing/`. Not coded, one sentence.

**The pattern.** Which one fits `PriceCalculator`, and the problem that makes
it fit. Name the problem.

> **Decorator.** Equivalently, an ordered chain of `PricingRule` objects, each
> wrapping the running price. It fits because pricing is a *published sequence
> of independent, order-sensitive adjustments*: base rate, then weekend
> surcharge, then long-booking discount, then tier discount
> (`PriceCalculator.java:12-14`, `:30-44`). Each adjustment takes the price so
> far and returns a new one, and today adding, removing or reordering a rule
> means editing the middle of `price()` and re-checking that the order still
> holds (`everyRuleAppliesInOrder`).

**Would you apply it today?** Yes or no, one line, with the reason.

> No. There are four fixed rules in one 24-line method that reads exactly like
> the published rule list, all six tests pin it, and no requirement asks for a
> fifth rule or for configurable rules yet. That is the same lesson as `notify/`.
