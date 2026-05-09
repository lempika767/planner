package hometask.planner.controller;

import hometask.planner.dto.CreateMeetingRequest;
import hometask.planner.dto.TimeSlotDto;
import hometask.planner.service.OperationalService;
import hometask.planner.service.PersonNotAvailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
public class OperationalController {

    private final OperationalService operationalService;

    // POST /api/meetings
    // {
    //   "title": "Standup",
    //   "people": ["Alice", "Bob"],
    //   "start": "2025-06-01T10:00:00",
    //   "end": "2025-06-01T11:00:00"
    // }
    @PostMapping
    public Mono<ResponseEntity<Boolean>> createMeeting(@RequestBody CreateMeetingRequest request) {
        return operationalService.createMeeting(request.title(), request.description(), request.people(), request.start(), request.end())
                .map(ResponseEntity::ok)
                .onErrorResume(NoSuchElementException.class,
                        e -> Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(false)))
                .onErrorResume(PersonNotAvailableException.class,
                        e -> Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(false)))
                .onErrorResume(IllegalStateException.class,
                        e -> Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(false)));
    }

    // POST /api/meetings/timeslots/{personId}?start=...&end=...
    @PostMapping("/timeslots/{personId}")
    public Flux<TimeSlotDto> addTimeSlots(
            @PathVariable UUID personId,
            @RequestParam LocalDateTime start,
            @RequestParam LocalDateTime end) {
        return operationalService.addTimeSlots(personId, start, end);
    }

    // DELETE /api/meetings/timeslots/{personId}?start=...&end=...
    @DeleteMapping("/timeslots/{personId}")
    public Flux<TimeSlotDto> removeTimeSlots(
            @PathVariable UUID personId,
            @RequestParam LocalDateTime start,
            @RequestParam LocalDateTime end) {
        return operationalService.removeTimeSlots(personId, start, end);
    }

}