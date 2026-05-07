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

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.Lock;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationalServiceTest {

    @Mock
    private MeetingRepository meetingRepository;
    @Mock
    private PeopleRepository peopleRepository;
    @Mock
    private TimeSlotsRepository timeSlotsRepository;

    @InjectMocks
    private OperationalService operationalService;

    private static final UUID PERSON_ID_1 = UUID.randomUUID();
    private static final UUID PERSON_ID_2 = UUID.randomUUID();
    private static final LocalDateTime START = LocalDateTime.of(2026, 5, 7, 10, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 5, 7, 11, 0);

    private Person person1;
    private Person person2;

    @BeforeEach
    void setUp() {
        person1 = new Person(PERSON_ID_1, "Alice");
        person2 = new Person(PERSON_ID_2, "Bob");
    }

    // --- createMeeting ---

    @Test
    void createMeeting_shouldReturnFalse_whenPersonNotFound() {
        when(peopleRepository.getPeople(List.of("Alice", "Bob"))).thenReturn(List.of(person1));

        assertThrows(NoSuchElementException.class, () ->
                operationalService.createMeeting("Meeting", List.of("Alice", "Bob"), START, END));
    }

    @Test
    void createMeeting_shouldReturnTrue_whenAllPeopleAvailable() throws Exception {
        when(peopleRepository.getPeople(List.of("Alice"))).thenReturn(List.of(person1));
        when(timeSlotsRepository.calculateBricksInPeriod(START, END)).thenReturn(4L);
        when(timeSlotsRepository.getPersonAvailability(PERSON_ID_1)).thenReturn(
                Set.of(
                        new TimeSlot(START, SlotType.AVAILABLE, PERSON_ID_1, null),
                        new TimeSlot(START.plusMinutes(15), SlotType.AVAILABLE, PERSON_ID_1, null),
                        new TimeSlot(START.plusMinutes(30), SlotType.AVAILABLE, PERSON_ID_1, null),
                        new TimeSlot(START.plusMinutes(45), SlotType.AVAILABLE, PERSON_ID_1, null)
                )
        );
        when(meetingRepository.addMeeting(any(), any(), any(), any())).thenReturn(
                new Meeting(UUID.randomUUID(), START, END, "MyTestMeeting", List.of(person1))
        );

        boolean result = operationalService.createMeeting("Meeting", List.of("Alice"), START, END);

        assertTrue(result);
        verify(timeSlotsRepository).removeTimeSlots(PERSON_ID_1, START, END);
    }

    @Test
    void createMeeting_shouldThrowPersonNotAvailableException_whenPersonBusy() {
        when(peopleRepository.getPeople(List.of("Alice"))).thenReturn(List.of(person1));
        when(timeSlotsRepository.calculateBricksInPeriod(START, END)).thenReturn(4L);
        when(timeSlotsRepository.getPersonAvailability(PERSON_ID_1)).thenReturn(Set.of(
                new TimeSlot(START, SlotType.AVAILABLE, PERSON_ID_1, null) // only 1 of 4 slots
        ));

        assertThrows(PersonNotAvailableException.class, () ->
                operationalService.createMeeting("Meeting", List.of("Alice"), START, END));

        verify(meetingRepository, never()).addMeeting(any(), any(), any(), any());
        verify(timeSlotsRepository, never()).removeTimeSlots(any(), any(), any());
    }


    // --- checkPeopleAvailabilityInPeriod ---

    @Test
    void checkPeopleAvailability_shouldPass_whenAllSlotsAvailable() {
        when(timeSlotsRepository.calculateBricksInPeriod(START, END)).thenReturn(2L);
        when(timeSlotsRepository.getPersonAvailability(PERSON_ID_1)).thenReturn(Set.of(
                new TimeSlot(START, SlotType.AVAILABLE, PERSON_ID_1, null),
                new TimeSlot(START.plusMinutes(15), SlotType.AVAILABLE, PERSON_ID_1, null)
        ));

        assertDoesNotThrow(() ->
                operationalService.checkPeopleAvailabilityInPeriod(List.of(person1), START, END, "Meeting"));
    }

    @Test
    void checkPeopleAvailability_shouldThrow_whenNotEnoughSlots() {
        when(timeSlotsRepository.calculateBricksInPeriod(START, END)).thenReturn(2L);
        when(timeSlotsRepository.getPersonAvailability(PERSON_ID_1)).thenReturn(Set.of(
                new TimeSlot(START, SlotType.AVAILABLE, PERSON_ID_1, null)
        ));

        assertThrows(PersonNotAvailableException.class, () ->
                operationalService.checkPeopleAvailabilityInPeriod(List.of(person1), START, END, "Meeting"));
    }

    @Test
    void checkPeopleAvailability_shouldThrow_whenSlotOutsidePeriod() {
        when(timeSlotsRepository.calculateBricksInPeriod(START, END)).thenReturn(2L);
        when(timeSlotsRepository.getPersonAvailability(PERSON_ID_1)).thenReturn(Set.of(
                new TimeSlot(START, SlotType.AVAILABLE, PERSON_ID_1, null),
                new TimeSlot(END.plusMinutes(15), SlotType.AVAILABLE, PERSON_ID_1, null) // outside range
        ));

        assertThrows(PersonNotAvailableException.class, () ->
                operationalService.checkPeopleAvailabilityInPeriod(List.of(person1), START, END, "Meeting"));
    }

    // --- acquireLocks ---

    @Test
    void acquireLocks_shouldAcquireLocksForAllPeople() throws InterruptedException {
        List<Lock> locks = operationalService.acquireLocks(List.of(person1, person2));

        assertEquals(2, locks.size());
        locks.forEach(Lock::unlock); // cleanup
    }

    @Test
    void acquireLocks_shouldReturnEmptyList_whenNoPeople() throws InterruptedException {
        List<Lock> locks = operationalService.acquireLocks(List.of());
        assertTrue(locks.isEmpty());
    }

    // --- addTimeSlots ---

    @Test
    void addTimeSlots_shouldReturnTimeSlots_whenLockAcquired() {
        Set<TimeSlot> expected = Set.of(
                new TimeSlot(START, SlotType.AVAILABLE, PERSON_ID_1, null),
                new TimeSlot(START.plusMinutes(15), SlotType.AVAILABLE, PERSON_ID_1, null)
        );
        when(timeSlotsRepository.addTimeSlots(PERSON_ID_1, START, END)).thenReturn(expected);

        Set<TimeSlot> result = operationalService.addTimeSlots(PERSON_ID_1, START, END);

        assertEquals(expected, result);
        verify(timeSlotsRepository).addTimeSlots(PERSON_ID_1, START, END);
    }

    @Test
    void addTimeSlots_shouldCallRepository_withCorrectArguments() {
        when(timeSlotsRepository.addTimeSlots(PERSON_ID_1, START, END)).thenReturn(Set.of());

        operationalService.addTimeSlots(PERSON_ID_1, START, END);

        verify(timeSlotsRepository, times(1)).addTimeSlots(PERSON_ID_1, START, END);
    }

// --- removeTimeSlots ---

    @Test
    void removeTimeSlots_shouldReturnRemainingSlots_whenLockAcquired() {
        Set<TimeSlot> remaining = Set.of(
                new TimeSlot(START.plusMinutes(30), SlotType.AVAILABLE, PERSON_ID_1, null)
        );
        when(timeSlotsRepository.removeTimeSlots(PERSON_ID_1, START, END)).thenReturn(remaining);

        Set<TimeSlot> result = operationalService.removeTimeSlots(PERSON_ID_1, START, END);

        assertEquals(remaining, result);
        verify(timeSlotsRepository).removeTimeSlots(PERSON_ID_1, START, END);
    }

    @Test
    void removeTimeSlots_shouldReturnNull_whenAllSlotsRemoved() {
        when(timeSlotsRepository.removeTimeSlots(PERSON_ID_1, START, END)).thenReturn(null);

        Set<TimeSlot> result = operationalService.removeTimeSlots(PERSON_ID_1, START, END);

        assertNull(result);
    }

// --- reservePersonTime ---

    @Test
    void reservePersonTime_shouldReturnTrue_whenReservationSucceeds() {
        UUID meetingId = UUID.randomUUID();
        when(timeSlotsRepository.reservePerson(PERSON_ID_1, meetingId, START, END)).thenReturn(
                Set.of(new TimeSlot(START, SlotType.BUSY, PERSON_ID_1, meetingId))
        );

        boolean result = operationalService.reservePersonTime(PERSON_ID_1, meetingId, START, END);

        assertTrue(result);
        verify(timeSlotsRepository).reservePerson(PERSON_ID_1, meetingId, START, END);
    }

    @Test
    void reservePersonTime_shouldReturnFalse_whenPersonNotFound() {
        UUID meetingId = UUID.randomUUID();
        when(timeSlotsRepository.reservePerson(PERSON_ID_1, meetingId, START, END)).thenReturn(null);

        boolean result = operationalService.reservePersonTime(PERSON_ID_1, meetingId, START, END);

        assertFalse(result);
    }

// --- executeWithWriteLock (via addTimeSlots) - concurrency/error paths ---

    @Test
    void addTimeSlots_shouldReleaseLock_evenWhenRepositoryThrows() {
        when(timeSlotsRepository.addTimeSlots(PERSON_ID_1, START, END))
                .thenThrow(new RuntimeException("db error"))
                .thenReturn(Set.of()); // second call succeeds

        assertThrows(RuntimeException.class, () ->
                operationalService.addTimeSlots(PERSON_ID_1, START, END));

        // lock should be released — subsequent call should not deadlock
        assertDoesNotThrow(() -> operationalService.addTimeSlots(PERSON_ID_1, START, END));
    }

    @Test
    void addTimeSlots_shouldBeCallableSequentially_withoutDeadlock() {
        when(timeSlotsRepository.addTimeSlots(PERSON_ID_1, START, END)).thenReturn(Set.of());

        assertDoesNotThrow(() -> {
            operationalService.addTimeSlots(PERSON_ID_1, START, END);
            operationalService.addTimeSlots(PERSON_ID_1, START, END);
            operationalService.addTimeSlots(PERSON_ID_1, START, END);
        });
    }

// --- createMeeting - edge cases ---

    @Test
    void createMeeting_shouldReturnTrue_whenEmptyPeopleList() throws Exception {
        when(peopleRepository.getPeople(List.of())).thenReturn(List.of());
        when(meetingRepository.addMeeting(any(), any(), any(), any())).thenReturn(
                new Meeting(UUID.randomUUID(), START, END, "Meeting", List.of())
        );

        boolean result = operationalService.createMeeting("Meeting", List.of(), START, END);
        assertTrue(result);
    }

    @Test
    void createMeeting_shouldNotReserveTime_whenAvailabilityCheckFails() {
        when(peopleRepository.getPeople(List.of("Alice", "Bob"))).thenReturn(List.of(person1, person2));
        when(timeSlotsRepository.calculateBricksInPeriod(START, END)).thenReturn(4L);
        when(timeSlotsRepository.getPersonAvailability(PERSON_ID_1)).thenReturn(Set.of(
                new TimeSlot(START, SlotType.AVAILABLE, PERSON_ID_1, null)
        ));

        assertThrows(PersonNotAvailableException.class, () ->
                operationalService.createMeeting("Meeting", List.of("Alice", "Bob"), START, END));

        verify(meetingRepository, never()).addMeeting(any(), any(), any(), any());
        verify(timeSlotsRepository, never()).removeTimeSlots(any(), any(), any());
    }
}