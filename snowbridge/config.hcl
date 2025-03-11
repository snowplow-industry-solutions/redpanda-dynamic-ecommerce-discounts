license {
  accept = env("ACCEPT_LICENSE")
}

source {
  use "kinesis" {
    stream_name         = "enriched-good"
    region              = env("AWS_REGION")
    app_name            = "snowbridge"
    custom_aws_endpoint = "http://localhost.localstack.cloud:4566"
  }
}

target {
  use "kafka" {
    brokers    = "redpanda:9092"
    topic_name = "snowplow-enriched-good"
  }
}

failure_target {
  use "stdout" {}
}

log_level = "debug"

disable_telemetry = true
