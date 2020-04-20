package com.twitter.finagle.mysql.integration

import com.twitter.conversions.DurationOps._
import com.twitter.finagle.Mysql
import com.twitter.finagle.mysql.QueryRequest
import com.twitter.finagle.param
import com.twitter.finagle.tracing._
import com.twitter.util.{Await, Awaitable}
import org.scalatest.FunSuite

class MysqlBuilderTest extends FunSuite with IntegrationClient {

  private[this] def ready[T](t: Awaitable[T]): Unit = Await.ready(t, 5.seconds)

  test("clients have granular tracing") {
    Trace.enable()
    var annotations: List[Annotation] = Nil
    val mockTracer = new Tracer {
      def record(record: Record): Unit = {
        annotations ::= record.annotation
      }
      def sampleTrace(traceId: TraceId): Option[Boolean] = Some(true)
    }

    // if we have a local instance of mysql running.
    if (isAvailable) {
      val username = p.getProperty("username", "<user>")
      val password = p.getProperty("password", null)
      val db = p.getProperty("db", "test")
      val client = Mysql.client
        .configured(param.Label("myclient"))
        .configured(param.Tracer(mockTracer))
        .withDatabase("test")
        .withCredentials(username, password)
        .withDatabase(db)
        .withConnectionInitRequest(QueryRequest("SET SESSION sql_mode='TRADITIONAL,NO_AUTO_VALUE_ON_ZERO,ONLY_FULL_GROUP_BY'"))
        .newRichClient("localhost:3306")

      ready(client.query("SELECT 1"))
      ready(client.prepare("SELECT ?")(1))
      ready(client.ping())

      val mysqlTraces = annotations.collect {
        case Annotation.BinaryAnnotation("mysql.query", "SELECT 1") => ()
        case Annotation.BinaryAnnotation("mysql.prepare", "SELECT ?") => ()
        case Annotation.Message("mysql.PingRequest") => ()
      }

      assert(mysqlTraces.nonEmpty, "missing traces")
    }

  }
}
