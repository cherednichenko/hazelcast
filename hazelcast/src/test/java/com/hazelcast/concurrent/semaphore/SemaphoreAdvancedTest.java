package com.hazelcast.concurrent.semaphore;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.ISemaphore;
import com.hazelcast.test.HazelcastParallelClassRunner;
import com.hazelcast.test.HazelcastTestSupport;
import com.hazelcast.test.TestHazelcastInstanceFactory;
import com.hazelcast.test.annotation.QuickTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(HazelcastParallelClassRunner.class)
@Category(QuickTest.class)
public class SemaphoreAdvancedTest extends HazelcastTestSupport {

    @Test(expected = IllegalStateException.class, timeout = 30000)
    public void testAcquire_whenInstanceShutdown() throws InterruptedException {
        HazelcastInstance hz = createHazelcastInstance();
        final ISemaphore semaphore = hz.getSemaphore(randomString());
        hz.shutdown();
        semaphore.acquire();
    }


    @Test(timeout = 300000)
    public void testSemaphoreWithFailures() throws InterruptedException {
        final String semaphoreName = randomString();
        final int k = 4;
        final TestHazelcastInstanceFactory factory = createHazelcastInstanceFactory(k + 1);
        final HazelcastInstance[] instances = factory.newInstances();
        final ISemaphore semaphore = instances[k].getSemaphore(semaphoreName);
        int initialPermits = 20;

        semaphore.init(initialPermits);

        for (int i = 0; i < k; i++) {
            int rand = (int) (Math.random() * 5) + 1;

            semaphore.acquire(rand);
            initialPermits -= rand;
            assertEquals(initialPermits, semaphore.availablePermits());
            semaphore.release(rand);
            initialPermits += rand;
            assertEquals(initialPermits, semaphore.availablePermits());

            instances[i].shutdown();

            semaphore.acquire(rand);
            initialPermits -= rand;
            assertEquals(initialPermits, semaphore.availablePermits());
            semaphore.release(rand);
            initialPermits += rand;
            assertEquals(initialPermits, semaphore.availablePermits());
        }
    }

    @Test(timeout = 300000)
    public void testSemaphoreWithFailuresAndJoin() {
        final String semaphoreName = randomString();
        final TestHazelcastInstanceFactory factory = createHazelcastInstanceFactory(3);
        final HazelcastInstance instance1 = factory.newHazelcastInstance();
        final HazelcastInstance instance2 = factory.newHazelcastInstance();
        final ISemaphore semaphore = instance1.getSemaphore(semaphoreName);
        final CountDownLatch countDownLatch = new CountDownLatch(1);

        assertTrue(semaphore.init(0));

        final Thread thread = new Thread() {
            public void run() {
                for (int i = 0; i < 2; i++) {
                    try {
                        semaphore.acquire();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                countDownLatch.countDown();
            }
        };
        thread.start();

        instance2.shutdown();
        semaphore.release();

        HazelcastInstance instance3 = factory.newHazelcastInstance();
        ISemaphore semaphore1 = instance3.getSemaphore(semaphoreName);
        semaphore1.release();

        try {
            assertTrue(countDownLatch.await(15, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            thread.interrupt();
        }
    }

    @Test(timeout = 300000)
    public void testMutex() throws InterruptedException {
        final String semaphoreName = randomString();
        final int threadCount = 2;
        final HazelcastInstance[] instances = createHazelcastInstanceFactory(threadCount).newInstances();
        final CountDownLatch latch = new CountDownLatch(threadCount);
        final int loopCount = 1000;

        class Counter {
            int count = 0;

            void inc() {
                count++;
            }

            int get() {
                return count;
            }
        }
        final Counter counter = new Counter();

        assertTrue(instances[0].getSemaphore(semaphoreName).init(1));

        for (int i = 0; i < threadCount; i++) {
            final ISemaphore semaphore = instances[i].getSemaphore(semaphoreName);
            new Thread() {
                public void run() {
                    for (int j = 0; j < loopCount; j++) {
                        try {
                            semaphore.acquire();
                            sleepMillis((int) (Math.random() * 3));
                            counter.inc();
                        } catch (InterruptedException e) {
                            return;
                        } finally {
                            semaphore.release();
                        }
                    }
                    latch.countDown();
                }
            }.start();
        }

        assertOpenEventually(latch);
        assertEquals(loopCount * threadCount, counter.get());
    }
}
