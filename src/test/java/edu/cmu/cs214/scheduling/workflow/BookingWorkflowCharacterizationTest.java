package edu.cmu.cs214.scheduling.workflow;

import edu.cmu.cs214.scheduling.domain.Booking;
import edu.cmu.cs214.scheduling.domain.BookingOutcome;
import edu.cmu.cs214.scheduling.domain.BookingRequest;
import edu.cmu.cs214.scheduling.domain.BookingStore;
import edu.cmu.cs214.scheduling.domain.Member;
import edu.cmu.cs214.scheduling.domain.MembershipTier;
import edu.cmu.cs214.scheduling.domain.Room;
import edu.cmu.cs214.scheduling.domain.TimeSlot;
import edu.cmu.cs214.scheduling.notify.NotificationHub;
import edu.cmu.cs214.scheduling.pricing.PriceCalculator;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins behavior of the shipped BookingWorkflow that no shipped test covers,
 * written before the refactor. It records what the code does, not what it
 * should do.
 */
class BookingWorkflowCharacterizationTest {

    private static final LocalDateTime MON_8AM = LocalDateTime.of(2026, 10, 5, 8, 0);
    private static final LocalDateTime MON_9AM = LocalDateTime.of(2026, 10, 5, 9, 0);
    private static final LocalDateTime MON_10AM = LocalDateTime.of(2026, 10, 5, 10, 0);

    @Test
    void recurringSubmitSkipsAWeekThatOnlyTouchesAnExistingBooking() {
        BookingStore store = new BookingStore();
        store.addRoom(new Room("C-200", "Cedar Hall", 20));
        store.addMember(new Member("m-1", "Ada", "ada@rooms.example.edu", MembershipTier.BASIC));
        store.addMember(new Member("m-2", "Grace", "grace@rooms.example.edu",
                MembershipTier.BASIC));
        NotificationHub hub = new NotificationHub();
        BookingWorkflow workflow = new BookingWorkflow(store, new PriceCalculator(), hub);

        // 8-9 on the first Monday only. A regular 9-10 request would be accepted here.
        workflow.submit(BookingRequest.regular("C-200", "m-2", MON_8AM, MON_9AM, 4));

        BookingOutcome outcome = workflow.submit(
                BookingRequest.recurring("C-200", "m-1", MON_9AM, MON_10AM, 3, 6));

        // Week one starts exactly when the 8-9 booking ends, and the series skips it anyway.
        assertTrue(outcome.isAccepted());
        assertEquals(List.of(new TimeSlot(MON_9AM, MON_10AM)), outcome.getSkipped());
        assertEquals("series S-1: 2 booked, 1 skipped", outcome.getMessage());

        // Occurrence indices follow the week number, so the skipped week leaves a gap.
        List<Booking> booked = outcome.getBooked();
        assertEquals(2, booked.size());
        assertEquals(MON_9AM.plusWeeks(1), booked.get(0).getStart());
        assertEquals(2, booked.get(0).getOccurrenceIndex());
        assertEquals(3, booked.get(1).getOccurrenceIndex());

        // One confirmation for the regular booking, one per written occurrence.
        assertEquals(3, hub.getOutbox().size());
    }
}
