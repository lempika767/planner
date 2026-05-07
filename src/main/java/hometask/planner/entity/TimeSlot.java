package hometask.planner.entity;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;


public record TimeSlot(LocalDateTime startFrom, SlotType slotType, UUID personId, UUID meetingId) {

    @Override
    public boolean equals(Object o){
        if (this == o) return true;
        if (!(o instanceof TimeSlot ts)) return false;
        return this.personId.equals(ts.personId) && this.startFrom.equals(ts.startFrom);
    }

    @Override
    public int hashCode(){
        return Objects.hash(startFrom, personId);
    }

}
