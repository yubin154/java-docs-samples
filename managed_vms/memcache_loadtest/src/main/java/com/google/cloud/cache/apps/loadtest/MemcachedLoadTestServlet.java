package com.google.cloud.cache.apps.loadtest;

import com.google.common.collect.Range;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.logging.Logger;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.InetSocketAddress;
import net.spy.memcached.ConnectionFactoryBuilder;
import net.spy.memcached.ConnectionFactoryBuilder.Protocol;
import net.spy.memcached.MemcachedClient;

/** Run 'times' of memcache Get requests + 1 Set request. */
public final class MemcachedLoadTestServlet extends HttpServlet {
  private static final Logger logger = Logger.getLogger(MemcachedLoadTestServlet.class.getName());

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    RequestReader reader = RequestReader.create(request);
    final ResponseWriter writer = ResponseWriter.create(response);
  
    Range<Integer> valueSizeRange = reader.readValueSizeRange();
    final int iterationCount = reader.readIterationCount();
    final int durationSec = reader.readDurationSec();
    int frontendQps = reader.readFrontendQps();
    List<Future> futures = new ArrayList<>();
    for (int i = 0; i < frontendQps; ++i) {
      final String key = UUID.randomUUID().toString();
      final Object value = MemcacheValues.random(valueSizeRange);
      memcache.put(key, value);
      ExecutionTracker.getInstance().incrementQps();
      futures.add(
          ExecutionTracker.getInstance()
              .getExecutorService()
              .submit(
                  new Runnable() {
                    @Override
                    public void run() {
                      int duration = durationSec;
                      List<Future> futures = new ArrayList<>();
                      try {
                        do {
                          futures.clear();
                          long start = System.currentTimeMillis();
                          for (int i = 0; i < iterationCount; ++i) {
                            futures.add(memcache.get(key));
                            ExecutionTracker.getInstance().incrementQps();
                          }
                          for (Future future : futures) {
                            if (future.get() == null) {
                              ExecutionTracker.getInstance().incrementErrorCount();
                            }
                          }
                          if (duration > 0) {
                            // sleep till 1 second
                            Thread.sleep(Math.max(1, 1000 - (System.currentTimeMillis() - start)));
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
      writer.write(String.format("QPS %s\n", ExecutionTracker.getInstance().getAndResetQps()));
      writer.write(
          String.format("Errors %s\n", ExecutionTracker.getInstance().getAndResetErrorCount()));
      writer.flush();
    }
    for (Future future : futures) {
      try {
        future.get();
      } catch (Exception e) {
        writer.write(e.getMessage());
      }
    }
    writer.write("done");
  }
}
