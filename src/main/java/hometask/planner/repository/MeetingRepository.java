package hometask.planner.repository;


import hometask.planner.entity.Meeting;
import hometask.planner.entity.Person;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class MeetingRepository {

    private final ConcurrentHashMap<UUID, Meeting> meetingData = new ConcurrentHashMap<>();

    public Meeting addMeeting(LocalDateTime timeStart, LocalDateTime timeEnd, String title, String description, List<Person> people) {
        Meeting meeting = new Meeting(UUID.randomUUID(), timeStart, timeEnd, title, description, people);
        meetingData.put(meeting.meetingId(), meeting);
        return meeting;
    }

    public Optional<Meeting> getMeeting(UUID meetingId) {
        return Optional.ofNullable(meetingData.get(meetingId));
    }

    public boolean removeMeeting(UUID meetingId) {
        return meetingData.remove(meetingId) != null;
    }

    public List<Meeting> getMeetingsForPerson(UUID personId) {
        return meetingData.values().stream()
                .filter(m -> m.people().stream().anyMatch(p -> p.personId().equals(personId)))
                .toList();
    }
}