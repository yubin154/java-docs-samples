package com.google.cloud.cache.apps.loadtest;

import com.google.common.collect.Range;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.logging.Logger;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Run 'times' of memcache Get requests + 1 Set request. */
public final class MemcachedLoadTestServlet extends HttpServlet {
  private static final Logger logger = Logger.getLogger(MemcachedLoadTestServlet.class.getName());

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    RequestReader reader = RequestReader.create(request);
    final ResponseWriter writer = ResponseWriter.create(response);
    try {
      final Range<Integer> valueSizeRange = reader.readValueSizeRange();
      final int iterationCount = reader.readIterationCount();
      final int durationSec = reader.readDurationSec();
      final int frontendQps = reader.readFrontendQps();

      writer.write(
          String.format(
              "Setup load test iteration=%s, duration=%s, fe_qps=%s\n",
              iterationCount, durationSec, frontendQps));
      List<Future> futures = new ArrayList<>();
      for (int i = 0; i < 10; i++) {
        futures.add(
            ExecutionTracker.getInstance()
                .getExecutorService()
                .submit(
                    new Runnable() {
                      @Override
                      public void run() {
                        try {
                          MemcachedLoadTest loadTester =
                              new MemcachedLoadTest("169.254.10.1", 11211, "1.4.22");
                          loadTester.setUp();
                          loadTester.runTest(
                              valueSizeRange, iterationCount, durationSec, frontendQps / 10);
                          loadTester.tearDown();
                        } catch (Exception e) {
                          logger.severe(e.getMessage());
                        }
                      }
                    }));
      }
      for (int i = 0; i <= durationSec; ++i) {
        Thread.sleep(1000);
        writer.write(String.format("QPS %s\n", ExecutionTracker.getInstance().getAndResetQps()));
        writer.write(
            String.format("Errors %s\n", ExecutionTracker.getInstance().getAndResetErrorCount()));
      }
      for (Future future : futures) {
        future.get();
      }
    } catch (Exception e) {
      throw new IOException(e);
    }
    writer.write("done\n");
    writer.flush();
  }
}
