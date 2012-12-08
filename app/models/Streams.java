package models;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import static iteratees.F.*;
import static iteratees.Iteratees.*;

public class Streams {

    public static interface Event {}

    public static class Operation implements Event {
        public String level;
        public Integer amount;
        public Operation(String level, Integer amount) {
            this.level = level;
            this.amount = amount;
        }
    }

    public static class SystemStatus implements Event {
        public String message;
        public SystemStatus(String message) {
            this.message = message;
        }
    }

    public static final Random random = new Random();

    public static final Enumerator<Event> operations = Enumerator.generate( 1, TimeUnit.SECONDS, new Function<Unit, Option<Event>>() {
        @Override
        public Option<Event> apply(Unit unit) {
            String status = random.nextBoolean() ? "public" : "private";
            return Option.<Event>some(new Operation(status, random.nextInt(1000)));
        }
    });

    public static final Enumerator<Event> noise = Enumerator.generate( 5, TimeUnit.SECONDS, new Function<Unit, Option<Event>>() {
        @Override
        public Option<Event> apply(Unit unit) {
            return Option.<Event>some(new SystemStatus("System message"));
        }
    });
    public static final Enumerator<Event> events = Enumerator.interleave( Event.class, operations, noise );

}
