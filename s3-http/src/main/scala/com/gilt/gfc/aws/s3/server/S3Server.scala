package com.gilt.gfc.aws.s3.server

import akka.actor.ActorSystem
import akka.http.javadsl.server.RouteResult
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.RouteResult.{Complete, Rejected}
import akka.http.scaladsl.server.directives.{DebuggingDirectives, LoggingMagnet}
import akka.stream.ActorMaterializer
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.S3Object
import com.gilt.gfc.aws.s3.akka.S3DownloaderSource
import org.slf4j.LoggerFactory

case class RouteConfigElement(
  uriPrefix: String,
  bucketName: String
)

class S3Server(
  host: String,
  port: Int,
  s3Client: AmazonS3,
  exposedBuckets: Seq[RouteConfigElement],
  streamChunkSize: Int
) {

  import com.gilt.gfc.aws.s3.akka.AmazonS3Extensions._

  private implicit val system = ActorSystem("s3-server")
  private implicit val materializer = ActorMaterializer()
  private implicit val executionContext = system.dispatcher
  private val logger = LoggerFactory.getLogger(this.getClass)

  private def mkResponseFromS3Object(s3Object: S3Object) = {
    val meta = s3Object.getObjectMetadata
    val filename = s3Object.getKey.split("/").last
    HttpResponse(
      headers =
        Option(meta.getExpirationTime).map(x => Expires(DateTime(x.getTime))).toList ++
        List[HttpHeader](
          `Last-Modified`(DateTime.apply(meta.getLastModified.getTime)),
          ETag(meta.getETag),
          `Content-Disposition`(ContentDispositionTypes.inline, Map("filename" -> filename))
        ),
      entity = HttpEntity(
        ContentType.parse(meta.getContentType).right.get,
        meta.getContentLength,
        S3DownloaderSource.chunked(s3Client, s3Object.getBucketName, s3Object.getKey, streamChunkSize, streamChunkSize)
      )
    )
  }

  private def getS3Item(bucket: String, prefix: String) = {
    onSuccess(s3Client.mostRecentObject(bucket, prefix)) { s3ObjectOpt =>
      complete {
        s3ObjectOpt.fold(
          HttpResponse(StatusCodes.NotFound)
        )(
          mkResponseFromS3Object
        )
      }
    }
  }

  private def logRequestResponse(req: HttpRequest)(resp: RouteResult): Unit = {
    resp match {
      case Complete(response) =>
        val message = s"${response.status} - ${req.method.value} ${req.uri}"
        if(response.status.intValue() >= 500) {
          logger.error(message)
        } else {
          logger.info(message)
        }
      case Rejected(_) =>
        logger.info(s"Rejected: ${req.method.value} ${req.uri}")
    }
  }

  private val logRequests = DebuggingDirectives.logRequestResult(
    LoggingMagnet(_ => logRequestResponse)
  )

  val healthCheckRoute = path("_internal_" / "healthcheck") {
    get {
      complete("{ \"status\": \"ok\" }")
    }
  }

  private val s3HandlingRoutes = exposedBuckets.map { routeConfigElement =>
    pathPrefix(routeConfigElement.uriPrefix) {
      extractUnmatchedPath { prefix =>
        get {
          getS3Item(routeConfigElement.bucketName, prefix.toString().stripPrefix("/").stripSuffix("/"))
        }
      }
    }
  }.reduce { (r1, r2) => r1 ~ r2 }

  val routes = healthCheckRoute ~ s3HandlingRoutes

  def start() = {
    Http().bindAndHandle(
      logRequests(routes),
      host,
      port)
  }
}

