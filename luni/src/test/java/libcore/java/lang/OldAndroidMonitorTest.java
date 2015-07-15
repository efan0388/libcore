/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package libcore.java.lang;

import junit.framework.TestCase;

import java.util.concurrent.CountDownLatch;

public class OldAndroidMonitorTest extends TestCase {

    public void testWaitArgumentsTest() throws Exception {
            /* Try some valid arguments.  These should all
             * return very quickly.
             */
            try {
                synchronized (this) {
                    /* millisecond version */
                    wait(1);
                    wait(10);

                    /* millisecond + nanosecond version */
                    wait(0, 1);
                    wait(0, 999999);
                    wait(1, 1);
                    wait(1, 999999);
                }
            } catch (InterruptedException ex) {
                throw new RuntimeException("good Object.wait() interrupted",
                        ex);
            } catch (Exception ex) {
                throw new RuntimeException("Unexpected exception when calling" +
                        "Object.wait() with good arguments", ex);
            }

            /* Try some invalid arguments.
             */
            boolean sawException = false;
            try {
                synchronized (this) {
                    wait(-1);
                }
            } catch (InterruptedException ex) {
                throw new RuntimeException("bad Object.wait() interrupted", ex);
            } catch (IllegalArgumentException ex) {
                sawException = true;
            } catch (Exception ex) {
                throw new RuntimeException("Unexpected exception when calling" +
                        "Object.wait() with bad arguments", ex);
            }
            if (!sawException) {
                throw new RuntimeException("bad call to Object.wait() should " +
                        "have thrown IllegalArgumentException");
            }

            sawException = false;
            try {
                synchronized (this) {
                    wait(0, -1);
                }
            } catch (InterruptedException ex) {
                throw new RuntimeException("bad Object.wait() interrupted", ex);
            } catch (IllegalArgumentException ex) {
                sawException = true;
            } catch (Exception ex) {
                throw new RuntimeException("Unexpected exception when calling" +
                        "Object.wait() with bad arguments", ex);
            }
            if (!sawException) {
                throw new RuntimeException("bad call to Object.wait() should " +
                        "have thrown IllegalArgumentException");
            }

            sawException = false;
            try {
                synchronized (this) {
                    /* The legal range of nanos is 0-999999. */
                    wait(0, 1000000);
                }
            } catch (InterruptedException ex) {
                throw new RuntimeException("bad Object.wait() interrupted", ex);
            } catch (IllegalArgumentException ex) {
                sawException = true;
            } catch (Exception ex) {
                throw new RuntimeException("Unexpected exception when calling" +
                        "Object.wait() with bad arguments", ex);
            }
            if (!sawException) {
                throw new RuntimeException("bad call to Object.wait() should " +
                        "have thrown IllegalArgumentException");
            }
    }

    /**
     * A thread that blocks forever on {@code wait()} until it's interrupted.
     */
    static class Waiter extends Thread {
        private final Object lock;
        private final CountDownLatch cdl;
        private boolean wasInterrupted;

        public Waiter(Object lock, CountDownLatch cdl) {
            this.lock = lock;
            this.cdl = cdl;
            wasInterrupted = false;
        }

        @Override
        public void run() {
            synchronized (lock) {
                try {
                    cdl.countDown();
                    while (true) {
                        lock.wait();
                    }
                } catch (InterruptedException ex) {
                    wasInterrupted = true;
                }
            }
        }

        public boolean wasInterrupted() {
            synchronized (lock) {
                return wasInterrupted;
            }
        }
    }

    public void testInterrupt() throws Exception {
        final Object lock = new Object();
        final CountDownLatch cdl = new CountDownLatch(1);
        final Waiter waiter = new Waiter(lock, cdl);

        waiter.start();

        // Wait for the "waiter" to start and acquire |lock| for the first time.
        try {
            cdl.await();
        } catch (InterruptedException ie) {
            fail();
        }

        // Interrupt |waiter| after we acquire |lock|. This ensures that |waiter| is
        // currently blocked on a call to "wait".
        synchronized (lock) {
            waiter.interrupt();
        }

        // Wait for the waiter to complete.
        try {
            waiter.join();
        } catch (InterruptedException ie) {
            fail();
        }

        // Assert than an InterruptedException was thrown.
        assertTrue(waiter.wasInterrupted());
    }

