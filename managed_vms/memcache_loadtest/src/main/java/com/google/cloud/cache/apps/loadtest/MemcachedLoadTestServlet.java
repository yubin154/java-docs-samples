package com.google.cloud.cache.apps.loadtest;

import com.google.common.collect.Range;
import java.io.IOException;
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
      MemcachedLoadTest loadTester = new MemcachedLoadTest("169.254.10.1", 11211, "1.4.22");
      Range<Integer> valueSizeRange = reader.readValueSizeRange();
      final int iterationCount = reader.readIterationCount();
      final int durationSec = reader.readDurationSec();
      int frontendQps = reader.readFrontendQps();

      writer.write(
          String.format(
              "Setup load test iteration=%s, duration=%s, fe_qps=%s",
              iterationCount, durationSec, frontendQps));
      loadTester.setUp();
      loadTester.runTest(valueSizeRange, iterationCount, durationSec, frontendQps);
      writer.write(loadTester.getResult());
      loadTester.tearDown();
    } catch (Exception e) {
      throw new IOException(e);
    }
    writer.write("done");
    writer.flush();
  }
}
