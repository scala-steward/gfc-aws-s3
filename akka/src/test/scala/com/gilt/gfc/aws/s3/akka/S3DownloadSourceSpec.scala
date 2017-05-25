package com.gilt.gfc.aws.s3.akka

import java.io.ByteArrayInputStream

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.util.ByteString
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model._
import org.apache.http.client.methods.HttpRequestBase
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.Future
import scala.concurrent.duration._

class S3DownloadSourceSpec extends WordSpec with Matchers with MockFactory {

  import S3DownloaderSource._
  import ScalaFutures._
  implicit val system = ActorSystem("s3-test")

  implicit val materializer = ActorMaterializer()

  val chunkSize = 200
  val expectedBytes = 4000
  val bucketName = "test-bucket"
  val fileKey = "test-file"
  val memoryBufferSize = 1024 * 1024
  val charset = "UTF-8"
  val fileBytes = (1 to 10000).flatMap(_.toString.getBytes(charset)).toArray
  val initialString = new String(fileBytes, charset)
  val numberOfPartsOnServer = 5
  val timeout = Timeout(10 seconds)

  def mkS3ObjectWithData(data: Array[Byte]): S3Object = {
    val s3Object = stub[S3Object]
    (s3Object.getObjectContent _).when().returning(new S3ObjectInputStream(new ByteArrayInputStream(data), stub[HttpRequestBase]))
    s3Object
  }

  def downloadAsVector(source: Source[ByteString, NotUsed]): Future[Vector[Byte]] = {
    source.mapConcat[Byte](identity)
      .toMat(Sink.fold(Vector[Byte]()) {
        case (vector, elem) => vector :+ elem
      })(Keep.right)
      .run()
  }

  def initMultipartS3Mock(failingPart: Option[Int]) = {
    val s3Client = stub[AmazonS3]

    (s3Client.getObjectMetadata(_: GetObjectMetadataRequest)).when(where { req: GetObjectMetadataRequest =>
      req.getBucketName == bucketName && req.getKey == fileKey && req.getPartNumber != null
    }).returning({
      val resp = stub[ObjectMetadata]
      (resp.getPartCount _).when().returning(numberOfPartsOnServer)
      resp
    })

    (s3Client.getObject(_: GetObjectRequest)).when(where { req: GetObjectRequest =>
      req.getBucketName == bucketName && req.getKey == fileKey && req.getPartNumber != null
    }).onCall { req: GetObjectRequest =>
      val partNumber = req.getPartNumber()
      failingPart.foreach(x => if(partNumber == x) throw new IntendedTestFailureException)
      val content = fileBytes.slice((partNumber - 1) * fileBytes.length / numberOfPartsOnServer, partNumber * fileBytes.length / numberOfPartsOnServer)
      mkS3ObjectWithData(content)
    }

    s3Client
  }

  def initChunkedS3Mock(failingByteNumber: Option[Long]) = {
    val s3Client = stub[AmazonS3]

    (s3Client.getObjectMetadata(_: GetObjectMetadataRequest)).when(where { req: GetObjectMetadataRequest =>
      req.getBucketName == bucketName && req.getKey == fileKey && req.getPartNumber == null
    }).returning({
      val resp = stub[ObjectMetadata]
      (resp.getContentLength _).when().returning(fileBytes.length)
      resp
    })

    (s3Client.getObject(_: GetObjectRequest)).when(where { req: GetObjectRequest =>
      req.getBucketName == bucketName && req.getKey == fileKey && req.getPartNumber == null
    }).onCall { req: GetObjectRequest =>
      failingByteNumber.foreach(x => if(x >= req.getRange()(0) && req.getRange()(1) < x) throw new IntendedTestFailureException)
      val content = fileBytes.slice(req.getRange()(0).toInt, req.getRange()(1).toInt + 1)
      mkS3ObjectWithData(content)
    }
    s3Client
  }

  "Multipart downloader" should {

    "Successfully download" in {
      val s3Client = initMultipartS3Mock(None)
      val source = Source.s3MultipartDownload(s3Client, bucketName, fileKey, memoryBufferSize)

      val result: Future[Vector[Byte]] = downloadAsVector(source)

      whenReady(result, timeout) { vector =>
        val str = new String(vector.toArray, charset)
        str shouldEqual initialString
      }
    }

    "Fail correctly" in {
      val s3Client = initMultipartS3Mock(Some(3))
      val source = Source.s3MultipartDownload(s3Client, bucketName, fileKey, memoryBufferSize)

      val result: Future[Vector[Byte]] = downloadAsVector(source)

      whenReady(result.failed, timeout) { ex =>
        ex shouldBe a [IntendedTestFailureException]
      }
    }
  }

  "Chunked download" should {
    "Successfully download" in {
      val s3Client = initChunkedS3Mock(None)
      val source = Source.s3ChunkedDownload(s3Client, bucketName, fileKey, fileBytes.length / 3, memoryBufferSize)

      val result = downloadAsVector(source)

      whenReady(result, timeout) { v =>
        val str = new String(v.toArray, charset)
        str shouldEqual initialString
      }
    }

    "Fail correctly" in {
      val s3Client = initChunkedS3Mock(Some(fileBytes.length / 2))
      val source = Source.s3ChunkedDownload(s3Client, bucketName, fileKey, fileBytes.length / 3, memoryBufferSize)

      val result = downloadAsVector(source)

      whenReady(result.failed, timeout) { ex =>
        ex shouldBe a [IntendedTestFailureException]
      }
    }

  }
}
