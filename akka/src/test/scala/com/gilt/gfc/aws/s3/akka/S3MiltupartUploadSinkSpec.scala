package com.gilt.gfc.aws.s3.akka

import java.io.{ByteArrayOutputStream, OutputStream}
import java.util.UUID

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model._
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.duration._

class S3MiltupartUploadSinkSpec extends WordSpec with Matchers with MockFactory {

  implicit val system = ActorSystem("s3-test")
  implicit val materializer = ActorMaterializer()

  val chunkSize = 200
  val expectedBytes = 4000
  val bucketName = "test-bucket"
  val fileKey = "test-file"
  val sourceBytes = Range(1, 10000)
    .flatMap { x => x.toString.getBytes("UTF-8") }
    .take(expectedBytes)
  val source = Source(sourceBytes)

  def initS3Mock(failingPart: Option[Int], outStream: OutputStream) = {
    val uploadId = UUID.randomUUID().toString

    val s3Client = stub[AmazonS3]

    val initUploadResult = stub[InitiateMultipartUploadResult]
    (initUploadResult.getUploadId _).when().returning(uploadId)

    (s3Client.initiateMultipartUpload _).when( where { req:InitiateMultipartUploadRequest =>
      req.getBucketName == bucketName && req.getKey == fileKey
    }).once().returning(initUploadResult)

    (s3Client.uploadPart _).when(where { req: UploadPartRequest =>
      req.getBucketName == bucketName && req.getKey == fileKey
    }).onCall({ req: UploadPartRequest =>
      failingPart.foreach { x => if(x == req.getPartNumber) throw new IntendedTestFailureException }
      val etag = s"${req.getBucketName} - ${req.getKey} - ${req.getPartNumber}"
      val result = new UploadPartResult()
      val inputStream = req.getInputStream()
      Iterator.continually(inputStream.read)
        .takeWhile(_ != -1)
        .foreach(outStream.write)

      result.setETag(etag)
      result
    })

    (s3Client.completeMultipartUpload _).when(
      where { x: CompleteMultipartUploadRequest =>
        x.getPartETags.contains(s"${x.getBucketName} - ${x.getKey} - 1")
      }
    )

    s3Client
  }

  "Test successfull upload" in {
    val output = new ByteArrayOutputStream()
    val s3Client = initS3Mock(None, output)
    val sink = S3MultipartUploaderSink(s3Client, bucketName, fileKey, chunkSize)

    import S3MultipartUploaderSink._
    val sink2 = Sink.s3MultipartUpload(s3Client, bucketName, fileKey, chunkSize)

    val totalLengthFuture = Source(sourceBytes)
      .runWith(sink)

    val totalLength = ScalaFutures.whenReady(totalLengthFuture, Timeout(10 seconds)) { length =>
      length should be (expectedBytes)
      output.toByteArray shouldEqual sourceBytes
    }
  }

  "Test upload failure" in {
    val s3Client = initS3Mock(Some(4), new ByteArrayOutputStream())
    val sink = S3MultipartUploaderSink(s3Client, bucketName, fileKey, chunkSize)

    val totalLengthFuture = Source(sourceBytes)
      .runWith(sink)

    val result = ScalaFutures.whenReady(totalLengthFuture.failed, Timeout(10 seconds)) { ex =>
      ex shouldBe a [IntendedTestFailureException]
    }
  }
}