     private static void deepWait(int depth, Object lock) {
            synchronized (lock) {
                if (depth > 0) {
                    deepWait(depth - 1, lock);
                } else {
                    String threadName = Thread.currentThread().getName();
                    try {
                        // System.out.println(threadName + " waiting");
                        lock.wait();
                        // System.out.println(threadName + " done waiting");
                    } catch (InterruptedException ex) {
                        // System.out.println(threadName + " interrupted.");
                    }
                }
            }
        }

        private class Worker extends Thread {
            Object lock;
            int id;

            Worker(int id, Object lock) {
                super("Worker(" + id + ")");
                this.id = id;
                this.lock = lock;
            }

            public void run() {
                int iterations = 0;

                while (OldAndroidMonitorTest.running) {
                    OldAndroidMonitorTest.deepWait(id, lock);
                    iterations++;
                }
                // System.out.println(getName() + " done after " + iterations + " iterations.");
            }
        }

    private static Object commonLock = new Object();
        private static Boolean running = false;


    public void testNestedMonitors() throws Exception {
        final int NUM_WORKERS = 5;

            Worker w[] = new Worker[NUM_WORKERS];
            int i;

            for (i = 0; i < NUM_WORKERS; i++) {
                w[i] = new Worker(i * 2 - 1, new Object());
            }

            running = true;

            // System.out.println("NestedMonitors: starting workers");
            for (i = 0; i < NUM_WORKERS; i++) {
                w[i].start();
            }

            try {
                Thread.currentThread().sleep(1000);
            } catch (InterruptedException ex) {
               // System.out.println("Test sleep interrupted.");
            }

            for (i = 0; i < 100; i++) {
                for (int j = 0; j < NUM_WORKERS; j++) {
                    synchronized (w[j].lock) {
                        w[j].lock.notify();
                    }
                }
            }

            // System.out.println("NesterMonitors: stopping workers");
            running = false;
            for (i = 0; i < NUM_WORKERS; i++) {
                synchronized (w[i].lock) {
                    w[i].lock.notifyAll();
                }
            }
    }

    private static class CompareAndExchange extends Thread {
        static Object toggleLock = null;
        static int toggle = -1;
        static Boolean running = false;

        public void run() {
            toggleLock = new Object();
            toggle = -1;

            Worker w1 = new Worker(0, 1);
            Worker w2 = new Worker(2, 3);
            Worker w3 = new Worker(4, 5);
            Worker w4 = new Worker(6, 7);

            running = true;

            // System.out.println("CompareAndExchange: starting workers");

            w1.start();
            w2.start();
            w3.start();
            w4.start();

            try {
                this.sleep(10000);
            } catch (InterruptedException ex) {
                // System.out.println(getName() + " interrupted.");
            }

            // System.out.println("MonitorTest: stopping workers");
            running = false;

            toggleLock = null;
        }

        class Worker extends Thread {
            int i1;
            int i2;

            Worker(int i1, int i2) {
                super("Worker(" + i1 + ", " + i2 + ")");
                this.i1 = i1;
                this.i2 = i2;
            }

            public void run() {
                int iterations = 0;

                /* Latch this because run() may set the static field to
                 * null at some point.
                 */
                Object toggleLock = CompareAndExchange.toggleLock;

                // System.out.println(getName() + " running");
                try {
                    while (CompareAndExchange.running) {
                        synchronized (toggleLock) {
                            int test;
                            int check;

                            if (CompareAndExchange.toggle == i1) {
                                this.sleep(5 + i2);
                                CompareAndExchange.toggle = test = i2;
                            } else {
                                this.sleep(5 + i1);
                                CompareAndExchange.toggle = test = i1;
                            }
                            if ((check = CompareAndExchange.toggle) != test) {
//                                System.out.println("Worker(" + i1 + ", " +
//                                                   i2 + ") " + "test " + test +
//                                                   " != toggle " + check);
                                throw new RuntimeException(
                                        "locked value changed");
                            }
                        }

                        iterations++;
                    }
                } catch (InterruptedException ex) {
                   // System.out.println(getName() + " interrupted.");
                }

//                System.out.println(getName() + " done after " +
//                                   iterations + " iterations.");
            }
        }
    }
}
