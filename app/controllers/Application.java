package controllers;

import static iteratees.Iteratees.*;
import static iteratees.F.*;

import java.util.concurrent.TimeUnit;

public class Application extends JIterateeController {

    public static void index() {
        render();
    }

    public static void sse() {
        Enumerator<String> enumerator = Enumerator.generate(1, TimeUnit.SECONDS, new Function<Unit, Option<String>>() {
            @Override
            public Option<String> apply(Unit unit) {
                return Option.some(System.currentTimeMillis() + "");
            }
        });
        System.out.println("enumerator 0 : " +enumerator);
        eventSource( enumerator );
    }
}