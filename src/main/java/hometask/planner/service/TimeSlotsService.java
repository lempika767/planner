package hometask.planner.service;

import hometask.planner.dto.TimeSlotDto;
import hometask.planner.repository.TimeSlotsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TimeSlotsService {

    private final TimeSlotsRepository timeSlotsRepository;

    public Flux<TimeSlotDto> getPersonAvailability(UUID personId) {
        return timeSlotsRepository.getPersonAvailability(personId)
                .map(TimeSlotDto::from);
    }

    public Flux<UUID> getPersonMeetings(UUID personId) {
        return timeSlotsRepository.getPersonMeetings(personId);
    }

    public Mono<Void> cleanOldTimeSlots(UUID personId) {
        return Mono.fromRunnable(() -> timeSlotsRepository.cleanOldTimeSlots(personId));
    }
}