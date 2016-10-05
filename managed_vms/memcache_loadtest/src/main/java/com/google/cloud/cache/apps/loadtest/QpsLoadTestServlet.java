package com.google.cloud.cache.apps.loadtest;

import com.google.appengine.api.memcache.AsyncMemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
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

/** Run 'times' of memcache Get requests + 1 Set request. */
public final class QpsLoadTestServlet extends HttpServlet {
  private static final Logger logger = Logger.getLogger(GetServlet.class.getName());

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    RequestReader reader = RequestReader.create(request);
    final ResponseWriter writer = ResponseWriter.create(response);
    final AsyncMemcacheService memcache = MemcacheServiceFactory.getAsyncMemcacheService();

    final String key = UUID.randomUUID().toString();
    Range<Integer> valueSizeRange = reader.readValueSizeRange();
    final Object value = MemcacheValues.random(valueSizeRange);
    final int iterationCount = reader.readIterationCount();
    final int durationSec = reader.readDurationSec();
    int frontendQps = reader.readFrontendQps();
    List<Future> futures = new ArrayList<>();
    for (int i = 0; i < frontendQps; ++i) {
      futures.add(
          ExecutionTracker.getInstance()
              .getExecutorService()
              .submit(
                  new Runnable() {
                    @Override
                    public void run() {
                      int duration = durationSec;
                      long start = System.currentTimeMillis();
                      try {
                        do {
                          memcache.put(key, value);
                          ExecutionTracker.getInstance().incrementQps();
                          for (int i = 0; i < iterationCount; ++i) {
                            Object value = memcache.get(key);
                            if (value == null) {
                              ExecutionTracker.getInstance().incrementErrorCount();
                            }
                            ExecutionTracker.getInstance().incrementQps();
                          }
                          if (duration > 0) {
                            // sleep till 1 second
                            Thread.sleep(1000 - (System.currentTimeMillis() - start));
                          }
                        } while (duration-- >= 0);
                      } catch (Throwable t) {
                        ExecutionTracker.getInstance().incrementErrorCount();
                      }
                    }
                  }));
    }
    for (int i = 0; i <= durationSec; ++i) {
      writer.write(
          String.format("memcache QPS %s", ExecutionTracker.getInstance().getAndResetQps()));
      writer.write(
          String.format(
              "memcache Errors %s", ExecutionTracker.getInstance().getAndResetErrorCount()));
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
