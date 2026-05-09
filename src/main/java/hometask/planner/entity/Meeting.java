package hometask.planner.entity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record Meeting(UUID meetingId, LocalDateTime timeStart, LocalDateTime timeEnd, String title, String description, List<Person> people) {
}
