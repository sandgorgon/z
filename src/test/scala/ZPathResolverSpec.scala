import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.io.File

class ZPathResolverSpec extends AnyFlatSpec with Matchers {

  val root = new File(System.getProperty("user.home")).getCanonicalPath

  // ── resolvePath ─────────────────────────────────────────────────────────────

  "resolvePath" should "return an absolute path unchanged" in {
    ZPathResolver.resolvePath("/usr/local/bin", root) shouldEqual "/usr/local/bin"
  }

  it should "join a relative path with root and canonicalize" in {
    val result = ZPathResolver.resolvePath("src/main", root)
    result shouldEqual new File(root + "/src/main").getCanonicalPath
  }

  it should "expand a tilde path" in {
    val result = ZPathResolver.resolvePath("~/Documents", root)
    result shouldEqual new File(System.getProperty("user.home") + "/Documents").getCanonicalPath
  }

  it should "handle '.' relative to root" in {
    val result = ZPathResolver.resolvePath(".", root)
    result shouldEqual new File(root + "/.").getCanonicalPath
  }

  // ── isWndPathPrefix ─────────────────────────────────────────────────────────

  "isWndPathPrefix" should "return true for a relative rawPath when stxt is a prefix and fromTag is false" in {
    ZPathResolver.isWndPathPrefix("src/main/Foo.scala", "src", fromTag = false, "src/main/Foo.scala cmd1") shouldBe true
  }

  it should "return true when fromTag=true and tagText starts with stxt" in {
    ZPathResolver.isWndPathPrefix("src/main/Foo.scala", "src", fromTag = true, "src/main/Foo.scala cmd1") shouldBe true
  }

  it should "return false when fromTag=true and tagText does not start with stxt" in {
    // stxt appears as a command arg, not at position 0 of the tag
    ZPathResolver.isWndPathPrefix("src/main/Foo.scala", "src", fromTag = true, "Get src/main/Foo.scala") shouldBe false
  }

  it should "return false when rawPath is an absolute path" in {
    ZPathResolver.isWndPathPrefix("/home/user/Foo.scala", "home", fromTag = false, "/home/user/Foo.scala") shouldBe false
  }

  it should "return false when stxt is not a prefix of rawPath" in {
    ZPathResolver.isWndPathPrefix("src/main/Foo.scala", "lib", fromTag = false, "lib cmd1") shouldBe false
  }

  // ── resolveBase ─────────────────────────────────────────────────────────────

  "resolveBase" should "return root when stxt is a window path prefix" in {
    val base = ZPathResolver.resolveBase("src/main/Foo.scala", "src", fromTag = true, "src/main/Foo.scala", root, "/some/baseDir")
    base shouldEqual root
  }

  it should "return baseDir when stxt is not a window path prefix" in {
    val base = ZPathResolver.resolveBase("src/main/Foo.scala", "lib", fromTag = false, "lib cmd", root, "/some/baseDir")
    base shouldEqual "/some/baseDir"
  }
}
