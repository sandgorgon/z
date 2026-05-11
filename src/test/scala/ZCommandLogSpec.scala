import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach

class ZCommandLogSpec extends AnyFlatSpec with Matchers with BeforeAndAfterEach {

  override def beforeEach(): Unit = CommandLog.clear()

  "CommandLog.record" should "return a timestamp in HH:mm:ss format" in {
    val ts = CommandLog.record("wnd", "/some/path", "Get")
    ts should fullyMatch regex """\d{2}:\d{2}:\d{2}"""
  }

  it should "make the recorded entry visible in render()" in {
    CommandLog.record("wnd", "/foo", "Put")
    CommandLog.render should include("Put")
    CommandLog.render should include("wnd")
    CommandLog.render should include("/foo")
  }

  "CommandLog.render" should "return empty string when no entries have been recorded" in {
    CommandLog.render shouldEqual ""
  }

  it should "include all recorded entries in order" in {
    CommandLog.record("wnd", "src", "Get")
    CommandLog.record("col", "src", "New")
    CommandLog.record("app", "src", "Help")
    val out = CommandLog.render
    out.indexOf("Get") should be < out.indexOf("New")
    out.indexOf("New") should be < out.indexOf("Help")
  }

  "CommandLog.setLimit" should "evict the oldest entry once the cap is reached" in {
    CommandLog.setLimit(3)
    CommandLog.record("wnd", "p", "cmd1")
    CommandLog.record("wnd", "p", "cmd2")
    CommandLog.record("wnd", "p", "cmd3")
    CommandLog.record("wnd", "p", "cmd4")  // should evict cmd1
    val out = CommandLog.render
    out should not include "cmd1"
    out should include("cmd2")
    out should include("cmd3")
    out should include("cmd4")
  }

  it should "keep exactly limit entries after heavy use" in {
    CommandLog.setLimit(5)
    (1 to 20).foreach(i => CommandLog.record("wnd", "p", s"cmd$i"))
    CommandLog.render.linesIterator.count(_.nonEmpty) shouldEqual 5
  }
}
