# syslogStreaming
基于Spark的实时日志分析及异常检测系统 Flume + Kafka + Hbase + Spark-Streaming + Scala
## 1，Flume
#### flume 配置文件
		// 为该Flume Agent的source、channel以及sink命名
		agent_log.sources = syslog_source1 
		agent_log.sinks = kafka_sink
		agent_log.channels = memory_channel

		// 配置Syslog源
		agent_log.sources.syslog_source1.type = exec
		agent_log.sources.syslog_source1.command = tail -F /home/hadoop/syslogStreaming/data/switchlog1.log

		// Kafka Sink的相关配置
		agent_log.sinks.kafka_sink.type = org.apache.flume.sink.kafka.KafkaSink
		agent_log.sinks.kafka_sink.channel = memory_channel
		agent_log.sinks.kafka_sink.kafka.topic = syslog_kafka_topic
		agent_log.sinks.kafka_sink.kafka.bootstrap.servers = spark0:9092,spark1:9092,spark2:9092
		agent_log.sinks.kafka_sink.kafka.flumeBatchSize = 20
		agent_log.sinks.kafka_sink.kafka.producer.acks = 1
		#agent_log.sinks.kafka_sink.kafka.producer.linger.ms = 1
		#agent_log.sinks.kafka_sink.kafka.producer.compression.type = snappy
		
		// Channel基于内存作为缓存
		agent_log.channels.memory_channel.type = memory
		agent_log.channels.memory_channel.capacity = 1000
		agent_log.channels.memory_channel.transactionCapacity = 1000

		// 将Source以及Sink绑定至Channel
		agent_log.sources.syslog_source1.channels = memory_channel
		agent_log.sinks.kafka_sink.channel = memory_channel
#### 启动flume连接到kafka
    // flume-ng agent --conf "配置文件文件目录" --conf-file "配置文件" --name "配置文件里agent的名字"
		flume-ng agent -n agent_log -c $FLUME_HOME/conf/ -f /home/hadoop/syslogStreaming/syslog_kafka.conf -Dflume.root.logger=INFO,console
## 2，kafka
#### 配置文件
    修改server.properties文件
	  kafka安装配置参考：https://www.cnblogs.com/zhaojiankai/p/7181910.html?utm_source=itdadao&utm_medium=referral
#### 启动kafka broker
    bin/kafka-server-start.sh -daemon config/server.properties &
#### 创建topic
    bin/kafka-topics.sh --create --zookeeper spark0:2181 --replication-factor 3 --partitions 1 --topic syslog_kafka_topic
#### 创建后，查看topic情况
	  bin/kafka-topics.sh --list --zookeeper spark0:2181
	  bin/kafka-topics.sh --describe --zookeeper spark0:2181 --topic syslog_kafka_topic
#### 启动kafka消费者接受flume数据
	  bin/kafka-console-consumer.sh --bootstrap-server 192.168.1.52:9092 --topic syslog_kafka_topic --from-beginning
## 3, kafka -----> spark streaming
#### maven相关依赖porm文件
#### 定义好kafka参数
    val kafkaParams = Map[String, Object](
      ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG -> brokers,
      ConsumerConfig.GROUP_ID_CONFIG -> groupId,
      ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG -> classOf[StringDeserializer],
      ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG -> classOf[StringDeserializer]
    )
#### 订阅相应的topic：
    val messages = KafkaUtils.createDirectStream[String, String](
      ssc,
      LocationStrategies.PreferConsistent,
      ConsumerStrategies.Subscribe[String, String](topicsSet, kafkaParams)
    )
## 4, spark streaming ------> HBase
#### 引入Hbase相关依赖
#### 将数据存储为HBase对应的格式
    // 随机产生某个uuid为行键 (示例）
    val put = new Put(Bytes.toBytes(UUID.randomUUID().toString))
    // 将列簇，列明，列值添加进对应结构
    put.addColumn(Bytes.toBytes("column_family"), Bytes.toBytes("column_name"), Bytes.toBytes("column_value"))
#### 插入HBase
    // 表名
    val tablename = "table_name"
    // 创建初始配置
    val hbaseconf = HBaseConfiguration.create()
    // 创建链接
    val conn = ConnectionFactory.createConnection(hbaseconf)
    // 指定表
    val table: HTable = new HTable(hbaseconf, Bytes.toBytes(tablename))
    // 提交事务，插入数据
    table.put(put)
    
