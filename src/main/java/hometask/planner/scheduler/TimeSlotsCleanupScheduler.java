package hometask.planner.scheduler;

import hometask.planner.entity.Person;
import hometask.planner.repository.PeopleRepository;
import hometask.planner.repository.TimeSlotsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TimeSlotsCleanupScheduler {

    private final TimeSlotsRepository timeSlotsRepository;
    private final PeopleRepository peopleRepository;

    @Scheduled(cron = "0 0 0 * * *")
    public void cleanOldTimeSlots() {
        log.info("Running midnight cleanup of old time slots");
        peopleRepository.getAllPeople()
                .map(Person::personId)
                .doOnNext(id -> log.debug("Cleaning time slots for person {}", id))
                .doOnError(e -> log.error("Failed to clean time slots", e))
                .doOnComplete(() -> log.info("Midnight cleanup completed"))
                .subscribe(timeSlotsRepository::cleanOldTimeSlots);
    }
}