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

import com.hazelcast.core.*;
import com.hazelcast.instance.MemberImpl;
import com.hazelcast.monitor.LocalExecutorStats;
import com.hazelcast.monitor.impl.LocalExecutorStatsImpl;
import com.hazelcast.spi.AbstractDistributedObject;
import com.hazelcast.spi.Invocation;
import com.hazelcast.spi.NodeEngine;
import com.hazelcast.spi.OperationService;
import com.hazelcast.spi.impl.ResponseHandlerFactory;
import com.hazelcast.util.Clock;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @mdogan 1/17/13
 */
public class ExecutorServiceProxy extends AbstractDistributedObject<DistributedExecutorService> implements IExecutorService {

    private final String name;
    private final Random random = new Random();
    private final int partitionCount;
    private final AtomicInteger consecutiveSubmits = new AtomicInteger();
    private volatile long lastSubmitTime = 0L;

    public ExecutorServiceProxy(String name, NodeEngine nodeEngine, DistributedExecutorService service) {
        super(nodeEngine, service);
        this.name = name;
        this.partitionCount = nodeEngine.getPartitionService().getPartitionCount();
    }

    public void execute(Runnable command) {
        Callable<?> callable = new RunnableAdapter(command);
        submit(callable);
    }

    public void executeOnKeyOwner(Runnable command, Object key) {
        Callable<?> callable = new RunnableAdapter(command);
        submitToKeyOwner(callable, key);
    }

    public void executeOnMember(Runnable command, Member member) {
        Callable<?> callable = new RunnableAdapter(command);
        submitToMember(callable, member);
    }

    public void executeOnMembers(Runnable command, Collection<Member> members) {
        Callable<?> callable = new RunnableAdapter(command);
        submitToMembers(callable, members);
    }

    public void executeOnAllMembers(Runnable command) {
        Callable<?> callable = new RunnableAdapter(command);
        submitToAllMembers(callable);
    }

    public Future<?> submit(Runnable task) {
        Callable<?> callable = new RunnableAdapter(task);
        return submit(callable);
    }

    public <T> Future<T> submit(Runnable task, T result) {
        Callable<T> callable = new RunnableAdapter<T>(task);
        return new FutureProxy<T>(submit(callable), getNodeEngine().getSerializationService(), result);
    }

    public <T> Future<T> submit(Callable<T> task) {
        final int partitionId = getTaskPartitionId(task);
        return submitToPartitionOwner(task, partitionId);
    }

    private <T> Future<T> submitToPartitionOwner(Callable<T> task, int partitionId) {
        if (isShutdown()) {
            throw new RejectedExecutionException(getRejectionMessage());
        }
        final NodeEngine nodeEngine = getNodeEngine();
        Invocation inv = nodeEngine.getOperationService().createInvocationBuilder(DistributedExecutorService.SERVICE_NAME,
                new CallableTaskOperation<T>(name, task), partitionId).build();
        return invoke(inv);
    }

    private <T> Future<T> invoke(Invocation inv) {
        final NodeEngine nodeEngine = getNodeEngine();
        final boolean sync = checkSync();
        final Future future = inv.invoke();
        if (sync) {
            Object response;
            try {
                response = future.get();
            } catch (Exception e) {
                response = e;
            }
            return new FakeFuture<T>(nodeEngine.getSerializationService(), response);
        }
        return new FutureProxy<T>(future, nodeEngine.getSerializationService());
    }

    private boolean checkSync() {
        boolean sync = false;
        final long last = lastSubmitTime;
        final long now = Clock.currentTimeMillis();
        if (last + 10 < now) {
            consecutiveSubmits.set(0);
        } else if (consecutiveSubmits.incrementAndGet() % 100 == 0) {
            sync = true;
        }
        lastSubmitTime = now;
        return sync;
    }

    private <T> int getTaskPartitionId(Callable<T> task) {
        final int partitionId;
        if (task instanceof PartitionAware) {
            final Object partitionKey = ((PartitionAware) task).getPartitionKey();
            partitionId = getNodeEngine().getPartitionService().getPartitionId(partitionKey);
        } else {
            partitionId = random.nextInt(partitionCount);
        }
        return partitionId;
    }

    public <T> Future<T> submitToKeyOwner(Callable<T> task, Object key) {
        final NodeEngine nodeEngine = getNodeEngine();
        return submitToPartitionOwner(task, nodeEngine.getPartitionService().getPartitionId(key));
    }

    public <T> Future<T> submitToMember(Callable<T> task, Member member) {
        if (isShutdown()) {
            throw new RejectedExecutionException(getRejectionMessage());
        }
        final NodeEngine nodeEngine = getNodeEngine();
        Invocation inv = nodeEngine.getOperationService().createInvocationBuilder(DistributedExecutorService.SERVICE_NAME,
                new MemberCallableTaskOperation<T>(name, task), ((MemberImpl) member).getAddress()).build();
        return invoke(inv);
    }

    public <T> Map<Member, Future<T>> submitToMembers(Callable<T> task, Collection<Member> members) {
        final Map<Member, Future<T>> futures = new HashMap<Member, Future<T>>(members.size());
        for (Member member : members) {
            futures.put(member, submitToMember(task, member));
        }
        return futures;
    }

    public <T> Map<Member, Future<T>> submitToAllMembers(Callable<T> task) {
        final NodeEngine nodeEngine = getNodeEngine();
        return submitToMembers(task, nodeEngine.getClusterService().getMembers());
    }

