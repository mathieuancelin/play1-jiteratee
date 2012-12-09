package models;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import static iteratee.F.*;
import static iteratee.Iteratees.*;

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

    public static final HubEnumerator<Event> hub = Enumerator.broadcast( Streams.events );

    public static final Enumeratee<Event, String> asJson = Enumeratee.map( new Function<Event, String>() {
        @Override
        public String apply(Event o) {
            for (SystemStatus status : caseClassOf(SystemStatus.class, o)) {
                return "{\"type\":\"status\", \"message\":\"" + status.message + "\"}";
            }
            for (Operation operation : caseClassOf(Operation.class ,o)) {
                return "{\"type\":\"operation\", \"amount\":" + operation.amount + ", \"visibility\":\"" + operation.level + "\"}";
            }
            return "";
        }
    });

    public static Enumeratee<Event, Event> getInBounds(final int lowerBoundValue, final int higherBoundValue) {
        return Enumeratee.collect(new Function<Event, Option<Event>>() {
            @Override
            public Option<Event> apply(Event o) {
                for (SystemStatus status : caseClassOf(SystemStatus.class, o)) {
                    return Option.<Event>some(status);
                }
                for (Operation operation : caseClassOf(Operation.class, o)) {
                    if (operation.amount > lowerBoundValue && operation.amount < higherBoundValue) {
                        return Option.<Event>some(operation);
                    }
                }
                return Option.none();
            }
        });
    }

    public static Enumeratee<Event, Event> getSecure(final String roleValue) {
        return Enumeratee.collect(new Function<Event, Option<Event>>() {
            @Override
            public Option<Event> apply(Event o) {
                for (SystemStatus status : caseClassOf(SystemStatus.class, o)) {
                    if (roleValue.equals("MANAGER")) {
                        return Option.<Event>some(status);
                    }
                }
                for (Operation operation : caseClassOf(Operation.class, o)) {
                    if (operation.level.equals("public")) {
                        return Option.<Event>some(operation);
                    } else {
                        if (roleValue.equals("MANAGER")) {
                            return Option.<Event>some(operation);
                        }
                    }
                }
                return Option.none();
            }
        });
    }

}
