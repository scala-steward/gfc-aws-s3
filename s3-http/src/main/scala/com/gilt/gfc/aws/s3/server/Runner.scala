package com.gilt.gfc.aws.s3.server

import com.amazonaws.ClientConfiguration
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

object Runner {
  def main(args: Array[String]) {

    val logger = LoggerFactory.getLogger(this.getClass)
    val config = ConfigFactory.load()
    val host = config.getString("host")
    val port = config.getInt("port")
    val s3Region = config.getString("region")
    val exposedBuckets = config.getConfigList("exposed_buckets").asScala.map { item =>
      RouteConfigElement(
        item.getString("uri"),
        item.getString("bucket")
      )
    }
    val chunkSize = config.getInt("chunk_size") * 1024
    val retriesOnDownload = config.getInt("retries_on_download")
    val globalTimeout = config.getInt("global_timeout")

    val s3Client = AmazonS3ClientBuilder.standard()
      .withRegion(s3Region)
      .withClientConfiguration(
        new ClientConfiguration()
          .withConnectionTimeout(globalTimeout)
          .withMaxErrorRetry(retriesOnDownload)
      )
      .build

    val server = new S3Server(
      host,
      port,
      s3Client,
      exposedBuckets,
      chunkSize
    )

    server.start()

    logger.info(s"Server online at http://$host:$port/")
    exposedBuckets.foreach { item => logger.info(s"Exposing bucket ${item.bucketName} on /${item.uriPrefix}/") }
  }
}
