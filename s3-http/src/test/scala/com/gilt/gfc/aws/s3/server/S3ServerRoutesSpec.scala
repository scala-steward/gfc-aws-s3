package com.gilt.gfc.aws.s3.server

import java.util.UUID

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.amazonaws.auth.{AWSStaticCredentialsProvider, AnonymousAWSCredentials}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import io.findify.s3mock.S3Mock
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}



class S3ServerRoutesSpec extends WordSpec with Matchers with ScalatestRouteTest with BeforeAndAfterAll {

  val testBucket = "s3http-test"
  val s3MockPort = 18002

  val api = S3Mock(port = s3MockPort, dir = "./tmp/s3")
  api.start

  override protected def afterAll(): Unit = {
    api.stop
    super.afterAll()
  }

  val endpoint = new EndpointConfiguration(s"http://localhost:$s3MockPort", "us-east-1")
  val s3Client = AmazonS3ClientBuilder
    .standard
    .withPathStyleAccessEnabled(true)
    .withEndpointConfiguration(endpoint)
    .withCredentials(new AWSStaticCredentialsProvider(new AnonymousAWSCredentials()))
    .build

  val s3Server = new S3Server(
    "localhost",
    8080,
    s3Client,
    Seq(RouteConfigElement(
      testBucket, testBucket
    )),
    1024
  )

  "Should respond on healtcheck" in {
    Get("/_internal_/healthcheck") ~> s3Server.routes ~> check {
      responseAs[String] shouldEqual "{ \"status\": \"ok\" }"
    }
  }

  "On operations with s3 bucket" should {

    val folder = UUID.randomUUID().toString
    s3Client.createBucket(testBucket)
    s3Client.putObject(testBucket, s"$folder/key1", "file content 1")
    Thread.sleep(1000)
    s3Client.putObject(testBucket, s"$folder/key2", "file content 2")
    Thread.sleep(1000)
    s3Client.putObject(testBucket, s"$folder/key3", "file content 3")

    "Return the most recent file when given the prefix" in {
      Get(s"/$testBucket/$folder/key") ~> s3Server.routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] shouldEqual "file content 3"
        response.headers.find(_.is("content-disposition")).get.value() should include ("filename=\"key3\"")
      }
    }

    "Return the file when the name is exact match" in {
      Get(s"/$testBucket/$folder/key2") ~> s3Server.routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] shouldEqual "file content 2"
        response.headers.find(_.is("content-disposition")).get.value() should include ("filename=\"key2\"")
      }
    }

    "Return 404 when the file doesn't exist" in {
      Get(s"/$testBucket/some-inxistent-file") ~> s3Server.routes ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }

    "Return 404 when requested the file from bucket that is not served" in {
      Get(s"/not-referenced-bucket/s") ~> Route.seal(s3Server.routes) ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }
  }

}
