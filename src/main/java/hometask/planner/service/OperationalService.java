package hometask.planner.service;

import hometask.planner.entity.Person;
import hometask.planner.entity.TimeSlot;
import hometask.planner.repository.MeetingRepository;
import hometask.planner.repository.PeopleRepository;
import hometask.planner.repository.TimeSlotsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
@RequiredArgsConstructor
public class OperationalService {
    private final ConcurrentHashMap<UUID, ReadWriteLock> personSlotsBucketLocks = new ConcurrentHashMap<>();
    private final MeetingRepository meetingRepository;
    private final PeopleRepository peopleRepository;
    private final TimeSlotsRepository timeSlotsRepository;

    public boolean createMeeting(String meetingName, List<String> people, LocalDateTime start, LocalDateTime end) throws PersonNotAvailableException {
        var peopleEntities = peopleRepository.getPeople(people);
        if (peopleEntities.size() != people.size()){
            throw new NoSuchElementException("Someone from people not exists");
        }
        List<Lock> acquiredLocks = new ArrayList<>();
        try {
            acquiredLocks = acquireLocks(peopleEntities);
            // all locks acquired
            checkPeopleAvailabilityInPeriod(peopleEntities,start,end,meetingName);
            var meeting = meetingRepository.addMeeting(start,end, meetingName, peopleEntities);
            reservePeopleTime(peopleEntities, start, end, meeting.meetingId());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // restore interrupted status
            return false;
        } finally {
            acquiredLocks.forEach(Lock::unlock); // only unlock what we locked
        }
    }

    private void reservePeopleTime(List<Person> peopleEntities, LocalDateTime start, LocalDateTime end, UUID uuid) {
        for (Person person : peopleEntities) {
            timeSlotsRepository.removeTimeSlots(person.personId(),start,end);
        }
    }

    void checkPeopleAvailabilityInPeriod(List<Person> peopleEntities, LocalDateTime start, LocalDateTime end, String meetingName) throws PersonNotAvailableException {
        var bricksInPeriod = timeSlotsRepository.calculateBricksInPeriod(start,end);
        for (Person person : peopleEntities) {
            var availableslots = timeSlotsRepository.getPersonAvailability(person.personId())
                    .parallelStream()
                    .filter(ts -> !ts.startFrom().isBefore(start) && !ts.startFrom().isAfter(end))
                    .count();
            if (bricksInPeriod != availableslots){
                throw new PersonNotAvailableException(String.format("Person %s not available for meeting %s", person.name(), meetingName));
            }
        }
    }

    List<Lock> acquireLocks(List<Person> peopleEntities) throws InterruptedException {
        List<Lock> acquiredLocks = new ArrayList<>();
        for (Person person : peopleEntities) {
            Lock lock = personSlotsBucketLocks
                    .computeIfAbsent(person.personId(), key -> new ReentrantReadWriteLock())
                    .writeLock();
            if (lock.tryLock(5, TimeUnit.SECONDS)) {
                acquiredLocks.add(lock);
            } else {
                throw new InterruptedException(); // couldn't acquire all locks
            }
        }
        return acquiredLocks;
    }

    public boolean reservePersonTime(UUID personId, UUID meetingId, LocalDateTime start, LocalDateTime end) {
        return executeWithWriteLock(personId, () ->
                timeSlotsRepository.reservePerson(personId, meetingId, start, end) != null
        );
    }

    public Set<TimeSlot> addTimeSlots(UUID personId, LocalDateTime start, LocalDateTime end) {
        return executeWithWriteLock(personId, () ->
                timeSlotsRepository.addTimeSlots(personId, start, end)
        );
    }

    public Set<TimeSlot> removeTimeSlots(UUID personId, LocalDateTime start, LocalDateTime end) {
        return executeWithWriteLock(personId, () ->
                timeSlotsRepository.removeTimeSlots(personId, start, end)
        );
    }

    private <T> T executeWithWriteLock(UUID personId, LockAction<T> action) {
        Lock lock = personSlotsBucketLocks
                .computeIfAbsent(personId, key -> new ReentrantReadWriteLock())
                .writeLock();
        try {
            if (!lock.tryLock(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Could not acquire lock for person " + personId);
            }
            return action.execute();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for lock for person " + personId);
        } finally {
            lock.unlock();
        }
    }

    @FunctionalInterface
    private interface LockAction<T> {
        T execute();
    }
}
