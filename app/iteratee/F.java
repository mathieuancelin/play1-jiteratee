/*
 *  Copyright 2011-2012 Mathieu ANCELIN
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  under the License.
 */

package iteratee;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.*;

public final class F { 

    final static None<Object> none = new None<Object>();

    private F() {}

    public static class Unit {
        private static final Unit instance = new Unit();
        private Unit() {}
        public static Unit unit() { return instance; }
    }

    public static abstract class UFunction<T> implements Function<T, Unit> {
        public abstract void invoke(T value);
        public Unit apply(T value) {
            invoke(value);
            return Unit.unit();
        }
    }
        
    public static interface Action<T> {
        void apply(T t);
    }

    public static interface Function<T, R> {
        R apply(T t);
    }

    public static interface Monad<T> {
        <R> Option<R> map(Function<T, R> function);
        Option<T> flatMap(Function<T, Option<T>> action);
    }

    public static abstract class Option<T> implements Iterable<T>, Monad<T>, Serializable {
        
        public abstract boolean isDefined();

        public abstract boolean isEmpty();
        
        public abstract T get();

        public Option<T> orElse(T value) {
            return isEmpty() ? Option.apply(value) : this;
        }
        
        public T getOrElse(T value) {
            return isEmpty() ? value : get();
        }

        public T getOrElse(Function<Unit, T> function) {
            return isEmpty() ? function.apply(Unit.unit()) : get();
        }

        public T getOrNull() {
            return isEmpty() ? null : get();
        }
        
        public Option<T> filter(Function<T, Boolean> predicate) {
            if (isDefined()) {
                if (predicate.apply(get())) {
                    return this;
                } else {
                    return Option.none();
                }
            }
            return Option.none();
        }
        
        public Option<T> filterNot(Function<T, Boolean> predicate) {
            if (isDefined()) {
                if (!predicate.apply(get())) {
                    return this;
                } else {
                    return Option.none();
                }
            }
            return Option.none();
        }

        @Override
        public <R> Option<R> map(Function<T, R> function) {
            if (isDefined()) {
                return Option.apply(function.apply(get()));
            }
            return Option.none();
        }
        
        @Override
        public Option<T> flatMap(Function<T, Option<T>> action) {
            if (isDefined()) {
                return action.apply(get());
            }
            return Option.none();
        }

        public static <T> None<T> none() {
            return (None<T>) (Object) none;
        }

        public static <T> Some<T> some(T value) {
            return new Some<T>(value);
        }
        
        public static <T> Option<T> apply(T value) {
            if (value == null) {
                return Option.none();
            } else {
                return Option.some(value);
            }
        }
        
        public static <T> Option<T> unit(T value) {
            return apply(value);
        }
    }

    public static class None<T> extends Option<T> {

        @Override
        public boolean isDefined() {
            return false;
        }

        @Override
        public T get() {
            throw new IllegalStateException("No value");
        }

        @Override
        public Iterator<T> iterator() {
            return Collections.<T>emptyList().iterator();
        }

        @Override
        public String toString() {
            return "None";
        }

        @Override
        public boolean isEmpty() {
            return true;
        }
    }

    public static class Some<T> extends Option<T> {

        final T value;

        public Some(T value) {
            this.value = value;
        }

        @Override
        public boolean isDefined() {
            return true;
        }

        @Override
        public T get() {
            return value;
        }

        @Override
        public Iterator<T> iterator() {
            return Collections.singletonList(value).iterator();
        }

        @Override
        public String toString() {
            return "Some ( " + value + " )";
        }

        @Override
        public boolean isEmpty() {
            return false;
        }
    }

    public static class Tuple<A, B> implements Serializable {

        final public A _1;
        final public B _2;

        public Tuple(A _1, B _2) {
            this._1 = _1;
            this._2 = _2;
        }

        public Tuple<B, A> swap() {
            return new Tuple<B, A>(_2, _1);
        }

        @Override
        public String toString() {
            return "Tuple ( _1: " + _1 + ", _2: " + _2 + " )";
        }
    }
    
    public static class Promise<V> implements Future<V>, F.Action<V> {

        private final CountDownLatch taskLock = new CountDownLatch(1);

        private boolean cancelled = false;

        private List<Action<Promise<V>>> callbacks = new ArrayList<Action<Promise<V>>>();

        private boolean invoked = false;

        private V result = null;

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return cancelled;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return invoked;
        }

        public V getOrNull() {
            return result;
        }

        @Override
        public V get() throws InterruptedException, ExecutionException {
            taskLock.await();
            return result;
        }

