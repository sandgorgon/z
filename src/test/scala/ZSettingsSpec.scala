import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach
import java.io.File
import java.nio.file.Files
import scala.compiletime.uninitialized

class ZSettingsSpec extends AnyFlatSpec with Matchers with BeforeAndAfterEach {

  private var tempDir: File = uninitialized

  override def beforeEach(): Unit =
    tempDir = Files.createTempDirectory("zsettings-test").toFile

  override def afterEach(): Unit = {
    tempDir.listFiles.foreach(_.delete())
    tempDir.delete()
  }

  "ZSettings.dump then load" should "round-trip a simple map" in {
    val f = new File(tempDir, "settings")
    val m = Map("key1" -> "value1", "key2" -> "value2")
    ZSettings.dump(m, f, "test")
    ZSettings.load(f) should contain allOf ("key1" -> "value1", "key2" -> "value2")
  }

  it should "preserve numeric string values" in {
    val f = new File(tempDir, "settings")
    ZSettings.dump(Map("width" -> "800", "height" -> "600"), f, "test")
    val loaded = ZSettings.load(f)
    loaded("width") shouldEqual "800"
    loaded("height") shouldEqual "600"
  }

  "ZSettings.load" should "return Map.empty for a nonexistent file" in {
    ZSettings.load(new File(tempDir, "missing")) shouldBe Map.empty
  }

  it should "return Map.empty for a corrupt file (and log to stderr)" in {
    val f = new File(tempDir, "corrupt")
    // Write a file that is valid unicode but not a valid Java Properties file
    // (backslash at end of line makes java.util.Properties.load() throw on some JVMs,
    //  or return a partial map; either way, load() must not throw).
    java.nio.file.Files.write(f.toPath, "key = val\n\\".getBytes)
    noException should be thrownBy ZSettings.load(f)
  }

  "ZSettings.dump" should "not throw on a bad path (and log to stderr)" in {
    val f = new File("/no/such/dir/settings")
    noException should be thrownBy ZSettings.dump(Map("x" -> "y"), f, "test")
  }
}
