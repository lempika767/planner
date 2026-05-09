package hometask.planner.controller;

import hometask.planner.dto.TimeSlotDto;
import hometask.planner.service.TimeSlotsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/timeslots")
@RequiredArgsConstructor
public class TimeSlotsController {

    private final TimeSlotsService timeSlotsService;

    // GET /api/timeslots/{personId}/availability
    @GetMapping("/{personId}/availability")
    public Flux<TimeSlotDto> getPersonAvailability(@PathVariable UUID personId) {
        return timeSlotsService.getPersonAvailability(personId);
    }

    // GET /api/timeslots/{personId}/meetings
    @GetMapping("/{personId}/meetings")
    public Flux<UUID> getPersonMeetings(@PathVariable UUID personId) {
        return timeSlotsService.getPersonMeetings(personId);
    }

    // DELETE /api/timeslots/{personId}/old
    @DeleteMapping("/{personId}/old")
    public Mono<Void> cleanOldTimeSlots(@PathVariable UUID personId) {
        return timeSlotsService.cleanOldTimeSlots(personId);
    }
}