    public void submit(Runnable task, ExecutionCallback callback) {
        Callable<?> callable = new RunnableAdapter(task);
        submit(callable, callback);
    }

    public void submitToKeyOwner(Runnable task, Object key, ExecutionCallback callback) {
        Callable<?> callable = new RunnableAdapter(task);
        submitToKeyOwner(callable, key, callback);
    }

    public void submitToMember(Runnable task, Member member, ExecutionCallback callback) {
        Callable<?> callable = new RunnableAdapter(task);
        submitToMember(callable, member, callback);
    }

    public void submitToMembers(Runnable task, Collection<Member> members, MultiExecutionCallback callback) {
        Callable<?> callable = new RunnableAdapter(task);
        submitToMembers(callable, members, callback);
    }

    public void submitToAllMembers(Runnable task, MultiExecutionCallback callback) {
        Callable<?> callable = new RunnableAdapter(task);
        submitToAllMembers(callable, callback);
    }

    private <T> void submitToPartitionOwner(Callable<T> task, ExecutionCallback<T> callback, int partitionId) {
        if (isShutdown()) {
            throw new RejectedExecutionException(getRejectionMessage());
        }
        final NodeEngine nodeEngine = getNodeEngine();
        Invocation inv = nodeEngine.getOperationService().createInvocationBuilder(DistributedExecutorService.SERVICE_NAME,
                new CallableTaskOperation<T>(name, task), partitionId).build();
        nodeEngine.getAsyncInvocationService().invoke(inv, callback);
    }

    public <T> void submit(Callable<T> task, ExecutionCallback<T> callback) {
        final int partitionId = getTaskPartitionId(task);
        submitToPartitionOwner(task, callback, partitionId);
    }

    public <T> void submitToKeyOwner(Callable<T> task, Object key, ExecutionCallback<T> callback) {
        final NodeEngine nodeEngine = getNodeEngine();
        submitToPartitionOwner(task, callback, nodeEngine.getPartitionService().getPartitionId(key));
    }

    public <T> void submitToMember(Callable<T> task, Member member, ExecutionCallback<T> callback) {
        if (isShutdown()) {
            throw new RejectedExecutionException(getRejectionMessage());
        }
        final NodeEngine nodeEngine = getNodeEngine();
        Invocation inv = nodeEngine.getOperationService().createInvocationBuilder(DistributedExecutorService.SERVICE_NAME,
                new MemberCallableTaskOperation<T>(name, task), ((MemberImpl) member).getAddress()).build();
        nodeEngine.getAsyncInvocationService().invoke(inv, callback);
    }

    private String getRejectionMessage() {
        return "ExecutorService[" + name + "] is shutdown! In order to create a new ExecutorService with name '" +
                name + "', you need to destroy current ExecutorService first!";
    }

    public <T> void submitToMembers(Callable<T> task, Collection<Member> members, MultiExecutionCallback callback) {
        final NodeEngine nodeEngine = getNodeEngine();
        ExecutionCallbackAdapterFactory executionCallbackFactory = new ExecutionCallbackAdapterFactory(nodeEngine,
                members, callback);
        for (Member member : members) {
            submitToMember(task, member, executionCallbackFactory.<T>callbackFor(member));
        }
    }

    public <T> void submitToAllMembers(Callable<T> task, MultiExecutionCallback callback) {
        final NodeEngine nodeEngine = getNodeEngine();
        submitToMembers(task, nodeEngine.getClusterService().getMembers(), callback);
    }

    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        throw new UnsupportedOperationException();
    }

    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        throw new UnsupportedOperationException();
    }

    public boolean isShutdown() {
        return getService().isShutdown(name);
    }

    public boolean isTerminated() {
        return isShutdown();
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return false;
    }

    public void shutdown() {
        final NodeEngine nodeEngine = getNodeEngine();
        final Collection<MemberImpl> members = nodeEngine.getClusterService().getMemberList();
        final OperationService operationService = nodeEngine.getOperationService();
        final Collection<Future> calls = new LinkedList<Future>();
        for (MemberImpl member : members) {
            if (member.localMember()) {
                final ShutdownOperation op = new ShutdownOperation(name);
                op.setServiceName(getServiceName()).setNodeEngine(nodeEngine)
                        .setResponseHandler(ResponseHandlerFactory.createEmptyResponseHandler());
                operationService.runOperation(op);
            } else {
                Future f = operationService.createInvocationBuilder(getServiceName(), new ShutdownOperation(name),
                        member.getAddress()).build().invoke();
                calls.add(f);
            }
        }
        for (Future f : calls) {
            try {
                f.get(1, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            }
        }
    }

    public List<Runnable> shutdownNow() {
        shutdown();
        return null;
    }

    public LocalExecutorStats getLocalExecutorStats() {
        LocalExecutorStatsImpl localExecutorStats = new LocalExecutorStatsImpl();
        ExecutorServiceStatsContainer serviceStatsContainer = getService().getExecutorServiceStatsContainer(name);
        localExecutorStats.setCreationTime(serviceStatsContainer.getCreationTime());
        localExecutorStats.setTotalFinished(serviceStatsContainer.getTotalFinished());
        localExecutorStats.setTotalStarted(serviceStatsContainer.getTotalStarted());
        localExecutorStats.setOperationStats(serviceStatsContainer.getOperationsCounter().getPublishedStats());
        return localExecutorStats;
    }

    public String getServiceName() {
        return DistributedExecutorService.SERVICE_NAME;
    }

    public Object getId() {
        return name;
    }

    public String getName() {
        return name;
    }
}
