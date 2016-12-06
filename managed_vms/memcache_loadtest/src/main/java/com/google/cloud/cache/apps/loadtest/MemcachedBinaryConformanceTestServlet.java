package com.google.cloud.cache.apps.loadtest;

import java.io.IOException;
import java.util.logging.Logger;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Run 'times' of memcache Get requests + 1 Set request. */
public final class MemcachedBinaryConformanceTestServlet extends HttpServlet {
  private static final Logger logger =
      Logger.getLogger(MemcachedBinaryConformanceTestServlet.class.getName());

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    RequestReader reader = RequestReader.create(request);
    final ResponseWriter writer = ResponseWriter.create(response);
    try {
      SpyMemcachedBinaryTestStandalone binaryTester =
          new SpyMemcachedBinaryTestStandalone("169.254.10.1", 11211, "1.4.22");
      writer.write("Setup Binary test\n");
      binaryTester.setUp();
      binaryTester.runAllTests();
      writer.write(binaryTester.getResult());
      writer.write(
          String.format("Binary test %s\n", binaryTester.isTestPassed() ? "PASS" : "FAIL"));
      binaryTester.tearDown();
    } catch (Exception e) {
      throw new IOException(e);
    }
    writer.write("done\n");
    writer.flush();
  }
}
