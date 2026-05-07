package hometask.planner.repository;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TimeSlotsRepositoryTest {
    private final TimeSlotsRepository timeSlotsRepository = new TimeSlotsRepository(15);

    @Test
    void makeBreaksInPeriod_1Hour(){
        var personId = UUID.randomUUID();
        var start = LocalDateTime.of(2026,1,1,10,0);
        var end = LocalDateTime.of(2026,1,1,11,0);
        var result = timeSlotsRepository.makeBreaksInPeriod(personId, start, end);
        assertNotNull(result);
        assertEquals(4, result.size());
    }

    @Test
    void makeBreaksInPeriod_1HourStartFrom5Minute(){
        var personId = UUID.randomUUID();
        var start = LocalDateTime.of(2026,1,1,10,5);
        var end = LocalDateTime.of(2026,1,1,11,0);
        var result = timeSlotsRepository.makeBreaksInPeriod(personId, start, end);
        assertNotNull(result);
        assertEquals(3, result.size());
    }

    @Test
    void addTimeslots_newPerson(){
        var personId = UUID.randomUUID();
        var start = LocalDateTime.of(2026,1,1,10,0);
        var end = LocalDateTime.of(2026,1,1,11,0);
        var result = timeSlotsRepository.addTimeSlots(personId,start,end);
        assertNotNull(result);
        assertEquals(4, result.size());
    }

    @Test
    void addTimeslots_addSameSlots(){
        var personId = UUID.randomUUID();
        var start = LocalDateTime.of(2026,1,1,10,0);
        var end = LocalDateTime.of(2026,1,1,11,0);
        timeSlotsRepository.addTimeSlots(personId,start,end);
        // add same second time
        var result = timeSlotsRepository.addTimeSlots(personId,start,end);
        assertNotNull(result);
        assertEquals(4, result.size());
    }

    @Test
    void addTimeslots_addDifferentSlots(){
        var personId = UUID.randomUUID();
        var start = LocalDateTime.of(2026,1,1,10,0);
        var end = LocalDateTime.of(2026,1,1,11,0);
        timeSlotsRepository.addTimeSlots(personId,start,end);
        // add same second time
        var start1 = LocalDateTime.of(2026,1,2,10,0);
        var end1 = LocalDateTime.of(2026,1,2,11,0);
        var result = timeSlotsRepository.addTimeSlots(personId,start1,end1);
        assertNotNull(result);
        assertEquals(8, result.size());
    }

    @Test
    void removeTimeSlots_2Slots(){
        var personId = UUID.randomUUID();
        var start = LocalDateTime.of(2026,1,1,10,0);
        var end = LocalDateTime.of(2026,1,1,11,0);
        timeSlotsRepository.addTimeSlots(personId,start,end);
        var start1 = LocalDateTime.of(2026,1,1,10,30);
        var result = timeSlotsRepository.removeTimeSlots(personId,start1,end);
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.iterator().next().startFrom().isBefore(start1));
    }

    @Test
    void cleanOldTimeSlots_4Slots(){
        var personId = UUID.randomUUID();
        var start = LocalDateTime.of(2026,1,1,10,0);
        var end = LocalDateTime.of(2026,1,1,11,0);
        timeSlotsRepository.addTimeSlots(personId,start,end);
        var start1 = LocalDateTime.now().withMinute(0).plusHours(1);
        var end1 = LocalDateTime.now().withMinute(0).plusHours(2);
        var result = timeSlotsRepository.addTimeSlots(personId, start1, end1);
        assertEquals(8, result.size());
        timeSlotsRepository.cleanOldTimeSlots(personId);
        result = timeSlotsRepository.getPersonAvailability(personId);
        assertEquals(4, result.size());
    }

    @Test
    void reservePerson_30Mins(){
        var personId = UUID.randomUUID();
        var start = LocalDateTime.of(2026,1,1,10,0);
        var end = LocalDateTime.of(2026,1,1,11,0);
        timeSlotsRepository.addTimeSlots(personId,start,end);
        var start1 = LocalDateTime.of(2026,1,1,10,30);
        var result = timeSlotsRepository.reservePerson(personId,UUID.randomUUID(), start1, end);
        assertNotNull(result);
        assertEquals(4, result.size());
        result = timeSlotsRepository.getPersonAvailability(personId);
        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test
    void getPersonMeetings_2Meetings(){
        var personId = UUID.randomUUID();
        var start = LocalDateTime.of(2026,1,1,10,0);
        var end = LocalDateTime.of(2026,1,1,12,0);
        timeSlotsRepository.addTimeSlots(personId,start,end);
        var start1 = LocalDateTime.of(2026,1,1,10,30);
        var end1 = LocalDateTime.of(2026,1,1,11,0);
        timeSlotsRepository.reservePerson(personId,UUID.randomUUID(), start1, end1);
        start1 = LocalDateTime.of(2026,1,1,11,30);
        end1 = LocalDateTime.of(2026,1,1,12,0);
        timeSlotsRepository.reservePerson(personId,UUID.randomUUID(), start1, end1);
        var result = timeSlotsRepository.getPersonMeetings(personId);
        assertNotNull(result);
        assertEquals(2, result.size());
    }
}
