package controllers;


import play.mvc.Controller;
import play.mvc.Http;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.CountDownLatch;

import static iteratee.F.*;
import static iteratee.Iteratees.*;

public abstract class JIterateesController extends Controller {

    public static final String EVENTSOURCE = "text/event-stream";

    public static <T> void stream(final File file) {
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
        final play.libs.F.EventStream<byte[]> chunks = new play.libs.F.EventStream<byte[]>();
        enumerator.add(Iteratee.foreach(new UFunction<T>() {
            @Override
            public void invoke(T value) {
                chunks.publish(builder.build(value));
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
            byte[] chunck = Controller.await(chunks.nextEvent());
            Http.Response.current().writeChunk(chunck);
        }
    }

    public static <T> void stream(final Enumerator<T> enumerator, final ByteBuilder<T> builder, final String contentType) {
        final CountDownLatch latch = new CountDownLatch(1);
        final play.libs.F.EventStream<byte[]> chunks = new play.libs.F.EventStream<byte[]>();
        enumerator.applyOn(Iteratee.foreach(new UFunction<T>() {
            @Override
            public void invoke(T value) {
                chunks.publish(builder.build(value));
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
            byte[] chunck = await(chunks.nextEvent());
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
        final play.libs.F.EventStream<byte[]> chunks = new play.libs.F.EventStream<byte[]>();
        enumerator.applyOn(Iteratee.foreach(new UFunction<T>() {
            @Override
            public void invoke(T value) {
                try {
                    chunks.publish(("data: " + builder.build(value) + "\n\n").getBytes("UTF-8"));
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
            byte[] chunck = await(chunks.nextEvent());
            response.writeChunk(chunck);
        }
    }

    protected static <T> void eventSource(final HubEnumerator<T> enumerator, final StrBuilder<T> builder) {
        final CountDownLatch latch = new CountDownLatch(1);
        final play.libs.F.EventStream<byte[]> chunks = new play.libs.F.EventStream<byte[]>();
        enumerator.add(Iteratee.foreach(new UFunction<T>() {
            @Override
            public void invoke(T value) {
                try {
                    chunks.publish(("data: " + builder.build(value) + "\n\n").getBytes("UTF-8"));
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
            byte[] chunck = await(chunks.nextEvent());
            response.writeChunk(chunck);
        }
    }

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


    protected static <T> void comet(final String callback, final HubEnumerator<T> enumerator, final StrBuilder<T> builder) {
        final CountDownLatch latch = new CountDownLatch(1);
        final play.libs.F.EventStream<byte[]> chunks = new play.libs.F.EventStream<byte[]>();
        enumerator.add(Iteratee.foreach(new UFunction<T>() {
            @Override
            public void invoke(T value) {
                try {
                    chunks.publish(( buildJSMessage(callback, builder.build(value)) ).getBytes("UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
        }));/**.onRedeem(new Action<Promise<Unit>>() {
            @Override
            public void apply(Promise<Unit> unitPromise) {
                latch.countDown();
            }
        }); **/
        response.setHeader("Content-Length", "-1");
        response.contentType = "text/html";
        while(latch.getCount() > 0) {
            byte[] chunck = await(chunks.nextEvent());
            response.writeChunk(chunck);
        }
    }

    protected static <T> void comet(final String callback, final Enumerator<T> enumerator, final StrBuilder<T> builder) {
        final CountDownLatch latch = new CountDownLatch(1);
        final play.libs.F.EventStream<byte[]> chunks = new play.libs.F.EventStream<byte[]>();
        enumerator.applyOn(Iteratee.foreach(new UFunction<T>() {
            @Override
            public void invoke(T value) {
                try {
                    chunks.publish(( buildJSMessage(callback, builder.build(value)) ).getBytes("UTF-8"));
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
        response.contentType = "text/html";
        while(latch.getCount() > 0) {
            byte[] chunck = await(chunks.nextEvent());
            response.writeChunk(chunck);
        }
    }

    protected static <T> void comet(String callback, final Enumerator<T> enumerator) {
        comet(callback, enumerator, new StrBuilder<T>() {
            @Override
            public String build(T value) {
                return value.toString();
            }
        });
    }

    protected static <T> void comet(String callback, final HubEnumerator<T> enumerator) {
        comet(callback, enumerator, new StrBuilder<T>() {
            @Override
            public String build(T value) {
                return value.toString();
            }
        });
    }

    private static String buildJSMessage(String callback, String data) {
        // TODO : fix that !!!!
        return "<script type=\"text/javascript\">" + callback + "(" + data + ");</script>";
    }
}