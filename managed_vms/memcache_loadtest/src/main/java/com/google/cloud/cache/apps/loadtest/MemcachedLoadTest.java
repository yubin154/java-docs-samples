package com.google.cloud.cache.apps.loadtest;

import com.google.common.collect.Range;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Future;

final class MemcachedLoadTest extends SpyMemcachedBaseTest {

  private boolean stop = false;

  MemcachedLoadTest(String server, int port, String version) {
    super(server, port, version, false);
  }

  void stopTest() {
    this.stop = true;
  }

  private boolean stopped() {
    return stop;
  }

  void startTest(
      final Range<Integer> valueSizeRange,
      final int iterationCount,
      final int durationSec,
      final int numOfThreads)
      throws Exception {
    this.setUp();
    List<Future> futures = new ArrayList<>();
    for (int i = 0; i < numOfThreads; ++i) {
      final String key = UUID.randomUUID().toString();
      final Object value = MemcacheValues.random(valueSizeRange);
      client.set(key, 0, value).get();
      ExecutionTracker.getInstance().incrementQps();
      futures.add(
          ExecutionTracker.getInstance()
              .getExecutorService()
              .submit(
                  new Runnable() {
                    @Override
                    public void run() {
                      int duration = durationSec;
                      try {
                        while (!stopped()) {
                          long start = System.nanoTime();
                          if (client.get(key) != null) {
                            ExecutionTracker.getInstance().incrementQps();
                          } else {
                            ExecutionTracker.getInstance().incrementErrorCount();
                          }
                          LatencyTracker.getInstance().recordLatency(System.nanoTime() - start);
                        }
                      } catch (Throwable t) {
                        ExecutionTracker.getInstance().incrementErrorCount();
                      }
                    }
                  }));
    }
    for (Future future : futures) {
      future.get();
    }
    this.tearDown();
  }
}
