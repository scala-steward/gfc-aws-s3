package org.gfccollective.aws.s3.akka

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.Eventually.eventually
import com.adobe.testing.s3mock.testcontainers.S3MockContainer
import com.amazonaws.auth.{AWSCredentials, AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import org.testcontainers.utility.DockerImageName

import java.net.URI
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model._

import scala.jdk.CollectionConverters._
import java.util.UUID
import scala.util.Try

class IntegrationSpec extends AnyWordSpec
  with Matchers
  with BeforeAndAfterEach {

  private val dockerImageName = DockerImageName.parse(S3MockContainer.IMAGE_NAME).withTag("2.3.3")
  private val region = "us-east-1"
  private val awsCredentials: AWSCredentials = new BasicAWSCredentials("aaa", "bbb")
  private var bucketName: String = _
  private var s3MockContainer: S3MockContainer = _
  private var s3Client: AmazonS3 = _

  override def beforeEach(): Unit = {
    bucketName = UUID.randomUUID.toString
    s3MockContainer = new S3MockContainer(dockerImageName)
                          .withInitialBuckets(bucketName)
    s3MockContainer.start()
    eventually {
      s3MockContainer.isRunning shouldBe true
    }
    s3Client = createS3Client(s3MockContainer.getHttpEndpoint)
  }

  override def afterEach(): Unit = {
    Try { s3Client.shutdown() }
    Try { s3MockContainer.stop() }
  }

  "AWS integration test" should {
    /* "list objects" in {
      val s3Client = createS3Client(s3MockContainer.getHttpEndpoint)
      val objectListing = s3Client.listObjects(bucketName)
      objectListing.getObjectSummaries.size shouldBe 99
    } */

    "list buckets" in {
      val buckets = s3Client.listBuckets.asScala
      buckets.map(_.getName) shouldBe Seq(bucketName)
    }
  }

  protected def createS3Client(endpoint: String): AmazonS3 = {
    AmazonS3Client.builder()
      .withCredentials(new AWSStaticCredentialsProvider(awsCredentials))
      .withEndpointConfiguration(new EndpointConfiguration(endpoint, region))
      .build()
  }
}
