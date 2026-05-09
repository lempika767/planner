package hometask.planner.repository;

import hometask.planner.entity.Person;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class PeopleRepository {
    private final ConcurrentHashMap<String, Person> peopleData = new ConcurrentHashMap<>();

    public Mono<Person> getOrAddPerson(String name) {
        return Mono.fromCallable(() ->
                peopleData.computeIfAbsent(name, key -> new Person(UUID.randomUUID(), name))
        );
    }

    public Flux<Person> getPeople(List<String> names) {
        return Flux.fromIterable(names)
                .mapNotNull(peopleData::get);
    }

    public Mono<Boolean> removePerson(String name) {
        return Mono.fromCallable(() -> peopleData.remove(name) != null);
    }

    public Flux<Person> getAllPeople(){
        return Flux.fromIterable(peopleData.values());
    }
}
