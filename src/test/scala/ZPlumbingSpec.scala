import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach
import java.io.File
import java.nio.file.Files

class ZPlumbingSpec extends AnyFlatSpec with Matchers with BeforeAndAfterEach {

  private val savedRules = ZPlumbing.rules

  override def afterEach(): Unit = ZPlumbing.rules = savedRules

  // Helper to build a PlumbMessage with default wdir
  private def msg(data: String, wdir: String = "/tmp", src: String = "") =
    PlumbMessage(data, wdir, src)

  // ── Built-in rules ──────────────────────────────────────────────────────────

  "ZPlumbing.plumb" should "match an http URL with PlumbPortExec" in {
    val result = ZPlumbing.plumb(msg("https://example.com"))
    result shouldBe defined
    result.get.port shouldBe PlumbPortExec
    result.get.cmd.get should include("https://example.com")
  }

  it should "match an https URL" in {
    val result = ZPlumbing.plumb(msg("https://github.com/user/repo"))
    result shouldBe defined
    result.get.port shouldBe PlumbPortExec
  }

  it should "route file:line:col to PlumbPortEdit when the file exists" in {
    val tmp = Files.createTempFile("plumb", ".txt").toFile
    tmp.deleteOnExit()
    val result = ZPlumbing.plumb(msg(s"${tmp.getPath}:42:7", wdir = tmp.getParent))
    result shouldBe defined
    result.get.port shouldBe PlumbPortEdit
    result.get.message.data shouldEqual tmp.getCanonicalPath
    result.get.message.attrs.get("addr") shouldEqual Some("42")
  }

  it should "not match file:line:col when the file does not exist" in {
    ZPlumbing.plumb(msg("nonexistent_file.scala:42:7")) shouldBe None
  }

  it should "route file:line to PlumbPortEdit when the file exists" in {
    val tmp = Files.createTempFile("plumb", ".txt").toFile
    tmp.deleteOnExit()
    val result = ZPlumbing.plumb(msg(s"${tmp.getPath}:10", wdir = tmp.getParent))
    result shouldBe defined
    result.get.port shouldBe PlumbPortEdit
    result.get.message.data shouldEqual tmp.getCanonicalPath
    result.get.message.attrs.get("addr") shouldEqual Some("10")
  }

  it should "not match file:line when the file does not exist" in {
    ZPlumbing.plumb(msg("nonexistent_file.scala:10")) shouldBe None
  }

  it should "return None for plain text matching no rule" in {
    ZPlumbing.plumb(msg("just some text")) shouldBe None
  }

  it should "return None for an empty string" in {
    ZPlumbing.plumb(msg("")) shouldBe None
  }

  // ── Multi-line block parsing ────────────────────────────────────────────────

  it should "parse and evaluate a new-format exec block" in {
    ZPlumbing.rules = ZPlumbing.parseBlocks(
      """data matches ^GREET$
        |plumb to exec
        |plumb start echo hello""".stripMargin
    )
    val result = ZPlumbing.plumb(msg("GREET"))
    result shouldBe defined
    result.get.port shouldBe PlumbPortExec
    result.get.cmd shouldEqual Some("echo hello")
  }

  it should "parse and evaluate a new-format edit block" in {
    ZPlumbing.rules = ZPlumbing.parseBlocks(
      """data matches ^OPEN$
        |plumb to edit""".stripMargin
    )
    val result = ZPlumbing.plumb(msg("OPEN"))
    result shouldBe defined
    result.get.port shouldBe PlumbPortEdit
  }

  it should "handle multiple blocks and match the first one" in {
    ZPlumbing.rules = ZPlumbing.parseBlocks(
      """data matches ^foo
        |plumb to exec
        |plumb start first
        |
        |data matches ^foo
        |plumb to exec
        |plumb start second""".stripMargin
    )
    ZPlumbing.plumb(msg("foobar")).flatMap(_.cmd) shouldEqual Some("first")
  }

  it should "skip a block when a condition fails and try the next" in {
    ZPlumbing.rules = ZPlumbing.parseBlocks(
      """data matches ^NOMATCH
        |plumb to exec
        |plumb start should-not-run
        |
        |data matches ^hello
        |plumb to exec
        |plumb start echo $0""".stripMargin
    )
    val result = ZPlumbing.plumb(msg("hello world"))
    result shouldBe defined
    result.get.cmd shouldEqual Some("echo hello world")
  }

  // ── Condition verbs ─────────────────────────────────────────────────────────

