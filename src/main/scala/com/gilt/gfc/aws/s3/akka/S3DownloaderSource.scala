package com.gilt.gfc.aws.s3.akka

import akka.NotUsed
import akka.stream._
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.StreamConverters.fromInputStream
import akka.util.ByteString
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.{GetObjectMetadataRequest, GetObjectRequest}
import org.slf4j.LoggerFactory

import scala.concurrent.Future

class S3DowloaderSourceProtocol(
  s3Client: AmazonS3,
  bucketName: String,
  key: String
) {

  private val firstPartIndex = 1 //Numbered from 1

  protected val logger = LoggerFactory.getLogger(this.getClass)

  private def getPartCount(s3Client: AmazonS3, bucketName: String, key: String): Int =
  {
    /**
      * Quote from the AWS Java SDK docs:
      * To find the part count of an object, set the partNumber to 1 in GetObjectRequest. If the object has more than 1 part then part count will be returned, otherwise null is returned.
      *
      * @see http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/s3/model/ObjectMetadata.html#getPartCount--
      */
    val metadataRequest = new GetObjectMetadataRequest(bucketName, key).withPartNumber(1)
    val metaData = s3Client.getObjectMetadata(metadataRequest)
    Option(metaData.getPartCount).fold(1)(_.toInt)
  }

  private def getObjectContentLength(): Long = {
    val metadataRequest = new GetObjectMetadataRequest(bucketName, key)
    val metaData = s3Client.getObjectMetadata(metadataRequest)
    metaData.getContentLength
  }

  private def getChunkBoundaries(chunkIndex: Long, chunkSize: Long, totalFileLength: Long): (Long, Long) = {
    val firstByteOfChunk = chunkIndex * chunkSize
    val lastByteOfChunk = math.min(firstByteOfChunk + chunkSize, totalFileLength) - 1
    (firstByteOfChunk, lastByteOfChunk)
  }

  protected def s3SinglePartSource(
    partNumber: Int,
    readMemoryBufferSize: Int
  ): Source[ByteString, Future[IOResult]] = {

    val objectRequest = new GetObjectRequest(bucketName, key).withPartNumber(partNumber)

    fromInputStream(
      s3Client.getObject(objectRequest).getObjectContent,
      readMemoryBufferSize
    )
  }

  def s3MultipartDownloadSource(
    readMemoryBufferSize: Int
  ): Source[ByteString, NotUsed] = {

    Source.single(Unit)
      .map(_ => getPartCount(s3Client, bucketName, key))
      .flatMapConcat { partCount =>
        logger.info(s"Downloading file $key from bucket $bucketName; number of parts: $partCount")
        Source(Range(firstPartIndex, partCount + firstPartIndex))
      }.flatMapConcat { partNumber =>
      logger.debug(s"Downloading file $key from bucket $bucketName; downloading part $partNumber")
      s3SinglePartSource(partNumber, readMemoryBufferSize)
    }
  }

  protected def s3SingleChunkSource(
    firstByte: Long,
    lastByte: Long,
    readMemoryBufferSize: Int
  ): Source[ByteString, Future[IOResult]] = {

    val objectRequest = new GetObjectRequest(bucketName, key).withRange(firstByte, lastByte)
    fromInputStream(
      s3Client.getObject(objectRequest).getObjectContent,
      readMemoryBufferSize
    )

  }

  def s3ChunkedDownloadSource(
    s3DownloadChunkSize: Int,
    readMempryBufferSize: Int
  ): Source[ByteString, NotUsed] = {

    Source.single(Unit)
      .map { _ => getObjectContentLength() }
      .flatMapConcat { fileLength =>
        logger.info(s"Downloading file $key from bucket $bucketName; file length: $fileLength")
        //Range is inclusive, counting from 0 (see http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/s3/model/GetObjectRequest.html#withRange-long-long-)
        Source.unfold(0L) { chunkNumber =>
          val (firstByteOfChunk, lastByteOfChunk) = getChunkBoundaries(chunkNumber, s3DownloadChunkSize, fileLength)
          if (firstByteOfChunk >= fileLength)
            None
          else
            Some(chunkNumber + 1, (firstByteOfChunk, lastByteOfChunk))
        }
      }.flatMapConcat { case (firstByte, lastByte) =>
      s3SingleChunkSource(firstByte, lastByte, readMempryBufferSize)
    }
  }

}

object S3DownloaderSource {

  def multipart(
    s3Client: AmazonS3,
    bucketName: String,
    key: String,
    readMemoryBufferSize: Int
  ) = new S3DowloaderSourceProtocol(s3Client, bucketName, key).s3MultipartDownloadSource(readMemoryBufferSize)

  def chunked(
    s3Client: AmazonS3,
    bucketName: String,
    key: String,
    chunkSize: Int,
    readMemoryBufferSize: Int
  ) = new S3DowloaderSourceProtocol(s3Client, bucketName, key).s3ChunkedDownloadSource(chunkSize, readMemoryBufferSize)

  implicit class SourceExtender(val source: Source.type) extends AnyVal {

    def s3MultipartDownload(
      s3Client: AmazonS3,
      bucketName: String,
      key: String,
      readMemoryBufferSize: Int
    ) = multipart(s3Client, bucketName, key, readMemoryBufferSize)

    def s3ChunkedDownload(
      s3Client: AmazonS3,
      bucketName: String,
      key: String,
      chunkSize: Int,
      readMemoryBufferSize: Int
    ) = chunked(s3Client, bucketName, key, chunkSize, readMemoryBufferSize)

  }
}
