package services;

import static org.junit.jupiter.api.Assertions.*;

import edu.njit.jerse.ashe.services.JarMethodCountService;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MethodCountTest {
  @Test
  void works() throws Exception {
    // Creating two Java files in the temporary directory
    // Path commons = Path.of("src/test/resources/commons-io-2.18.0.jar");
    Path netty = Path.of("src/test/resources/netty-all-5.0.0.Alpha2.jar");
    Path spark = Path.of("src/test/resources/spark-core_2.13-4.0.0-preview2.jar");
    // Path zookeeper = Path.of("src/test/resources/zookeeper-3.9.3.jar");
    try {

      JarMethodCountService.compare(netty, spark);
    } catch (Exception e) {
      e.printStackTrace(System.out);
    }
  }

  @Test
  void do100() throws Exception {
    // Creating two Java files in the temporary directory
    Path src = Path.of("top100");
    Path dest = Path.of("top100");
    Path output = Path.of("top100_results.txt");
    try {

      var epList = JarMethodCountService.getEntrypoints(src, dest);
      Files.writeString(output, String.join("\n", epList));
    } catch (Exception e) {
      e.printStackTrace(System.out);
    }
  }
}
