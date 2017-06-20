# gfc-aws-s3

[![Build Status](https://travis-ci.com/gilt/gfc-aws-s3.svg?token=GMHJnzRkMmqWsbzuEWgW&branch=master)](https://travis-ci.com/gilt/gfc-aws-s3)

Tools for streaming data to and from S3. Part of the [Gilt Foundation Classes](https://github.com/gilt?q=gfc).

## Usage

The library provides tools to integrate akka-streams with Amazon S3 storage service. To use it add to your dependencies:

```sbt
"com.gilt" %% "gfc-aws-s3-akka" % "0.1.0"
```

The library contains akka-stream Sources and Sinks to Stream data from and to S3.

### Sinks

Allows uploading data to S3 in a streaming manner. The underlying implementation uses [S3 Multipart upload API](http://docs.aws.amazon.com/AmazonS3/latest/dev/llJavaUploadFile.html). Due to the API requirements the size of the part could not be less than 5Mb. You are to provide the size of the chunk on Source creation, the internals will automatically slice the incoming data into the chunks of the given size and upload those chunks to S3.

To create the source:

```scala
import com.gilt.gfc.s3.akka.S3MultipartUploaderSink._

val bucketName = "test-bucket"
val fileKey = "test-file"
val s3Client = AmazonS3ClientBuilder.standard()
  .withRegion("us-east-1")
  .build
val chunkSize = 6 * 1024 * 1024 // 6 Megabytes

val sink = Sink.s3MultipartUpload(s3Client, bucketName, fileKey, chunkSize)
```

The sink could also be created in different style manner:

```scala
import com.gilt.gfc.s3.akka.S3MultipartUploaderSink

val sink = S3MultipartUploaderSink(s3Client, bucketName, fileKey, chunkSize)
```

The materialized value of the sink is the total length of the uploaded file in case of successful uploads.

Please, bear in mind, that incomplete uploads eat S3 space (meaning cost you some money) but are not shown in AWS S3 UI. Probably the best idea is to configure S3 so that it will delete parts of the incomplete uploads automatically after given amount of time ([docs](http://docs.aws.amazon.com/AmazonS3/latest/dev/object-lifecycle-mgmt.html))

### Sources

Allows accessing S3 objects as a stream source in two different manners - by parts and by chunks. The difference is subtle but important:

1. accessing by parts means that you know or assume that the file was uploaded using S3 multipart API. If the was not uploaded using multipart API it would be downloaded in a single chunk. This will not eat memory, as the source does real streaming, and allows to control the buffer size for download, but could lead to some problems with very large files, as S3 tends to drop long-lasting connections sometimes.

To do that, use:

```scala
import com.gilt.gfc.s3.akka.S3DownloaderSource._

val bucketName = "test-bucket"
val fileKey = "test-file"
val s3Client = AmazonS3ClientBuilder.standard()
  .withRegion("us-east-1")
  .build
val memoryBufferSize = 128 * 1024 // 128 Kb buffer

val source = Source.s3MultipartDownload(s3Client, bucketName, fileKey, memoryBufferSize)
```

2. accessing by chunks means that you provide a size of the part to download, and the source will ultimately use `Range` header to access file in "seek-and-read" manner. This approach could be used with any S3 object, regardless of whether it was uploaded using multipart API or not. The size of the chunk will affect the number of the requests sent to S3.

To do that use:

```scala
import com.gilt.gfc.s3.akka.S3DownloaderSource._

val bucketName = "test-bucket"
val fileKey = "test-file"
val s3Client = AmazonS3ClientBuilder.standard()
  .withRegion("us-east-1")
  .build
val chunkSize = 1024 * 1024       // 1 Mb chunks to request from S3
val memoryBufferSize = 128 * 1024 // 128 Kb buffer

val source = Source.s3ChunkedDownload(s3Client, bucketName, fileKey, chunkSize, memoryBufferSize)
```

The pieces of code above will crease a Source[ByteString], where each ByteString represents a part of the file.

## Copyright and license

Copyright 2017 Gilt Groupe, Hudson's Bay Company

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
