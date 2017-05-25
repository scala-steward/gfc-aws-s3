package com.gilt.gfc.aws.s3.akka

import java.util.Date

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.{S3Object, S3ObjectSummary}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object AmazonS3Extensions {

  implicit class S3Extensions(val amazonS3: AmazonS3) extends AnyVal {

    import scala.concurrent.blocking

    def mostRecentObject(bucketName: String, prefix: String): Future[Option[S3Object]] = {
      Future {
        mostRecentObjectSummary(bucketName, prefix)
      }.map { objectSummaryOpt =>
        objectSummaryOpt.map { summary =>
          val key = summary.getKey
          amazonS3.getObject(bucketName, key)
        }
      }
    }

    private def mostRecentObjectSummary(bucketName: String, prefix: String): Option[S3ObjectSummary] = {
      import scala.collection.JavaConversions._
      blocking {
        amazonS3.listObjects(bucketName, prefix).getObjectSummaries.toList
      }.sortBy(_.getLastModified)(Ordering[Date].reverse).headOption
    }
  }

}
