package com.seven.spark

import org.apache.spark.SparkConf
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.streaming.kafka010.{ConsumerStrategies, KafkaUtils, LocationStrategies}
//import org.apache.spark.streaming.kafka.KafkaUtils

object syslogStreaming {
  def main(args:Array[String]): Unit = {
    if (args.length < 3) {
      System.err.println(
        s"""
           |Usage:syslogStreaming<brokers> <topics> <groupid>
           |<brokers> is a list of one or more Kafka brokers
           |<topics> is a list of one or more kafka topics to consume from
           |<groupid> is a consume group
         """.stripMargin
      )
      System.exit(1)
    }
    val Array(brokers, topics, groupId) = args
    // Create context with 2 second batch interval
    val sparkConf = new SparkConf().setAppName("syslogStreaming").setMaster("local[3]")
    val ssc = new StreamingContext(sparkConf, Seconds(5))
    val topicsSet = topics.split(",").toSet

    val kafkaParams = Map[String, Object](
      ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG -> brokers,
      ConsumerConfig.GROUP_ID_CONFIG -> groupId,
      ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG -> classOf[StringDeserializer],
      ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG -> classOf[StringDeserializer]
    )
    //val messages = KafkaUtils.createDirectStream(
      //ssc, kafkaParams, topicsSet)
    val messages = KafkaUtils.createDirectStream[String, String](
      ssc,
      LocationStrategies.PreferConsistent,
      ConsumerStrategies.Subscribe[String, String](topicsSet, kafkaParams)
    )
    val value = messages.map(x => x.value())

    value.print()

    ssc.start()
    ssc.awaitTermination()
    ssc.stop()
  }

}
