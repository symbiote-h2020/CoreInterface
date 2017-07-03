package computerdatabase

import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.concurrent.duration._

class SearchSimulationRemote extends Simulation {

  val httpConf = http
    .baseURL("https://symbiote-dev.man.poznan.pl:8100/coreInterface/v1") // Root for all relative URLs
    .acceptHeader("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
    .doNotTrackHeader("1")
    .acceptLanguageHeader("en-US,en;q=0.5")
    .acceptEncodingHeader("gzip, deflate")
    .userAgentHeader("Mozilla/5.0 (Macintosh; Intel Mac OS X 10.8; rv:16.0) Gecko/20100101 Firefox/16.0")

  val sentHeaders = Map("X-Auth-Token" -> "token") // Required for search requests

  val scn = scenario("Search all and search with name") // A scenario is a chain of requests and pauses
    .exec(http("searchAll").get("/query").headers(sentHeaders))
    .pause(3) // Note that Gatling has recorded real time pauses
    .exec(http("searchProperties").get("/query?observed_property=temperature").headers(sentHeaders))

  setUp(scn.inject(rampUsers(10) over (10 seconds)).protocols(httpConf))
}
