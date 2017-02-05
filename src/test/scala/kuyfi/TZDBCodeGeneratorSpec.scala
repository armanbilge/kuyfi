package kuyfi

import java.time.{Duration, LocalTime, LocalDateTime, Month, ZoneOffset}
import java.time.zone.ZoneOffsetTransition

import kuyfi.TZDB._
import org.scalatest.{FlatSpec, Matchers}

class TZDBCodeGeneratorSpec extends FlatSpec with Matchers {

  val zone1 = Zone("Europe/Belfast", List(
                ZoneTransition(GmtOffset(0, -23, -40), NullRule,                        "LMT",     Some(Until(1880, Some(Month.AUGUST),  Some(DayOfTheMonth(2)),  None))),
                ZoneTransition(GmtOffset(0, -25, -21), NullRule,                        "DMT",     Some(Until(1916, Some(Month.MAY),     Some(DayOfTheMonth(21)), Some(AtWallTime(LocalTime.of(2, 0)))))),
                ZoneTransition(GmtOffset(0, -25, -21), FixedOffset(GmtOffset(1, 0, 0)), "IST",     Some(Until(1916, Some(Month.OCTOBER), Some(DayOfTheMonth(1)),  Some(AtStandardTime(LocalTime.of(2, 0)))))),
                ZoneTransition(GmtOffset(0,   0,   0), RuleId("GB-Eire"),               "%s",      Some(Until(1968, Some(Month.OCTOBER), Some(DayOfTheMonth(27)), None))),
                ZoneTransition(GmtOffset(1,   0,   0), NullRule,                        "BST",     Some(Until(1971, Some(Month.OCTOBER), Some(DayOfTheMonth(31)), Some(AtUniversalTime(LocalTime.of(2, 0)))))),
                ZoneTransition(GmtOffset(0,   0,   0), RuleId("GB-Eire"),               "%s",      Some(Until(1996, None,                None,                    None))),
                ZoneTransition(GmtOffset(0,   0,   0), RuleId("EU"),                    "GMT/BST", None)
            ))

  val zoneFixed = Zone("Etc/GMT+1", List(ZoneTransition(GmtOffset(-1,0,0), NullRule, "-01",None)))

  val zone2 = Zone("Africa/Tripoli", List(
      ZoneTransition(GmtOffset( 0, 52, 44), NullRule,        "LMT",   Some(Until(1920, None,                  None,                    None))),
      ZoneTransition(GmtOffset( 1,  0,  0), RuleId("Libya"), "CE%sT", Some(Until(1959, None,                  None,                    None))),
      ZoneTransition(GmtOffset( 2,  0,  0), NullRule,        "EET",   Some(Until(1982, None,                  None,                    None))),
      ZoneTransition(GmtOffset( 1,  0,  0), RuleId("Libya"), "CE%sT", Some(Until(1990, Some(Month.MAY),       Some(DayOfTheMonth(4)),  None))),
      ZoneTransition(GmtOffset( 2,  0,  0), NullRule,        "EET",   Some(Until(1996, Some(Month.SEPTEMBER), Some(DayOfTheMonth(30)), None))),
      ZoneTransition(GmtOffset( 1,  0,  0), RuleId("Libya"), "CE%sT", Some(Until(1997, Some(Month.OCTOBER),   Some(DayOfTheMonth(4)),  None))),
      ZoneTransition(GmtOffset( 2,  0,  0), NullRule,        "EET",   Some(Until(2012, Some(Month.NOVEMBER),  Some(DayOfTheMonth(10)), Some(AtWallTime(LocalTime.of(2, 0)))))),
      ZoneTransition(GmtOffset( 1,  0,  0), RuleId("Libya"), "CE%sT", Some(Until(2013, Some(Month.OCTOBER),   Some(DayOfTheMonth(25)), Some(AtWallTime(LocalTime.of(2, 0)))))),
      ZoneTransition(GmtOffset( 2,  0,  0), NullRule,        "EET",   None)
  ))

  val link1 = Link("Europe/Belfast", "Europe/Ireland")
  val link2 = Link("America/Curacao", "America/Aruba")

  import TZDBCodeGenerator._
  import treehugger.forest._

