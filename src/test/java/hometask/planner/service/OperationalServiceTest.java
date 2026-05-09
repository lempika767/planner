package hometask.planner.service;

import hometask.planner.entity.Meeting;
import hometask.planner.entity.Person;
import hometask.planner.entity.SlotType;
import hometask.planner.entity.TimeSlot;
import hometask.planner.repository.MeetingRepository;
import hometask.planner.repository.PeopleRepository;
import hometask.planner.repository.TimeSlotsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationalServiceTest {

    @Mock private MeetingRepository meetingRepository;
    @Mock private PeopleRepository peopleRepository;
    @Mock private TimeSlotsRepository timeSlotsRepository;

    @InjectMocks
    private OperationalService operationalService;

    private static final LocalDateTime START = LocalDateTime.of(2025, 6, 1, 10, 0);
    private static final LocalDateTime END   = LocalDateTime.of(2025, 6, 1, 11, 0);

    private Person alice;
    private Person bob;
    private UUID meetingId;

    @BeforeEach
    void setUp() {
        alice     = new Person(UUID.randomUUID(), "Alice");
        bob       = new Person(UUID.randomUUID(), "Bob");
        meetingId = UUID.randomUUID();
    }

    // -------------------------------------------------------------------------
    // createMeeting — happy path
    // -------------------------------------------------------------------------

    @Test
    void createMeeting_success() {
        var slots = availableSlots(alice.personId(), START, END);

        when(peopleRepository.getPeople(List.of("Alice"))).thenReturn(Flux.just(alice));
        when(timeSlotsRepository.calculateBricksInPeriod(START, END)).thenReturn((long) slots.size());
        when(timeSlotsRepository.getPersonAvailability(alice.personId())).thenReturn(Flux.fromIterable(slots));
        when(meetingRepository.addMeeting(START, END, "Standup", List.of(alice)))
                .thenReturn(new Meeting(meetingId, START, END, "Standup", List.of(alice)));
        when(timeSlotsRepository.reservePerson(alice.personId(), meetingId, START, END))
                .thenReturn(Mono.just(true));

        StepVerifier.create(operationalService.createMeeting("Standup", List.of("Alice"), START, END))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void createMeeting_multiplePeople_success() {
        var aliceSlots = availableSlots(alice.personId(), START, END);
        var bobSlots   = availableSlots(bob.personId(), START, END);
        long bricks    = aliceSlots.size();

        when(peopleRepository.getPeople(List.of("Alice", "Bob"))).thenReturn(Flux.just(alice, bob));
        when(timeSlotsRepository.calculateBricksInPeriod(START, END)).thenReturn(bricks);
        when(timeSlotsRepository.getPersonAvailability(alice.personId())).thenReturn(Flux.fromIterable(aliceSlots));
        when(timeSlotsRepository.getPersonAvailability(bob.personId())).thenReturn(Flux.fromIterable(bobSlots));
        when(meetingRepository.addMeeting(START, END, "Sync", List.of(alice, bob)))  // "Sync" + both people
                .thenReturn(new Meeting(meetingId, START, END, "Sync", List.of(alice, bob)));
        when(timeSlotsRepository.reservePerson(alice.personId(), meetingId, START, END)).thenReturn(Mono.just(true));
        when(timeSlotsRepository.reservePerson(bob.personId(),   meetingId, START, END)).thenReturn(Mono.just(true));

        StepVerifier.create(operationalService.createMeeting("Sync", List.of("Alice", "Bob"), START, END))
                .expectNext(true)
                .verifyComplete();
    }

    // -------------------------------------------------------------------------
    // createMeeting — person not found
    // -------------------------------------------------------------------------

    @Test
    void createMeeting_personNotFound_emitsError() {
        // repository returns fewer people than requested
        when(peopleRepository.getPeople(List.of("Alice", "Ghost"))).thenReturn(Flux.just(alice));

        StepVerifier.create(operationalService.createMeeting("Standup", List.of("Alice", "Ghost"), START, END))
                .expectError(NoSuchElementException.class)
                .verify();

        verify(meetingRepository, never()).addMeeting(any(), any(), any(), anyList());
    }

    // -------------------------------------------------------------------------
    // createMeeting — person not available
    // -------------------------------------------------------------------------

    @Test
    void createMeeting_personNotAvailable_emitsError() {
        // repo returns 0 available slots but bricks > 0
        when(peopleRepository.getPeople(List.of("Alice"))).thenReturn(Flux.just(alice));
        when(timeSlotsRepository.calculateBricksInPeriod(START, END)).thenReturn(2L);
        when(timeSlotsRepository.getPersonAvailability(alice.personId())).thenReturn(Flux.empty());

        StepVerifier.create(operationalService.createMeeting("Standup", List.of("Alice"), START, END))
                .expectError(PersonNotAvailableException.class)
                .verify();

        verify(meetingRepository, never()).addMeeting(any(), any(), any(), anyList());
    }

    // -------------------------------------------------------------------------
    // checkPeopleAvailabilityInPeriod
    // -------------------------------------------------------------------------

    @Test
    void checkAvailability_allAvailable_completesEmpty() {
        var slots = availableSlots(alice.personId(), START, END);
        when(timeSlotsRepository.calculateBricksInPeriod(START, END)).thenReturn((long) slots.size());
        when(timeSlotsRepository.getPersonAvailability(alice.personId())).thenReturn(Flux.fromIterable(slots));

        StepVerifier.create(operationalService.checkPeopleAvailabilityInPeriod(List.of(alice), START, END, "Meeting"))
                .verifyComplete();
    }

    @Test
    void checkAvailability_partialSlots_emitsError() {
        var oneSlot = List.of(new TimeSlot(START, SlotType.AVAILABLE, alice.personId(), null));
        when(timeSlotsRepository.calculateBricksInPeriod(START, END)).thenReturn(2L);
        when(timeSlotsRepository.getPersonAvailability(alice.personId())).thenReturn(Flux.fromIterable(oneSlot));

        StepVerifier.create(operationalService.checkPeopleAvailabilityInPeriod(List.of(alice), START, END, "Meeting"))
                .expectError(PersonNotAvailableException.class)
                .verify();
    }

    @Test
    void checkAvailability_busySlotsNotCounted() {
        // repo already filters AVAILABLE — so if all slots are BUSY, it returns empty
        when(timeSlotsRepository.calculateBricksInPeriod(START, END)).thenReturn(1L);
        when(timeSlotsRepository.getPersonAvailability(alice.personId())).thenReturn(Flux.empty());

        StepVerifier.create(operationalService.checkPeopleAvailabilityInPeriod(List.of(alice), START, END, "Meeting"))
                .expectError(PersonNotAvailableException.class)
                .verify();
    }

    @Test
    void checkAvailability_slotsOutsideRange_notCounted() {
        var outsideSlot = new TimeSlot(END.plusHours(1), SlotType.AVAILABLE, alice.personId(), null);
        when(timeSlotsRepository.calculateBricksInPeriod(START, END)).thenReturn(1L);
        when(timeSlotsRepository.getPersonAvailability(alice.personId())).thenReturn(Flux.just(outsideSlot));

        StepVerifier.create(operationalService.checkPeopleAvailabilityInPeriod(List.of(alice), START, END, "Meeting"))
                .expectError(PersonNotAvailableException.class)
                .verify();
    }

    // -------------------------------------------------------------------------
    // addTimeSlots / removeTimeSlots — delegate + lock
    // -------------------------------------------------------------------------

    @Test
    void addTimeSlots_returnsSlots() {
        var slots = availableSlots(alice.personId(), START, END);
        when(timeSlotsRepository.addTimeSlots(alice.personId(), START, END)).thenReturn(Flux.fromIterable(slots));

        StepVerifier.create(operationalService.addTimeSlots(alice.personId(), START, END))
                .expectNextSequence(slots)
                .verifyComplete();
    }

    @Test
    void removeTimeSlots_returnsRemainingSlots() {
        var remaining = availableSlots(alice.personId(), END, END.plusHours(1));
        when(timeSlotsRepository.removeTimeSlots(alice.personId(), START, END)).thenReturn(Flux.fromIterable(remaining));

        StepVerifier.create(operationalService.removeTimeSlots(alice.personId(), START, END))
                .expectNextSequence(remaining)
                .verifyComplete();
    }

    @Test
    void removeTimeSlots_noRemaining_returnsEmpty() {
        when(timeSlotsRepository.removeTimeSlots(alice.personId(), START, END)).thenReturn(Flux.empty());

        StepVerifier.create(operationalService.removeTimeSlots(alice.personId(), START, END))
                .verifyComplete();
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    /** Generates one AVAILABLE slot per hour-brick between start and end. */
    private List<TimeSlot> availableSlots(UUID personId, LocalDateTime from, LocalDateTime to) {
        var slots = new java.util.ArrayList<TimeSlot>();
        var cursor = from;
        while (!cursor.isAfter(to)) {
            slots.add(new TimeSlot(cursor, SlotType.AVAILABLE, personId, null));
            cursor = cursor.plusMinutes(30);
        }
        return slots;
    }
}