transform {

  use "spEnrichedFilterUnstructEvent" {
    unstruct_event_name = "snowplow_ecommerce_action"
    custom_field_path = "type"
    regex         = "^product-view$"
    filter_action = "keep"
  }

}

transform {
  use "js" {
    script_path = "/tmp/script.js"
    snowplow_mode = true
  }
}
