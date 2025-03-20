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

#transform {
#  use "spEnrichedFilter" {
#    atomic_field = "event_name"
#    regex = "^page_view$"
#    filter_action = "keep"
#  }
#}

#transform {
#  use "js" {
#    script_path   = env.JS_SCRIPT_PATH
#    snowplow_mode = true
#  }
#}

target {
#  use "kafka" {
#    brokers    = "redpanda:9092"
#    topic_name = "snowplow-enriched-good"
#  }
  use "file" {
    path        = "/tmp/snowbridge.txt"
    append      = true
    permissions = 0644
  }
}

failure_target {
  use "stdout" {}
}

log_level = "debug"

disable_telemetry = true
