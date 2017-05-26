# gfc-aws-s3

[![Build Status](https://travis-ci.com/gilt/gfc-aws-s3.svg?token=GMHJnzRkMmqWsbzuEWgW&branch=master)](https://travis-ci.com/gilt/gfc-aws-s3)

Tools for streaming data to and from S3

## Akka

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

## S3-http

This is a tiny service to expose s3 buckets from the account as HTTP resources,
the url format is:

```
/<bucketname>/<filePrefix>
```
    
The buckets exposed are specified in the configuration file, so you could specify
the prefix to use in the url, and the corresponding S3 bucket. On receiving GET 
request on this URL the service will look in the corresponding bucket for a file 
with the given prefix. If there is more then one file with the given prefix it 
will return the most recently updated one. The service populates the following 
headers: 
    
- Last-Modified
- Content-Disposition (sets "inline", with "filename" attribute equals to the full filename of the returned file)
- Content-Type

If the client tries to request a file from the bucket not listed in the configuration, 
the service will respond with 404 (Not found) error

The files are streamed from the S3 allowing service to expose big files without 
any significant memory requirements.

The service is intended to be a read-only exposure. PUT, POST, DELETE operations 
are not supported at the moment.

### Configuration

The general configurations parameters:

- port - which TCP port to listen to
- host - which TCP host/IP address to bind to
- chunk_size - size in kBytes of the block on downstreaming

Example:

```hocon
port = 8080
hostA = "localhost"
chunk_size = 1024 #kBytes
```

To expose files in the bucket you need to specify exposed_buckets configuration like this:

```hocon
exposed_buckets = [
  {
    uri = "test1",
    bucket = "test-bucket1"
  }, {
    uri = "test2",
    bucket = "test-bucket2"
  }
]
```
Every item in that collection should define 2 properties:

- uri defines the first part of the request uri that will be used to access this bucket
- bucket defines the S3 bucket to access on receiving the request with the specified uri

### Docker image

To build a local docker image just issue

    sbt s3http/docker:publishLocal

This project uses sbt-native-packager plugin which allows building images easily

### Testing

Testing requires access to S3. Use .aws/credentials or supply `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY`
when running `sbt test`

## Copyright and license

Copyright 2017 Gilt Groupe, 
Hudson Bay Company

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
