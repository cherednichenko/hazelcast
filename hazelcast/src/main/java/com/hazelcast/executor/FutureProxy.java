/*
 * Copyright (c) 2008-2013, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.executor;

import com.hazelcast.nio.serialization.Data;
import com.hazelcast.nio.serialization.SerializationService;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @mdogan 1/18/13
 */
public final class FutureProxy<V> implements Future<V> {

    private final Future future;
    private final SerializationService serializationService;
    private final V value;
    private final boolean hasValue;
    private volatile boolean done = false;

    public FutureProxy(Future future, SerializationService serializationService) {
        this.future = future;
        this.serializationService = serializationService;
        this.value = null;
        this.hasValue = false;
    }

    public FutureProxy(Future future, SerializationService serializationService, V value) {
        this.future = future;
        this.value = value;
        this.serializationService = serializationService;
        this.hasValue = true;
    }

    public V get() throws InterruptedException, ExecutionException {
        final Object object = future.get();
        return getResult(object);
    }

    public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        final Object object = future.get(timeout, unit);
        return getResult(object);
    }

    private V getResult(Object object) {
        if (hasValue) {
            return value;
        }
        if (object instanceof Data) {
            object = serializationService.toObject((Data) object);
        }
        return (V) object;
    }

    public boolean cancel(boolean mayInterruptIfRunning) {
        done = true;
        return false;
    }

    public boolean isCancelled() {
        return false;
    }

    public boolean isDone() {
        return done;
    }
}
