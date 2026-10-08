package edu.cmu.cs214.scheduling.workflow;

import edu.cmu.cs214.scheduling.domain.Booking;
import edu.cmu.cs214.scheduling.domain.BookingOutcome;
import edu.cmu.cs214.scheduling.domain.BookingRequest;
import edu.cmu.cs214.scheduling.domain.Member;
import edu.cmu.cs214.scheduling.domain.Room;

/**
 * What one booking type does at each step of the workflow. {@link BookingWorkflow}
 * does the lookups every type shares, then hands off to the handler for the
 * booking's type.
 */
interface BookingTypeHandler {

    String FACILITIES_CONTACT = "facilities@rooms.example.edu";

    /** Validates and writes a request whose room is already known to exist. */
    BookingOutcome submit(BookingRequest request, Room room);

    /** Releases a live booking. */
    boolean cancel(Booking booking, String roomName, boolean adminOverride);

    /** What the holder owes, in dollars. */
    double price(Booking booking);

    /** A one-line summary for schedules and confirmation screens. */
    String describe(Booking booking, String roomName);

    static String recipientFor(Member member) {
        return member == null ? FACILITIES_CONTACT : member.getEmail();
    }
}