        @Override
        public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            taskLock.await(timeout, unit);
            return result;
        }

        @Override
        public void apply(V result) {
            synchronized (this) {
                if (!invoked) {
                    invoked = true;
                    this.result = result;
                    taskLock.countDown();
                } else {
                    return;
                }
            }
            for (F.Action<Promise<V>> callback : callbacks) {
                callback.apply(this);
            }
        }

        public void onRedeem(F.Action<Promise<V>> callback) {
            synchronized (this) {
                if (!invoked) {
                    callbacks.add(callback);
                }
            }
            if (invoked) {
                callback.apply(this);
            }
        }

        public void await() throws InterruptedException {
            taskLock.await();
        }

        public void await(long l, TimeUnit t) throws InterruptedException {
            taskLock.await(l, t);
        }

        public <B> Promise<B> map(final Function<V, B> map) {
            final Promise<B> promise = new Promise<B>();
            this.onRedeem(new F.Action<Promise<V>>() {
                @Override
                public void apply(Promise<V> t) {
                    try {
                        promise.apply(map.apply(t.get()));
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            });
            return promise;
        }

        public Promise<V> filter(final Function<V, Boolean> predicate) {
            final Promise<V> promise = new Promise<V>();
            this.onRedeem(new F.Action<Promise<V>>() {
                @Override
                public void apply(Promise<V> t) {
                    try {
                        if (predicate.apply(t.get())) {
                            promise.apply(t.get());
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            });
            return promise;
        }

        public Promise<V> filterNot(final Function<V, Boolean> predicate) {
            final Promise<V> promise = new Promise<V>();
            this.onRedeem(new F.Action<Promise<V>>() {
                @Override
                public void apply(Promise<V> t) {
                    try {
                        if (!predicate.apply(t.get())) {
                            promise.apply(t.get());
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            });
            return promise;
        }

        public <B> Promise<B> flatMap(final Function<V, Promise<B>> map) {
            final Promise<B> promise = new Promise<B>();
            this.onRedeem(new F.Action<Promise<V>>() {
                @Override
                public void apply(Promise<V> t) {
                    try {
                        promise.apply(map.apply(t.get()).get());
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            });
            return promise;
        }
        public static <T> Promise<List<T>> waitAll(final Promise<T>... promises) {
            return waitAll(Arrays.asList(promises));
        }

        public static <T> Promise<List<T>> waitAll(final Collection<Promise<T>> promises) {
            final CountDownLatch waitAllLock = new CountDownLatch(promises.size());
            final Promise<List<T>> result = new Promise<List<T>>() {

                @Override
                public boolean cancel(boolean mayInterruptIfRunning) {
                    boolean r = true;
                    for (Promise<T> f : promises) {
                        r = r & f.cancel(mayInterruptIfRunning);
                    }
                    return r;
                }

                @Override
                public boolean isCancelled() {
                    boolean r = true;
                    for (Promise<T> f : promises) {
                        r = r & f.isCancelled();
                    }
                    return r;
                }

                @Override
                public boolean isDone() {
                    boolean r = true;
                    for (Promise<T> f : promises) {
                        r = r & f.isDone();
                    }
                    return r;
                }

                @Override
                public List<T> get() throws InterruptedException, ExecutionException {
                    waitAllLock.await();
                    List<T> r = new ArrayList<T>();
                    for (Promise<T> f : promises) {
                        r.add(f.get());
                    }
                    return r;
                }

                @Override
                public List<T> get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                    waitAllLock.await(timeout, unit);
                    return get();
                }
            };
            final F.Action<Promise<T>> action = new F.Action<Promise<T>>() {
                @Override
                public void apply(Promise<T> completed) {
                    waitAllLock.countDown();
                    if (waitAllLock.getCount() == 0) {
                        try {
                            result.apply(result.get());
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            };
            for (Promise<T> f : promises) {
                f.onRedeem(action);
            }
            return result;
        }

        public static <T> Promise<T> waitAny(final Promise<T>... futures) {
            final Promise<T> result = new Promise<T>();
            final F.Action<Promise<T>> action = new F.Action<Promise<T>>() {
                @Override
                public void apply(Promise<T> completed) {
                    synchronized (this) {
                        if (result.isDone()) {
                            return;
                        }
                    }
                    result.apply(completed.getOrNull());
                }
            };
            for (Promise<T> f : futures) {
                f.onRedeem(action);
            }
            return result;
        }
        
        public static <T> Promise<T> pure(T t) {
            Promise<T> promise = new Promise<T>();
            promise.apply(t);
            return promise;
        }
    }
    
    public static <K> Option<K> caseClassOf(final Class<K> clazz, Object o) {
        if (clazz.isInstance(o)) {
            return Option.some(clazz.cast(o));
        }
        return Option.none();
    }

    public static <K> Option<K> caseObjEquals(Object a, K b) {
        if (a.equals(b)) {
            return Option.some(b);
        }
        return Option.none();
    }

    public static Option<String> caseStringEquals(Object o, String value) {
        for (String s : caseClassOf(String.class, o)) {
            if (s.equals(value)) {
                return Option.some(s);
            }
        }
        return Option.none();
    }

    public static Option<String> caseStringMatch(Object o, String regex) {
        for (String s : caseClassOf(String.class, o)) {
            if (s.matches(regex)) {
                return Option.some(s);
            }
        }
        return Option.none();
    }

    public static Option<String> caseStringContains(Object o, String value) {
        for (String s : caseClassOf(String.class, o)) {
            if (s.contains(value)) {
                return Option.some(s);
            }
        }
        return Option.none();
    }

    public static Option<String> caseStringStartWith(Object o, String value) {
        for (String s : caseClassOf(String.class, o)) {
            if (s.startsWith(value)) {
                return Option.some(s);
            }
        }
        return Option.none();
    }
}