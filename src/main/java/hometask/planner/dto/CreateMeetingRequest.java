package hometask.planner.dto;

import java.time.LocalDateTime;
import java.util.List;

public record CreateMeetingRequest(
        String title,
        String description,
        List<String> people,
        LocalDateTime start,
        LocalDateTime end
) {}