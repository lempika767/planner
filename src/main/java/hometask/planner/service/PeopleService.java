package hometask.planner.service;

import hometask.planner.entity.Person;
import hometask.planner.repository.PeopleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PeopleService {
    private final PeopleRepository peopleRepository;

    public Mono<Person> getOrAddPerson(String name) {
        return peopleRepository.getOrAddPerson(name);
    }

    public Flux<Person> getPeople(List<String> names) {
        return peopleRepository.getPeople(names);
    }

    public Mono<Boolean> removePerson(String name) {
        return peopleRepository.removePerson(name);
    }
}