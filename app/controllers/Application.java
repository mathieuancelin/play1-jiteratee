package controllers;

import static iteratee.Iteratees.*;
import static iteratee.F.*;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Application extends JIterateesController {

    public static void index() {
        render();
    }

    public static final AtomicInteger integer = new AtomicInteger(0);

    public static final PushEnumerator<String> enumerator = Enumerator.unicast( String.class );
    public static final HubEnumerator<String> hub = Enumerator.broadcast( enumerator );

    public static void push() {
        enumerator.push( integer.incrementAndGet() + "" );
        ok();
    }

    public static void comet() {
        comet( "parent.cometMessage", Enumerator.generate( 1, TimeUnit.SECONDS, new Function<Unit, Option<String>>() {
            @Override
            public Option<String> apply(Unit unit) {
                return Option.some( System.currentTimeMillis() + "" );
            }
        }));
    }

    public static void ssePushed() {
        eventSource( hub );
    }

    public static void sse() {
        eventSource( Enumerator.generate( 1, TimeUnit.SECONDS, new Function<Unit, Option<String>>() {
            @Override
            public Option<String> apply(Unit unit) {
                return Option.some( System.currentTimeMillis() + "" );
            }
        }));
    }

    public static void stream() {
        stream( Enumerator.generate( 1, TimeUnit.SECONDS, new Function<Unit, Option<String>>() {
            @Override
            public Option<String> apply(Unit unit) {
                return Option.some( System.currentTimeMillis() + "\n" );
            }
        }));
    }

    public static class WSApplication extends JIterateesWebsocketController {
        public static void ws() {
            final PushEnumerator<String> out = Enumerator.unicast( String.class );
            final Iteratee<String, Unit> in = Iteratee.foreach( new UFunction<String>() {
                public void invoke(String s) {
                    out.push("Received : " + s);
                }
            });
            websocket( in, out );
        }
    }
}