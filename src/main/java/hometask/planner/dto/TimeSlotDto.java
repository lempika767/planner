package hometask.planner.dto;

import hometask.planner.entity.SlotType;
import hometask.planner.entity.TimeSlot;

import java.time.LocalDateTime;
import java.util.UUID;

public record TimeSlotDto(
        LocalDateTime startFrom,
        SlotType slotType,
        UUID personId,
        UUID meetingId
) {
    public static TimeSlotDto from(TimeSlot timeSlot) {
        return new TimeSlotDto(
                timeSlot.startFrom(),
                timeSlot.slotType(),
                timeSlot.personId(),
                timeSlot.meetingId()
        );
    }
}