  "TZDB Code generator" should
    "generate a name from a Zone" in {
      treeToString(TreeGenerator[Zone].generateTree(zone1)) shouldBe "(\"Europe/Belfast\", ZoneRules.of(ZoneOffset.ofHoursMinutesSeconds(0, -23, -40)))"
    }
    it should "generate a name from a Fixed offset Zone" in {
      treeToString(TreeGenerator[Zone].generateTree(zoneFixed)) shouldBe "(\"Etc/GMT+1\", ZoneRules.of(ZoneOffset.ofHoursMinutesSeconds(-1, 0, 0)))"
    }
    it should "generate a tuple from a Link" in {
      treeToString(TreeGenerator[Link].generateTree(link2)) shouldBe "(\"America/Aruba\", \"America/Curacao\")"
    }
    it should "clean dangling links" in {
      val rows1 = link1.liftC[Row] :: link2.liftC[Row] :: Nil
      cleanLinks(rows1) shouldBe empty

      val rows2 = link1.liftC[Row] :: link2.liftC[Row] :: zone1.liftC[Row] :: Nil

      cleanLinks(rows2) should have size 2
    }
    it should "generate an object from a List of Zones" in {
      treeToString(TreeGenerator[List[Zone]].generateTree(List(zone1, zone2))) shouldBe "lazy val allZones: Map[String, ZoneRules] = Map((\"Europe/Belfast\", ZoneRules.of(ZoneOffset.ofHoursMinutesSeconds(0, -23, -40))), (\"Africa/Tripoli\", ZoneRules.of(ZoneOffset.ofHoursMinutesSeconds(0, 52, 44))))"
    }
    it should "generate from zone offset transition" in {
      treeToString(TreeGenerator[ZoneOffsetTransitionParams].generateTree(ZoneOffsetTransitionParams(LocalDateTime.of(2017, Month.FEBRUARY, 1, 10, 15), ZoneOffset.ofHours(1), ZoneOffset.ofHours(2)))) shouldBe s"ZoneOffsetTransition.of(LocalDateTime.of(2017, 2, 1, 10, 15, 0, 0), ZoneOffset.ofTotalSeconds(3600), ZoneOffset.ofTotalSeconds(7200))"
    }
    it should "generate from LocalDateTime" in {
      treeToString(TreeGenerator[LocalDateTime].generateTree(LocalDateTime.of(2017, Month.FEBRUARY, 1, 10, 15, 25))) shouldBe s"LocalDateTime.of(2017, 2, 1, 10, 15, 25, 0)"
    }
    it should "generate from offset" in {
      treeToString(TreeGenerator[ZoneOffset].generateTree(ZoneOffset.ofHoursMinutesSeconds(1, 2, 3))) shouldBe s"ZoneOffset.ofTotalSeconds(${1*3600+2*60+3})"
    }
    it should "import a top level package" in {
      treeToString(exportTzdb("org.threeten.bp", "org.threeten.bp", link1.liftC[Row] :: link2.liftC[Row] :: zone1.liftC[Row] :: Nil)) should include ("import org.threeten.bp._")
    }
    it should "generate from zone rules param" in {
      val params = ZoneRulesParams(ZoneOffset.ofHours(1), ZoneOffset.ofHours(0), Nil, Nil, Nil)
      treeToString(TreeGenerator[ZoneRulesParams].generateTree(params)).trim shouldBe s"""{
      |  val bso: ZoneOffset = ZoneOffset.ofTotalSeconds(3600)
      |  val bwo: ZoneOffset = ZoneOffset.ofTotalSeconds(0)
      |}""".stripMargin

      treeToString(exportTzdb("org.threeten.bp", "org.threeten.bp", link1.liftC[Row] :: link2.liftC[Row] :: zone1.liftC[Row] :: Nil)) should include ("import org.threeten.bp._")
    }
    it should "produce code with the rules" in {
      /*val text = scala.io.Source.fromInputStream(this.getClass.getResourceAsStream("/europe_london"), "UTF-8").mkString

      val parsedZoneRules: Option[Map[Zone, ZoneRulesParams]] = TZDBParser.parseFile(text).map(ZoneRulesBuilder.calculateTransitions).option*/

    }
}
