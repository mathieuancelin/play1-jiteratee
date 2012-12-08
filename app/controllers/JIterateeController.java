package controllers;


import play.mvc.Controller;
import play.mvc.Http;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static iteratees.F.*;
import static iteratees.Iteratees.*;

public abstract class JIterateeController extends Controller {

    public static final String EVENTSOURCE = "text/event-stream";

    public static <T> void streamFile(final File file) {
        stream(Enumerator.fromFile(file, 4096), new ByteIdentity(), "application/octet-stream");
    }

    public static <T> void stream(final Enumerator<T> enumerator) {
        stream(enumerator, new ByteBuilder<T>() {
            @Override
            public byte[] build(T value) {
                try {
                    return value.toString().getBytes("UTF-8");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                    return new byte[0];
                }
            }
        }, "");
    }

    public static <T> void stream(final HubEnumerator<T> enumerator) {
        stream(enumerator, new ByteBuilder<T>() {
            @Override
            public byte[] build(T value) {
                try {
                    return value.toString().getBytes("UTF-8");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                    return new byte[0];
                }
            }
        }, "");
    }


    public static <T> void stream(final HubEnumerator<T> enumerator, final ByteBuilder<T> builder, final String contentType) {
        final CountDownLatch latch = new CountDownLatch(1);
        final play.libs.F.EventStream<byte[]> chuncks = new play.libs.F.EventStream<byte[]>();
        enumerator.add(Iteratee.foreach(new UFunction<T>() {
            @Override
            public void invoke(T value) {
                chuncks.publish(builder.build(value));
            }
        }));
//        enumerator.onRedeem(new Action<Promise<Unit>>() {
//            @Override
//            public void apply(Promise<Unit> unitPromise) {
//                latch.countDown();
//            }
//        });
        Http.Response.current().setHeader("Content-Length", "-1");
        Http.Response.current().contentType = contentType;
        while(latch.getCount() > 0) {
            byte[] chunck = Controller.await(chuncks.nextEvent());
            Http.Response.current().writeChunk(chunck);
        }
    }

    public static <T> void stream(final Enumerator<T> enumerator, final ByteBuilder<T> builder, final String contentType) {
        final CountDownLatch latch = new CountDownLatch(1);
        final play.libs.F.EventStream<byte[]> chuncks = new play.libs.F.EventStream<byte[]>();
        enumerator.applyOn(Iteratee.foreach(new UFunction<T>() {
            @Override
            public void invoke(T value) {
                chuncks.publish(builder.build(value));
            }
        })).onRedeem(new Action<Promise<Unit>>() {
            @Override
            public void apply(Promise<Unit> unitPromise) {
                latch.countDown();
            }
        });
        response.setHeader("Content-Length", "-1");
        response.contentType = contentType;
        while(latch.getCount() > 0) {
            byte[] chunck = await(chuncks.nextEvent());
            response.writeChunk(chunck);
        }
    }

    public static interface ByteBuilder<T> extends Builder<T, byte[]> {
        public byte[] build(T value);
    }
    public static interface StrBuilder<T> extends Builder<T, String> {
        public String build(T value);
    }

    public static interface BuilderFromStr<T> extends Builder<String, T>{
        public T build(String value);
    }

    public static interface Builder<I, O> {
        public O build(I value);
    }

    public static class Identity<T> implements Builder<T, T> {
        @Override
        public T build(T value) {
            return value;
        }
    }

    public static class ByteIdentity implements ByteBuilder<byte[]> {
        @Override
        public byte[] build(byte[] value) {
            return value;
        }
    }

