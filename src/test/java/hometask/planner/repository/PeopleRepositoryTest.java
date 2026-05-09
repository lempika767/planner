package hometask.planner.repository;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PeopleRepositoryTest {
    private final PeopleRepository peopleRepository = new PeopleRepository();

    @Test
    void addPerson(){
        var name = "Ali";
        var person = peopleRepository.getOrAddPerson(name).block();
        assertNotNull(person);
        assertEquals(name, person.name());
        assertNotNull(person.personId());
    }

    @Test
    void removePerson_personExists(){
        var name = "Ali";
        var person = peopleRepository.getOrAddPerson(name).block();
        var result = peopleRepository.removePerson(name).block();
        assertTrue(result);
        var person1 = peopleRepository.getOrAddPerson(name).block();
        assertNotEquals(person.personId(), person1.personId());
    }

    @Test
    void removePerson_personNotExists(){
        var name = "Ali";
        var name1 = "Anna";
        peopleRepository.getOrAddPerson(name).block();
        var result = peopleRepository.removePerson(name1).block();
        assertFalse(result);
    }

}
