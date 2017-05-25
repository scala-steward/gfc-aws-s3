package com.gilt.gfc.aws.s3.akka

import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.util.ByteString
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model._
import org.slf4j.LoggerFactory

import scala.collection.JavaConversions._
import scala.concurrent.Future

case class S3MultipartUploaderState(
  uploadId: String,
  etags: List[PartETag],
  totalLength: Long
) {
  def this(uploadId: String) = this(uploadId, List(), 0)
}

class S3MultipartUploaderSinkProtocol(
  s3Client: AmazonS3,
  bucketName: String,
  key: String
) {

  import FoldResourceSink._

  protected val logger = LoggerFactory.getLogger(this.getClass)

  def initUpload(): S3MultipartUploaderState = {
    logger.info(s"Initializing uploader for $bucketName :: $key")

    val initRequest = new InitiateMultipartUploadRequest(bucketName, key)
    val initResponse = s3Client.initiateMultipartUpload(initRequest)
    val uploadId = initResponse.getUploadId
    logger.info(s"UploadId is $uploadId")
    new S3MultipartUploaderState(uploadId)
  }

  def uploadChunk(state: S3MultipartUploaderState, chunk: ByteString, chunkNumber: Int): S3MultipartUploaderState = {

    logger.info(s"Uploading part number $chunkNumber to ${state.uploadId} partsize: ${chunk.length}")
    val partUploadRequest = new UploadPartRequest()
      .withBucketName(bucketName)
      .withKey(key)
      .withUploadId(state.uploadId)
      .withPartNumber(chunkNumber)
      .withInputStream(chunk.iterator.asInputStream)
      .withPartSize(chunk.length)
    val partUploadResult = s3Client.uploadPart(partUploadRequest)
    state.copy(
      etags = partUploadResult.getPartETag :: state.etags,
      totalLength = state.totalLength + chunk.length
    )
  }

  def completeUpload(state: S3MultipartUploaderState): Long = {
    logger.info(s"Completing upload ${state.uploadId}")
    val completeRequest = new CompleteMultipartUploadRequest(bucketName, key, state.uploadId, state.etags)
    s3Client.completeMultipartUpload(completeRequest)
    logger.info(s"Upload completed for ${state.uploadId}")
    state.totalLength
  }

  def mkSink(chunkSize: Int): Sink[Byte, Future[Long]] = Flow[Byte]
    .grouped(chunkSize)
    .map(ByteString(_ : _*))
    .zip(
      Source.fromIterator(() => Iterator.from(1))
    ).toMat(
      Sink.foldResource[S3MultipartUploaderState, (ByteString, Int), Long](
        () => initUpload(),
        { case (state, (chunk, chunkNumber)) => uploadChunk(state, chunk, chunkNumber) },
        completeUpload
      )
    )(Keep.right)
}

object S3MultipartUploaderSink {

  def apply(s3Client: AmazonS3,
    bucketName: String,
    key: String,
    chunkSize: Int
  ): Sink[Byte, Future[Long]] = new S3MultipartUploaderSinkProtocol(s3Client, bucketName, key).mkSink(chunkSize)

  implicit class SinkExtension(val sink: Sink.type) extends AnyVal {
    def s3MultipartUpload(
      s3Client: AmazonS3,
      bucketName: String,
      key: String,
      chunkSize: Int
    ): Sink[Byte, Future[Long]] = apply(s3Client, bucketName, key, chunkSize)
  }
}