  it should "pass arg isfile when the file exists and set $arg" in {
    val tmp = Files.createTempFile("plumb", ".txt").toFile
    tmp.deleteOnExit()
    ZPlumbing.rules = ZPlumbing.parseBlocks(
      s"""data matches .+
         |arg isfile $$0
         |plumb to edit""".stripMargin
    )
    val result = ZPlumbing.plumb(msg(tmp.getPath, wdir = tmp.getParent))
    result shouldBe defined
    result.get.port shouldBe PlumbPortEdit
  }

  it should "fail arg isfile when the file does not exist" in {
    ZPlumbing.rules = ZPlumbing.parseBlocks(
      """data matches .+
        |arg isfile $0
        |plumb to edit""".stripMargin
    )
    ZPlumbing.plumb(msg("/no/such/file.txt")) shouldBe None
  }

  it should "pass arg isdir when the directory exists" in {
    ZPlumbing.rules = ZPlumbing.parseBlocks(
      """data matches .+
        |arg isdir $0
        |plumb to edit""".stripMargin
    )
    val result = ZPlumbing.plumb(msg("/tmp"))
    result shouldBe defined
  }

  it should "fail arg isdir for a regular file" in {
    val tmp = Files.createTempFile("plumb", ".txt").toFile
    tmp.deleteOnExit()
    ZPlumbing.rules = ZPlumbing.parseBlocks(
      """data matches .+
        |arg isdir $0
        |plumb to edit""".stripMargin
    )
    ZPlumbing.plumb(msg(tmp.getPath)) shouldBe None
  }

  it should "pass wdir matches when wdir matches regex" in {
    ZPlumbing.rules = ZPlumbing.parseBlocks(
      """wdir matches /tmp
        |data matches .+
        |plumb to exec
        |plumb start echo $0""".stripMargin
    )
    ZPlumbing.plumb(msg("hello", wdir = "/tmp/work")) shouldBe defined
  }

  it should "fail wdir matches when wdir does not match" in {
    ZPlumbing.rules = ZPlumbing.parseBlocks(
      """wdir matches /projects
        |data matches .+
        |plumb to exec
        |plumb start echo $0""".stripMargin
    )
    ZPlumbing.plumb(msg("hello", wdir = "/tmp")) shouldBe None
  }

  it should "pass src is when src matches exactly" in {
    ZPlumbing.rules = ZPlumbing.parseBlocks(
      """src is /my/file.txt
        |data matches .+
        |plumb to exec
        |plumb start echo $0""".stripMargin
    )
    ZPlumbing.plumb(msg("hello", src = "/my/file.txt")) shouldBe defined
    ZPlumbing.plumb(msg("hello", src = "/other.txt"))   shouldBe None
  }

  it should "always pass type is (Plan 9 compat no-op)" in {
    ZPlumbing.rules = ZPlumbing.parseBlocks(
      """type is text
        |data matches ^ok$
        |plumb to edit""".stripMargin
    )
    ZPlumbing.plumb(msg("ok")) shouldBe defined
  }

  // ── Action verbs ────────────────────────────────────────────────────────────

  it should "rewrite msg.data via data set" in {
    ZPlumbing.rules = ZPlumbing.parseBlocks(
      """data matches ^(.+):(\d+)$
        |data set $1
        |attr add addr=$2
        |plumb to edit""".stripMargin
    )
    val result = ZPlumbing.plumb(msg("foo.txt:99"))
    result shouldBe defined
    result.get.message.data shouldEqual "foo.txt"
    result.get.message.attrs.get("addr") shouldEqual Some("99")
  }

  it should "add attributes via attr add" in {
    ZPlumbing.rules = ZPlumbing.parseBlocks(
      """data matches ^(.+):(\d+):(\d+)$
        |attr add addr=$2
        |attr add col=$3
        |plumb to edit""".stripMargin
    )
    val result = ZPlumbing.plumb(msg("src/Foo.scala:10:5"))
    result shouldBe defined
    result.get.message.attrs shouldEqual Map("addr" -> "10", "col" -> "5")
  }

  it should "support attr set as alias for attr add" in {
    ZPlumbing.rules = ZPlumbing.parseBlocks(
      """data matches ^go$
        |attr set key=value
        |plumb to edit""".stripMargin
    )
    val result = ZPlumbing.plumb(msg("go"))
    result shouldBe defined
    result.get.message.attrs.get("key") shouldEqual Some("value")
  }

  // ── Variable interpolation ──────────────────────────────────────────────────

  it should "interpolate $0 as the full data match" in {
    ZPlumbing.rules = ZPlumbing.parseBlocks(
      """data matches foo(.+)
        |plumb to exec
        |plumb start echo $0""".stripMargin
    )
    ZPlumbing.plumb(msg("foobar")).flatMap(_.cmd) shouldEqual Some("echo foobar")
  }

