package hometask.planner.repository;

import hometask.planner.entity.SlotType;
import hometask.planner.entity.TimeSlot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Repository
public class TimeSlotsRepository {

    private final Map<UUID, Set<TimeSlot>> personTimeSlots = new ConcurrentHashMap<>();

    private final int timeSlotDuration;

    public TimeSlotsRepository(@Value("${timeslot.durationMins}") int timeSlotDuration) {
        this.timeSlotDuration = timeSlotDuration;
    }

    public Flux<TimeSlot> getPersonAvailability(UUID personId){
        var slots = personTimeSlots.get(personId);
        if (slots == null) return Flux.empty();
        return Flux.fromStream(slots.parallelStream()
                .filter(ts -> SlotType.AVAILABLE.equals(ts.slotType())));
    }

    public Flux<UUID> getPersonMeetings(UUID personId) {
        var slots = personTimeSlots.get(personId);
        if (slots == null) return Flux.empty();
        return Flux.fromStream(slots.parallelStream()
                .filter(ts -> SlotType.BUSY.equals(ts.slotType()))
                .map(TimeSlot::meetingId)
                .distinct());
    }

    public Mono<Boolean> reservePerson(UUID personId, UUID meetingId, LocalDateTime start, LocalDateTime end){
         var updated = personTimeSlots.computeIfPresent(personId, (key, slots) ->
             slots.parallelStream()
                    .map( ts ->{
                        if (!ts.startFrom().isBefore(start) && !ts.startFrom().isAfter(end)){
                            return  new TimeSlot(ts.startFrom(), SlotType.BUSY, personId, meetingId);
                        } return ts;
                    })
                    .collect(Collectors.toCollection(HashSet::new)));
         return Mono.just(updated != null);

    }

    public Flux<TimeSlot> addTimeSlots(UUID personId, LocalDateTime start, LocalDateTime end){
        var newTimeSlots = makeBricksInPeriod(personId, start,end);
        var personSlots =  personTimeSlots.compute(personId, (key, slots) -> {
            if (slots == null) slots = new HashSet<>();
            slots.addAll(newTimeSlots); // no need to removeAll first, it's a Set
            return slots;
        });
        return Flux.fromIterable(personSlots);
    }

    public Flux<TimeSlot> removeTimeSlots(UUID personId, LocalDateTime start, LocalDateTime end) {
        var timeSlotsToRemove = makeBricksInPeriod(personId, start, end);
        var remaining = personTimeSlots.computeIfPresent(personId, (key, slots) -> {
            slots.removeAll(timeSlotsToRemove);
            return slots.isEmpty() ? null : slots;
        });
        return remaining == null ? Flux.empty() : Flux.fromIterable(remaining);
    }

    Set<TimeSlot> makeBricksInPeriod(UUID personId, LocalDateTime start, LocalDateTime end){
        // I need to do breaks from minute Zero to have compatible blocks
        var startOfHour = start.withMinute(0);
        long cycles = ChronoUnit.MINUTES.between(startOfHour,end)/timeSlotDuration;
        return Stream.iterate(startOfHour, (el) -> el.plusMinutes(timeSlotDuration))
                .limit(cycles)
                //and now filter out all bricks before start date
                .filter( time -> !time.isBefore(start))
                .map( el -> new TimeSlot(el, SlotType.AVAILABLE, personId, null))
                .collect(Collectors.toSet());
    }

    public long calculateBricksInPeriod(LocalDateTime start, LocalDateTime end){
        var startOfHour = start.withMinute(0);
        long cycles =  ChronoUnit.MINUTES.between(startOfHour,end)/timeSlotDuration;
        return Stream.iterate(startOfHour, (el) -> el.plusMinutes(timeSlotDuration))
                .limit(cycles)
                //and now filter out all bricks before start date
                .filter( time -> !time.isBefore(start))
                .count();
    }

    public void cleanOldTimeSlots(UUID personId){
        personTimeSlots.computeIfPresent(personId, (key, slots) -> {
            slots.removeIf(ts -> ts.startFrom().isBefore(LocalDateTime.now()));
            return slots.isEmpty() ? null : slots;
        });
    }
}
