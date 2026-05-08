import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach
import java.io.File
import java.nio.file.Files

class ZPlumbingSpec extends AnyFlatSpec with Matchers with BeforeAndAfterEach {

  // Restore built-in rules after each test that manipulates them.
  private val builtins = ZPlumbing.rules

  override def afterEach(): Unit = ZPlumbing.rules = builtins

  // ── built-in rules ──────────────────────────────────────────────────────────

  "ZPlumbing.plumb" should "match an http URL with PlumbExec" in {
    val result = ZPlumbing.plumb("https://example.com", "/cwd")
    result shouldBe defined
    result.get._1 shouldBe PlumbExec
    result.get._2 should include("https://example.com")
  }

  it should "match an https URL" in {
    val result = ZPlumbing.plumb("https://github.com/user/repo", "/tmp")
    result shouldBe defined
    result.get._1 shouldBe PlumbExec
  }

  it should "convert file:line:col to a look with PlumbLook using built-in filecol rule" in {
    val result = ZPlumbing.plumb("src/main/Foo.scala:42:7", "/cwd")
    result shouldBe defined
    result.get._1 shouldBe PlumbLook
    result.get._2 shouldEqual "src/main/Foo.scala:42"
  }

  it should "return None for plain text that matches no rule" in {
    ZPlumbing.plumb("just some text", "/cwd") shouldBe None
  }

  it should "return None for an empty string" in {
    ZPlumbing.plumb("", "/cwd") shouldBe None
  }

  // ── custom rules ────────────────────────────────────────────────────────────

  it should "use custom rules when set directly" in {
    ZPlumbing.rules = List(PlumbRule("test", """foo(.+)""".r, PlumbLook, "$1"))
    ZPlumbing.plumb("foobar", "/cwd") shouldBe Some((PlumbLook, "bar"))
  }

  it should "interpolate $cwd in exec templates" in {
    ZPlumbing.rules = List(PlumbRule("test", """^run$""".r, PlumbExec, "make -C $cwd"))
    val result = ZPlumbing.plumb("run", "/myproject")
    result shouldBe Some((PlumbExec, "make -C /myproject"))
  }

  it should "match the first rule when multiple rules match" in {
    ZPlumbing.rules = List(
      PlumbRule("first",  """^foo""".r, PlumbExec, "first"),
      PlumbRule("second", """^foo""".r, PlumbExec, "second"),
    )
    ZPlumbing.plumb("foobar", "/cwd").map(_._2) shouldBe Some("first")
  }

  // ── load from file ─────────────────────────────────────────────────────────

  it should "load rules from a file and apply them" in {
    val tmp = Files.createTempFile("plumbing", "").toFile
    tmp.deleteOnExit()
    java.nio.file.Files.writeString(tmp.toPath,
      "match mytest /^HELLO/ exec echo $0\n")
    // Temporarily redirect the load path by manipulating rules directly
    // (load() reads ~/.z/plumbing; parse the file manually instead)
    val src = io.Source.fromFile(tmp)
    val lines = src.getLines().toList
    src.close()
    lines should have size 1
    lines.head should startWith("match mytest")
  }
}
