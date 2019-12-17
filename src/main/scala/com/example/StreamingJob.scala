package com.example

import java.util.{Properties, Base64}
import java.nio.charset.StandardCharsets

import scopt.OptionParser

import com.github.plokhotnyuk.jsoniter_scala.macros._
import com.github.plokhotnyuk.jsoniter_scala.core._

import org.apache.flink.util.Collector
import org.apache.flink.configuration.Configuration
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.streaming.api.functions.timestamps.AscendingTimestampExtractor
import org.apache.flink.streaming.api.scala.function.WindowFunction
import org.apache.flink.streaming.connectors.kinesis.FlinkKinesisConsumer
import org.apache.flink.streaming.connectors.kinesis.config.{AWSConfigConstants, ConsumerConfigConstants}
import org.apache.flink.kinesis.shaded.com.amazonaws.regions.{Region, RegionUtils}

case class SplitRecord(
  recordId: String,
  order: Short,
  count: Short,
  record: String,
  timestamp: Long
)

object StreamingJob {
  case class Params(
    kinesisRegion: Option[Region] = None,
    kinesisAwsAccessKeyId: Option[String] = None,
    kinesisAwsSecretAccessKey: Option[String] = None,
    kinesisEndpoint: Option[String] = None,
    kinesisStreamName: String = ""
  )

  val parser = new OptionParser[Params]("GameOptimizingServiceAnalytics") {
    head("flink-aggregate-splited-record-example")
    opt[String]("endpoint")
      .optional()
      .text("endpoint is string property")
      .action((x, p) => p.copy(kinesisEndpoint = Some(x)))
    opt[String]("region")
      .optional()
      .text("region is string property")
      .action((x, p) => p.copy(kinesisRegion = Some(RegionUtils.getRegion(x))))
    opt[String]("aws_access_key_id")
      .optional()
      .text("aws_access_key_id is string property")
      .action((x, p) => p.copy(kinesisAwsAccessKeyId = Some(x)))
    opt[String]("aws_secret_access_key")
      .optional()
      .text("aws_secret_access_key is string property")
      .action((x, p) => p.copy(kinesisAwsSecretAccessKey = Some(x)))
    opt[String]("stream")
      .text("stream is string property")
      .action((x, p) => p.copy(kinesisStreamName = x))
  }

  def main(args: Array[String]) {
    //parser.parse(args, Params()) match {
    parser.parse(args, Params()) match {
      case Some(params: Params) =>
        run(params)
      case _ =>
        System.exit(1)
    }
  }

  def run(params: Params): Unit = {
    println(s"params: $params")

    // set up the streaming execution environment
    val env = StreamExecutionEnvironment.getExecutionEnvironment

    val kinesisConsumerConfig = new Properties();
    params.kinesisRegion match {
      case Some(region) => kinesisConsumerConfig.setProperty(AWSConfigConstants.AWS_REGION, region.getName())
      case _ => 
    }
    kinesisConsumerConfig.put(AWSConfigConstants.AWS_ACCESS_KEY_ID, params.kinesisAwsAccessKeyId.getOrElse("aws_access_key_id"))
    kinesisConsumerConfig.put(AWSConfigConstants.AWS_SECRET_ACCESS_KEY, params.kinesisAwsSecretAccessKey.getOrElse("aws_secret_access_key"))
    params.kinesisEndpoint match {
      case Some(endpoint) => kinesisConsumerConfig.put(AWSConfigConstants.AWS_ENDPOINT, endpoint)
      case _ => 
    }
    kinesisConsumerConfig.put(ConsumerConfigConstants.SHARD_GETRECORDS_INTERVAL_MILLIS, "1000");
    kinesisConsumerConfig.put(ConsumerConfigConstants.STREAM_INITIAL_POSITION, "LATEST")

    //create Kinesis source
    val kinesisStream: DataStream[String] = env.addSource(new FlinkKinesisConsumer[String](
        //read events from the Kinesis stream passed in as a parameter
        params.kinesisStreamName,
        //deserialize events with EventSchema
        new SimpleStringSchema,
        //using the previously defined properties
        kinesisConsumerConfig
    ))

    val decodedRecordStream = kinesisStream.map(record => Base64.getDecoder.decode(record.getBytes(StandardCharsets.UTF_8)))

    val splitRecordStream: DataStream[SplitRecord] = decodedRecordStream.map(obj => {
      implicit val codec: JsonValueCodec[SplitRecord] = JsonCodecMaker.make(
        CodecMakerConfig.withFieldNameMapper(JsonCodecMaker.enforce_snake_case)
      )
      readFromArray[SplitRecord](obj)
    })
    .assignTimestampsAndWatermarks(new AscendingTimestampExtractor[SplitRecord] {
      def extractAscendingTimestamp(splitRecord: SplitRecord): Long = splitRecord.timestamp
    })
    splitRecordStream.print()

    val groupingByRecordIdStream : WindowedStream[SplitRecord, (String, Short), TimeWindow] = splitRecordStream
      .keyBy(r => (r.recordId, r.count))
      .timeWindow(Time.seconds(5))

    val reducedSplitRecordStream: DataStream[String] = groupingByRecordIdStream.apply[String](new ReduceSplitRecordWindowFunction())
    reducedSplitRecordStream.print()

    // execute program
    env.execute("Flink Streaming Scala API Skeleton")
  }

  class ReduceSplitRecordWindowFunction extends WindowFunction[SplitRecord, String, (String, Short), TimeWindow] {
    /** State to keep track of our current count */
		override def apply(key: (String, Short), window: TimeWindow, input: Iterable[SplitRecord], out: Collector[String]): Unit = {
      println(input, key._2)
      if (input.size == key._2) {
        // regular window function
        val reducedRecord = input.toList
          .sortBy(_.order)
          .map(_.record)
          .reduce(_ + _)

        out.collect(reducedRecord);
      }
    }
  }
}