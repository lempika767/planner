package hometask.planner.service;

import hometask.planner.dto.TimeSlotDto;
import hometask.planner.entity.Person;
import hometask.planner.entity.TimeSlot;
import hometask.planner.repository.MeetingRepository;
import hometask.planner.repository.PeopleRepository;
import hometask.planner.repository.TimeSlotsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class OperationalService {
    private final ConcurrentHashMap<UUID, ReadWriteLock> personSlotsBucketLocks = new ConcurrentHashMap<>();
    private final MeetingRepository meetingRepository;
    private final PeopleRepository peopleRepository;
    private final TimeSlotsRepository timeSlotsRepository;

    private final ConcurrentHashMap<UUID, Semaphore> personLocks = new ConcurrentHashMap<>();

    private Semaphore getLock(UUID personId) {
        return personLocks.computeIfAbsent(personId, id -> new Semaphore(1));
    }

    public Mono<Boolean> createMeeting(String title, String description, List<String> people, LocalDateTime start, LocalDateTime end) {
        return peopleRepository.getPeople(people)
                .collectList()
                .flatMap(peopleEntities -> {
                    if (peopleEntities.size() != people.size()) {
                        return Mono.error(new NoSuchElementException("Someone from people not exists"));
                    }

                    // acquire all semaphores sorted by ID to prevent deadlock
                    List<Semaphore> semaphores = peopleEntities.stream()
                            .map(p -> getLock(p.personId()))
                            .sorted(Comparator.comparingInt(System::identityHashCode))
                            .toList();

                    return Mono.fromCallable(() -> {
                                List<Semaphore> acquired = new ArrayList<>();
                                try {
                                    for (Semaphore s : semaphores) {
                                        if (!s.tryAcquire(5, TimeUnit.SECONDS)) {
                                            acquired.forEach(Semaphore::release); // release partial locks
                                            throw new IllegalStateException("Could not acquire lock within 5 seconds");
                                        }
                                        acquired.add(s);
                                    }
                                } catch (InterruptedException e) {
                                    acquired.forEach(Semaphore::release);
                                    Thread.currentThread().interrupt();
                                    throw e;
                                }
                                return semaphores;
                            })
                            .subscribeOn(Schedulers.boundedElastic()) // blocking acquire off event loop
                            .flatMap(acquired ->
                                    checkPeopleAvailabilityInPeriod(peopleEntities, start, end, title)
                                            .then(Mono.defer(() -> {
                                                var meeting = meetingRepository.addMeeting(start, end, title, description, peopleEntities);
                                                return reservePeopleTime(peopleEntities, start, end, meeting.meetingId());
                                            }))
                                            .thenReturn(true)
                                            .doFinally(signal -> semaphores.forEach(Semaphore::release)) // always release
                            );
                });
    }

    private Mono<Void> reservePeopleTime(List<Person> peopleEntities, LocalDateTime start, LocalDateTime end, UUID meetingId) {
        return Flux.fromIterable(peopleEntities)
                .flatMap(person -> timeSlotsRepository.reservePerson(person.personId(), meetingId, start, end))
                .then();
    }

    Mono<Void> checkPeopleAvailabilityInPeriod(List<Person> peopleEntities, LocalDateTime start, LocalDateTime end, String meetingName) {
        var bricksInPeriod = timeSlotsRepository.calculateBricksInPeriod(start, end);
        return Flux.fromIterable(peopleEntities)
                .flatMap(person -> timeSlotsRepository.getPersonAvailability(person.personId())
                        .filter(ts -> !ts.startFrom().isBefore(start) && !ts.startFrom().isAfter(end))
                        .count()
                        .flatMap(availableSlots -> {
                            if (bricksInPeriod != availableSlots) {
                                return Mono.error(new PersonNotAvailableException(
                                        String.format("Person %s not available for meeting %s", person.name(), meetingName)));
                            }
                            return Mono.empty();
                        })
                )
                .then();
    }

    public Flux<TimeSlotDto> addTimeSlots(UUID personId, LocalDateTime start, LocalDateTime end) {
        return executeWithWriteLock(personId, () ->
                timeSlotsRepository.addTimeSlots(personId, start, end)
        ).map(TimeSlotDto::from);
    }

    public Flux<TimeSlotDto> removeTimeSlots(UUID personId, LocalDateTime start, LocalDateTime end) {
        return executeWithWriteLock(personId, () ->
                timeSlotsRepository.removeTimeSlots(personId, start, end)
        ).map(TimeSlotDto::from);
    }

    private <T> T executeWithWriteLock(UUID personId, Supplier<T> action) {
        Lock lock = personSlotsBucketLocks
                .computeIfAbsent(personId, key -> new ReentrantReadWriteLock())
                .writeLock();
        try {
            if (!lock.tryLock(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Could not acquire lock for person " + personId);
            }
            return action.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for lock for person " + personId);
        } finally {
            lock.unlock();
        }
    }

}