  it should "interpolate $1 as first capture group" in {
    ZPlumbing.rules = ZPlumbing.parseBlocks(
      """data matches foo(.+)
        |plumb to exec
        |plumb start echo $1""".stripMargin
    )
    ZPlumbing.plumb(msg("foobar")).flatMap(_.cmd) shouldEqual Some("echo bar")
  }

  it should "interpolate $wdir in a template" in {
    ZPlumbing.rules = ZPlumbing.parseBlocks(
      """data matches ^run$
        |plumb to exec
        |plumb start make -C $wdir""".stripMargin
    )
    ZPlumbing.plumb(msg("run", wdir = "/myproject")).flatMap(_.cmd) shouldEqual Some("make -C /myproject")
  }

  it should "interpolate $arg from arg isfile" in {
    val tmp = Files.createTempFile("plumb", ".txt").toFile
    tmp.deleteOnExit()
    ZPlumbing.rules = ZPlumbing.parseBlocks(
      """data matches .+
        |arg isfile $0
        |data set $arg
        |plumb to edit""".stripMargin
    )
    val result = ZPlumbing.plumb(msg(tmp.getPath, wdir = tmp.getParent))
    result shouldBe defined
    result.get.message.data shouldEqual tmp.getCanonicalPath
  }

  it should "interpolate $file as alias for $arg" in {
    val tmp = Files.createTempFile("plumb", ".txt").toFile
    tmp.deleteOnExit()
    ZPlumbing.rules = ZPlumbing.parseBlocks(
      """data matches .+
        |arg isfile $0
        |data set $file
        |plumb to edit""".stripMargin
    )
    val result = ZPlumbing.plumb(msg(tmp.getPath, wdir = tmp.getParent))
    result shouldBe defined
    result.get.message.data shouldEqual tmp.getCanonicalPath
  }

  // ── Backward compat: old single-line format ─────────────────────────────────

  it should "parse legacy exec format and run as exec" in {
    ZPlumbing.rules = ZPlumbing.parseBlocks("match mytest /^HELLO/ exec echo $0\n")
    val result = ZPlumbing.plumb(msg("HELLO world"))
    result shouldBe defined
    result.get.port shouldBe PlumbPortExec
    result.get.cmd shouldEqual Some("echo HELLO world")
  }

  it should "parse legacy look format and route to edit" in {
    ZPlumbing.rules = ZPlumbing.parseBlocks("match mytest /foo(.+)/ look $1\n")
    val result = ZPlumbing.plumb(msg("foobar"))
    result shouldBe defined
    result.get.port shouldBe PlumbPortEdit
    result.get.message.data shouldEqual "bar"
  }

  it should "return None for text matching no legacy rule" in {
    ZPlumbing.rules = ZPlumbing.parseBlocks("match mytest /^HELLO/ exec echo $0\n")
    ZPlumbing.plumb(msg("goodbye")) shouldBe None
  }

  // ── Load from file ──────────────────────────────────────────────────────────

  it should "load new-format rules from a file and apply them" in {
    val tmp = Files.createTempFile("plumbing", "").toFile
    tmp.deleteOnExit()
    java.nio.file.Files.writeString(tmp.toPath,
      "data matches ^HELLO\nplumb to exec\nplumb start echo $0\n")
    val blocks = ZPlumbing.parseBlocks(io.Source.fromFile(tmp).mkString)
    blocks should have size 1
    ZPlumbing.rules = blocks
    val result = ZPlumbing.plumb(msg("HELLO world"))
    result shouldBe defined
    result.get.port shouldBe PlumbPortExec
    result.get.cmd shouldEqual Some("echo HELLO world")
  }

  it should "load legacy-format rules from a file and apply them" in {
    val tmp = Files.createTempFile("plumbing", "").toFile
    tmp.deleteOnExit()
    java.nio.file.Files.writeString(tmp.toPath, "match test /^HI/ exec greet $0\n")
    val blocks = ZPlumbing.parseBlocks(io.Source.fromFile(tmp).mkString)
    ZPlumbing.rules = blocks
    val result = ZPlumbing.plumb(msg("HI there"))
    result shouldBe defined
    result.get.cmd shouldEqual Some("greet HI there")
  }

  it should "ignore comment lines within a block" in {
    ZPlumbing.rules = ZPlumbing.parseBlocks(
      """# this is a comment
        |data matches ^ok$
        |# another comment
        |plumb to edit""".stripMargin
    )
    ZPlumbing.plumb(msg("ok")) shouldBe defined
  }
}
