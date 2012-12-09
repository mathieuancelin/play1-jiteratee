package controllers;

import models.Streams;

import static iteratee.Iteratees.*;
import static models.Streams.*;

public class RealTimeBroadcast extends JIterateesController {

    public static void index(String role) {
        render(role);
    }

    public static void feed(String role, int lowerBound, int higherBound) {
        Enumeratee<Event, Event> secure = Streams.getSecure(role);
        Enumeratee<Event, Event> inBounds = Streams.getInBounds(lowerBound, higherBound);
        eventSource( Enumerator.feed( Event.class, Streams.hub ).through( secure ).through( inBounds ).through( Streams.asJson ) );
    }
}