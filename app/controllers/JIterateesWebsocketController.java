package controllers;


import play.mvc.Http;
import play.mvc.WebSocketController;

import static iteratee.F.*;
import static iteratee.Iteratees.*;

public abstract class JIterateesWebsocketController extends WebSocketController {

    protected static void websocket(final Iteratee<String, Unit> inIteratee, final Enumerator<String> outEnumerator) {
        websocket(inIteratee, new JIterateesController.Identity<String>(), outEnumerator, new JIterateesController.Identity<String>());
    }

    protected static void websocket(final Iteratee<String, Unit> inIteratee, final HubEnumerator<String> outEnumerator) {
        websocket(inIteratee, new JIterateesController.Identity<String>(), outEnumerator, new JIterateesController.Identity<String>());
    }

    protected static <FR> void websocket(
            final Iteratee<FR, Unit> inIteratee, final JIterateesController.Builder<String, FR> inBuilder,
            final Enumerator<FR> outEnumerator, final JIterateesController.Builder<FR, String> outBuilder) {

        final Iteratee<FR, Unit> send = Iteratee.foreach(new Function<FR, Unit>() {
            @Override
            public Unit apply(FR s) {
                if (inbound.isOpen()) {
                    outbound.send(outBuilder.build(s));
                }
                return Unit.unit();
            }
        });
        final PushEnumerator<String> push = Enumerator.unicast(String.class);
        push.through(Enumeratee.map(new Function<String, FR>() {
            @Override
            public FR apply(String s) {
                return inBuilder.build(s);
            }
        })).applyOn(inIteratee);
        outEnumerator.applyOn(send);

        while(inbound.isOpen()) {
            Http.WebSocketEvent e = await(inbound.nextEvent());

            if(e instanceof Http.WebSocketFrame) {
                Http.WebSocketFrame frame = (Http.WebSocketFrame) e;
                if(!frame.isBinary) {
                    push.push(frame.textData);
                }
            }
            if(e instanceof Http.WebSocketClose) {
                push.stop();
            }
        }
    }

    protected static <FR> void websocket(
            final Iteratee<FR, Unit> inIteratee, final JIterateesController.Builder<String, FR> inBuilder,
            final HubEnumerator<FR> outEnumerator, final JIterateesController.Builder<FR, String> outBuilder) {

        final Iteratee<FR, Unit> send = Iteratee.foreach(new Function<FR, Unit>() {
            @Override
            public Unit apply(FR s) {
                outbound.send(outBuilder.build(s));
                return Unit.unit();
            }
        });
        final PushEnumerator<String> push = Enumerator.unicast(String.class);
        push.through(Enumeratee.map(new Function<String, FR>() {
            @Override
            public FR apply(String s) {
                return inBuilder.build(s);
            }
        })).applyOn(inIteratee);
        outEnumerator.add(send);

        while(inbound.isOpen()) {
            Http.WebSocketEvent e = await(inbound.nextEvent());

            if(e instanceof Http.WebSocketFrame) {
                Http.WebSocketFrame frame = (Http.WebSocketFrame) e;
                if(!frame.isBinary) {
                    push.push(frame.textData);
                }
            }
            if(e instanceof Http.WebSocketClose) {
                outEnumerator.stop();
                push.stop();
            }
        }
    }
}
