package hmda.persistence.submission

import akka.actor
import akka.actor.testkit.typed.scaladsl.TestProbe
import hmda.persistence.AkkaCassandraPersistenceSpec
import akka.actor.typed.scaladsl.adapter._
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.typed.{Cluster, Join}
import hmda.messages.submission.SubmissionProcessingCommands.{
  GetParsedWithErrorCount,
  PersistHmdaRowParsedError
}
import hmda.messages.submission.SubmissionProcessingEvents.{
  HmdaRowParsedCount,
  SubmissionProcessingEvent
}
import hmda.model.filing.submission.SubmissionId
import hmda.parser.filing.lar.LarParserErrorModel.{
  InvalidLoanTerm,
  InvalidOccupancy
}
import hmda.parser.filing.ts.TsParserErrorModel.InvalidId

class HmdaParserErrorSpec extends AkkaCassandraPersistenceSpec {
  override implicit val system = actor.ActorSystem()
  override implicit val typedSystem = system.toTyped

  val sharding = ClusterSharding(typedSystem)
  SubmissionManager.startShardRegion(sharding)
  SubmissionPersistence.startShardRegion(sharding)
  HmdaParserError.startShardRegion(sharding)

  val submissionId = SubmissionId("12345", "2018", 1)

  val errorsProbe = TestProbe[SubmissionProcessingEvent]("processing-event")

  "Parser errors" must {
    Cluster(typedSystem).manager ! Join(Cluster(typedSystem).selfMember.address)
    "be persisted and retrieved back" in {
      val hmdaParserError = sharding.entityRefFor(
        HmdaParserError.typeKey,
        s"${HmdaParserError.name}-${submissionId.toString}")
      val e1 = List(InvalidId)
      val e2 = List(InvalidLoanTerm, InvalidOccupancy)
      hmdaParserError ! PersistHmdaRowParsedError(1, e1.map(_.errorMessage))
      hmdaParserError ! PersistHmdaRowParsedError(2, e2.map(_.errorMessage))
      hmdaParserError ! GetParsedWithErrorCount(errorsProbe.ref)
      errorsProbe.expectMessage(HmdaRowParsedCount(2))
    }
  }
}
