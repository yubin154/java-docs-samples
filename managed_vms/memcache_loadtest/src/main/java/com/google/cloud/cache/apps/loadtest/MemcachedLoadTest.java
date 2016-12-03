package com.google.cloud.cache.apps.loadtest;

import com.google.common.collect.Range;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Future;

final class MemcachedLoadTest extends SpyMemcachedBaseTest {

  MemcachedLoadTest(String server, int port, String version) {
    super(server, port, version, false);
  }

  void runTest(
      final Range<Integer> valueSizeRange,
      final int iterationCount,
      final int durationSec,
      final int frontendQps)
      throws Exception {
    if (!lease()) {
      return;
    }
    try {
      List<Future> futures = new ArrayList<>();
      for (int i = 0; i < frontendQps; ++i) {
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
                        List<Future> opsFutures = new ArrayList<>();
                        try {
                          do {
                            opsFutures.clear();
                            long start = System.currentTimeMillis();
                            for (int i = 0; i < iterationCount; ++i) {
                              opsFutures.add(client.asyncGet(key));
                              ExecutionTracker.getInstance().incrementQps();
                            }
                            for (Future future : opsFutures) {
                              if (future.get() == null) {
                                ExecutionTracker.getInstance().incrementErrorCount();
                              }
                            }
                            if (duration > 0) {
                              // sleep till 1 second
                              Thread.sleep(
                                  Math.max(1, 1000 - (System.currentTimeMillis() - start)));
                            }
                          } while (duration-- >= 0);
                        } catch (Throwable t) {
                          ExecutionTracker.getInstance().incrementErrorCount();
                        }
                      }
                    }));
      }
      for (int i = 0; i <= durationSec; ++i) {
        try {
          Thread.sleep(1000);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
        result
            .append(String.format("QPS %s\n", ExecutionTracker.getInstance().getAndResetQps()))
            .append("\n");
        result
            .append(
                String.format(
                    "Errors %s\n", ExecutionTracker.getInstance().getAndResetErrorCount()))
            .append("\n");
      }
      for (Future future : futures) {
        future.get();
      }
    } finally {
      release();
    }
  }
}
