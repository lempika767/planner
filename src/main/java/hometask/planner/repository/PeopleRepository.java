package hometask.planner.repository;

import hometask.planner.entity.Person;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class PeopleRepository {
    private final ConcurrentHashMap<String, Person> peopleData = new ConcurrentHashMap<>();

    public Person getOrAddPerson(String name){
        return peopleData.computeIfAbsent(name, (key) -> new Person(UUID.randomUUID(), name));
    }

    public List<Person> getPeople(List<String> people){
        return  people.parallelStream()
                .map(peopleData::get)
                .toList();
    }

    public boolean removePerson(String name){
        return peopleData.remove(name) != null;
    }
}
