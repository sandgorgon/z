import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach
import java.io.File
import java.nio.file.Files
import scala.compiletime.uninitialized

class ZFileIOSpec extends AnyFlatSpec with Matchers with BeforeAndAfterEach {

  private var tempDir: File = uninitialized

  override def beforeEach(): Unit = {
    tempDir = Files.createTempDirectory("zfileio-test").toFile
  }

  override def afterEach(): Unit = {
    def delete(f: File): Unit = {
      if (f.isDirectory) f.listFiles.foreach(delete)
      f.delete()
    }
    delete(tempDir)
  }

  // ── writeFile / readFile round-trip ─────────────────────────────────────────

  "writeFile then readFile" should "round-trip text content" in {
    val f = new File(tempDir, "hello.txt")
    ZFileIO.writeFile(f.getPath, "hello world") shouldBe Right(())
    ZFileIO.readFile(f.getPath) shouldBe Right("hello world")
  }

  it should "preserve newlines" in {
    val content = "line1\nline2\nline3"
    val f = new File(tempDir, "lines.txt")
    ZFileIO.writeFile(f.getPath, content)
    ZFileIO.readFile(f.getPath) shouldBe Right(content)
  }

  "writeFile" should "return Left on an unwritable path" in {
    ZFileIO.writeFile("/no/such/dir/file.txt", "data") shouldBe a[Left[?, ?]]
  }

  "readFile" should "return Left when the file does not exist" in {
    ZFileIO.readFile(new File(tempDir, "missing.txt").getPath) shouldBe a[Left[?, ?]]
  }

  // ── readDir ──────────────────────────────────────────────────────────────────

  "readDir" should "list files sorted alphabetically" in {
    new File(tempDir, "b.txt").createNewFile()
    new File(tempDir, "a.txt").createNewFile()
    ZFileIO.readDir(tempDir.getPath).map(_.split(System.lineSeparator).toSeq) shouldEqual Right(Seq("a.txt", "b.txt"))
  }

  it should "append / to directory entries" in {
    new File(tempDir, "subdir").mkdir()
    new File(tempDir, "file.txt").createNewFile()
    val listing = ZFileIO.readDir(tempDir.getPath).getOrElse("")
    listing should include("subdir/")
    listing should include("file.txt")
    listing should not include "subdir\n"
  }

  it should "return Left when the path does not exist" in {
    ZFileIO.readDir(new File(tempDir, "ghost").getPath) shouldBe a[Left[?, ?]]
  }
}