    protected static <T> void eventSource(final Enumerator<T> enumerator, final StrBuilder<T> builder) {
        final CountDownLatch latch = new CountDownLatch(1);
        final play.libs.F.EventStream<byte[]> chuncks = new play.libs.F.EventStream<byte[]>();
        enumerator.applyOn(Iteratee.foreach(new UFunction<T>() {
            @Override
            public void invoke(T value) {
                try {
                    chuncks.publish(("data: " + builder.build(value) + "\n\n").getBytes("UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
        })).onRedeem(new Action<Promise<Unit>>() {
            @Override
            public void apply(Promise<Unit> unitPromise) {
                latch.countDown();
            }
        });
        response.setHeader("Content-Length", "-1");
        response.contentType = EVENTSOURCE;
        while(latch.getCount() > 0) {
            byte[] chunck = await(chuncks.nextEvent());
            response.writeChunk(chunck);
        }
    }

    protected static <T> void eventSource(final HubEnumerator<T> enumerator, final StrBuilder<T> builder) {
        final CountDownLatch latch = new CountDownLatch(1);
        final play.libs.F.EventStream<byte[]> chuncks = new play.libs.F.EventStream<byte[]>();
        enumerator.add(Iteratee.foreach(new UFunction<T>() {
            @Override
            public void invoke(T value) {
                try {
                    chuncks.publish(("data: " + builder.build(value) + "\n\n").getBytes("UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
        }));
//        enumerator.onRedeem(new Action<Promise<Unit>>() {
//            @Override
//            public void apply(Promise<Unit> unitPromise) {
//                latch.countDown();
//            }
//        });
        response.setHeader("Content-Length", "-1");
        response.contentType = EVENTSOURCE;
        while(latch.getCount() > 0) {
            byte[] chunck = await(chuncks.nextEvent());
            response.writeChunk(chunck);
        }
    }
//
    protected static <T> void eventSource(final Enumerator<T> enumerator) {
        eventSource(enumerator, new StrBuilder<T>() {
            @Override
            public String build(T value) {
                return value.toString();
            }
        });
    }

    protected static <T> void eventSource(final HubEnumerator<T> enumerator) {
        eventSource(enumerator, new StrBuilder<T>() {
            @Override
            public String build(T value) {
                return value.toString();
            }
        });
    }

//
//    public static <T> Results.Status comet(String callback, final HubEnumerator<T> enumerator, final StrBuilder<T> builder) {
//        Comet comet = new Comet(callback) {
//            public void onConnected() {
//                enumerator.add(Iteratees.Iteratee.foreach(new Function<T, Unit>() {
//                    @Override
//                    public Unit apply(T s) {
//                        sendMessage(builder.build(s));
//                        return Unit.unit();
//                    }
//                }));
//            }
//        };
//        comet.onDisconnected(new play.libs.F.Callback0() {
//            @Override
//            public void invoke() throws Throwable {}
//        });
//        return Controller.ok(comet);
//    }
//
//    public static <T> Results.Status comet(String callback, final Enumerator<T> enumerator, final StrBuilder<T> builder) {
//        Comet comet = new Comet(callback) {
//            public void onConnected() {
//                enumerator.applyOn(Iteratees.Iteratee.foreach(new Function<T, Unit>() {
//                    @Override
//                    public Unit apply(T s) {
//                        sendMessage(builder.build(s));
//                        return Unit.unit();
//                    }
//                })).onRedeem(new Action<Promise<Unit>>() {
//                    @Override
//                    public void apply(Promise<Unit> unitPromise) {
//                        close();
//                    }
//                });
//            }
//        };
//        return Controller.ok(comet);
//    }
//
//    public static <T> Results.Status comet(String callback, final Enumerator<T> enumerator) {
//        return comet(callback, enumerator, new StrBuilder<T>() {
//            @Override
//            public String build(T value) {
//                if (value instanceof JsonNode) {
//                    return Json.stringify((JsonNode) value);
//                } else {
//                    return value.toString();
//                }
//            }
//        });
//    }
//
//    public static <T> Results.Status comet(String callback, final HubEnumerator<T> enumerator) {
//        return comet(callback, enumerator, new StrBuilder<T>() {
//            @Override
//            public String build(T value) {
//                if (value instanceof JsonNode) {
//                    return Json.stringify((JsonNode) value);
//                } else {
//                    return value.toString();
//                }
//            }
//        });
//    }

//    public static <T> WebSocket<T> websocket(final Class<T> clazz, final Iteratee<T, Unit> inIteratee, final Enumerator<T> outEnumerator) {
//        return websocket(clazz, clazz, inIteratee, new Identity<T>(), outEnumerator, new Identity<T>());
//    }
//
//    public static <T> WebSocket<T> websocket(final Class<T> clazz, final Iteratee<T, Unit> inIteratee, final HubEnumerator<T> outEnumerator) {
//        return websocket(clazz, clazz, inIteratee, new Identity<T>(), outEnumerator, new Identity<T>());
//    }
//
//    public static <T> WebSocket<String> websocketStr(final Iteratee<String, Unit> inIteratee, final Enumerator<String> outEnumerator) {
//        return websocket(String.class, String.class, inIteratee, new Identity<String>(), outEnumerator, new Identity<String>());
//    }
//
//    public static WebSocket<String> websocketStr(final Iteratee<String, Unit> inIteratee, final HubEnumerator<String> outEnumerator) {
//        return websocket(String.class, String.class, inIteratee, new Identity<String>(), outEnumerator, new Identity<String>());
//    }
//
//    public static <T> WebSocket<JsonNode> websocketJSON(final Iteratee<JsonNode, Unit> inIteratee, final Enumerator<JsonNode> outEnumerator) {
//        return websocket(JsonNode.class, JsonNode.class, inIteratee, new Identity<JsonNode>(), outEnumerator, new Identity<JsonNode>());
//    }
//
//    public static WebSocket<JsonNode> websocketJSON(final Iteratee<JsonNode, Unit> inIteratee, final HubEnumerator<JsonNode> outEnumerator) {
//        return websocket(JsonNode.class, JsonNode.class, inIteratee, new Identity<JsonNode>(), outEnumerator, new Identity<JsonNode>());
//    }
//
//    public static <IO, FR> WebSocket<IO> websocket(final Class<IO> clazz, final Class<FR> from,
//            final Iteratee<FR, Unit> inIteratee, final Builder<IO, FR> inBuilder,
//            final Enumerator<FR> outEnumerator, final Builder<FR, IO> outBuilder) {
//
//        WebSocket<IO> ws =  new WebSocket<IO>() {
//            public void onReady(final WebSocket.In<IO> in, final WebSocket.Out<IO> out) {
//                final Iteratee<FR, Unit> send = Iteratee.foreach(new Function<FR, Unit>() {
//                    @Override
//                    public Unit apply(FR s) {
//                        out.write(outBuilder.build(s));
//                        return Unit.unit();
//                    }
//                });
//                final PushEnumerator<IO> push = Enumerator.unicast(clazz);
//                in.onMessage(new play.libs.F.Callback<IO>() {
//                    public void invoke(IO event) {
//                        push.push(event);
//                    }
//                });
//                in.onClose(new play.libs.F.Callback0() {
//                    public void invoke() {
//                        push.stop();
//                    }
//                });
//                push.through(Enumeratee.map(new Function<IO, FR>() {
//                    @Override
//                    public FR apply(IO s) {
//                        return inBuilder.build(s);
//                    }
//                })).applyOn(inIteratee);
//                outEnumerator.applyOn(send);
//                in.onClose(new play.libs.F.Callback0() {
//                    @Override
//                    public void invoke() throws Throwable {
//
//                    }
//                });
//            }
//        };
//        return ws;
//    }
//
//    public static <IO, FR> WebSocket<IO> websocket(final Class<IO> clazz, final Class<FR> from,
//            final Iteratee<FR, Unit> inIteratee, final Builder<IO, FR> inBuilder,
//            final HubEnumerator<FR> outEnumerator, final Builder<FR, IO> outBuilder) {
//
//        WebSocket<IO> ws = new WebSocket<IO>() {
//            public void onReady(final WebSocket.In<IO> in, final WebSocket.Out<IO> out) {
//                final Iteratee<FR, Unit> send = Iteratee.foreach(new Function<FR, Unit>() {
//                    @Override
//                    public Unit apply(FR s) {
//                        out.write(outBuilder.build(s));
//                        return Unit.unit();
//                    }
//                });
//                final PushEnumerator<IO> push = Enumerator.unicast(clazz);
//                in.onMessage(new play.libs.F.Callback<IO>() {
//                    public void invoke(IO event) {
//                        push.push(event);
//                    }
//                });
//                in.onClose(new play.libs.F.Callback0() {
//                    public void invoke() {
//                        push.stop();
//                    }
//                });
//                push.through(Enumeratee.map(new Function<IO, FR>() {
//                    @Override
//                    public FR apply(IO s) {
//                        return inBuilder.build(s);
//                    }
//                })).applyOn(inIteratee);
//                outEnumerator.add(send);
//            }
//        };
//        return ws;
//    }
}
